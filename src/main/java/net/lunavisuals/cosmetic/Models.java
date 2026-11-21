/*
 * This file is part of Luna Visuals
 *
 * Copyright (c) 2026 Luna
 *
 * Luna Visuals is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Luna Visuals is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Luna Visuals. If not, see <https://www.gnu.org/licenses/>.
 */
package net.lunavisuals.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.lunavisuals.core.Category;
import net.lunavisuals.core.Module;
import net.lunavisuals.core.Modules;
import net.lunavisuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The «Модели» wardrobe: Figura-style avatars bundled with Luna.
 *
 * <p>The original mod ships these as a split zip of avatar folders. We keep the same layout:
 * every folder with an {@code avatar.json} becomes one entry, its model is the largest
 * {@code .bbmodel} inside it, and the preview is {@code avatar.png} (or the biggest embedded
 * texture when the bundled preview is the generic placeholder). Stage 1 only plays the model and
 * the animations baked into the {@code .bbmodel}; Lua scripts stay for stage 2.
 */
public final class Models extends Module {
    private static final Path ROOT =
            FabricLoader.getInstance().getGameDir().resolve("LunaVisuals").resolve("models");
    private static final String BUNDLE = "/assets/lunavisuals/models/models.zip";
    private static final String BUNDLE_VERSION = "lunavisuals-models-v1";
    private static final String GENERIC_PREVIEW_SHA256 =
            "22c2e8c2358322d34941d1c6df6f6155c423ac3cb5183535a2daef5197674557";
    private static final int FULL_BRIGHT = 15728880;
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Preview>> PREVIEWS = new ConcurrentHashMap<>();

    public final Setting.Mode design;
    private final List<Entry> items;

    public Models() {
        super(
                "models",
                "Модели",
                "Полноценные модели персонажа: Figura-аватары из каталога Luna со скинами, частями тела и анимациями из .bbmodel.",
                Category.COSMETICS,
                false);
        extractBundle();
        this.items = scan();
        List<String> names = new ArrayList<>();
        for (Entry entry : this.items) {
            names.add(entry.name());
        }
        if (names.isEmpty()) {
            names.add("—");
        }
        this.design = this.add(new Setting.Mode("design", "Модель", names.get(0), names.toArray(String[]::new)));
    }

    public List<Entry> items() {
        return this.items;
    }

    public Entry current() {
        for (Entry entry : this.items) {
            if (this.design.is(entry.name())) {
                return entry;
            }
        }
        return this.items.isEmpty() ? null : this.items.get(0);
    }

    /** True when the vanilla player model has to make room for the selected avatar. */
    public boolean replaces(EntityRenderState state) {
        if (!this.enabled() || !(state instanceof AvatarRenderState a)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || a.id != mc.player.getId() || a.isInvisible || a.isSpectator) {
            return false;
        }
        Entry entry = this.current();
        ModelGeo geo = ModelGeo.get(entry);
        return geo != null && geo.quadCount() > 0;
    }

    public static void afterBody(
            EntityModel<?> model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        if (!(model instanceof PlayerModel) || !(state instanceof AvatarRenderState a)) {
            return;
        }

        Models module = Modules.MODELS;
        if (module == null || !module.enabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || a.id != mc.player.getId() || a.isInvisible || a.isSpectator) {
            return;
        }

        Entry entry = module.current();
        ModelGeo geo = ModelGeo.get(entry);
        if (geo == null || geo.quadCount() == 0) {
            return;
        }

        Entity entity = mc.player;
        long millis = System.currentTimeMillis();
        double seconds = (double) (millis % 3600600L) / 1000.0;
        boolean moving =
                a.walkAnimationSpeed > 0.03F || entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
        float walk = moving ? Mth.clamp(a.walkAnimationSpeed * 4.0F, 0.35F, 1.0F) : 0.0F;
        boolean water = a.isVisuallySwimming || a.isInWater;
        Drive drive = new Drive(
                seconds,
                a.walkAnimationPos,
                walk,
                a.isFallFlying ? 1.0F : 0.0F,
                a.isCrouching ? 1.0F : 0.0F,
                water ? 1.0F : 0.0F,
                entity.isSprinting(),
                millis);

        geo.render(pose, collector, a.lightCoords, drive);
    }

    public Identifier preview(Entry entry) {
        Preview preview = previewOf(entry);
        return preview == null ? null : preview.id();
    }

    public Preview previewOf(Entry entry) {
        if (entry == null) {
            return null;
        }
        return PREVIEWS.computeIfAbsent(entry.id(), key -> Optional.ofNullable(makePreview(entry)))
                .orElse(null);
    }

    private static Preview makePreview(Entry entry) {
        try {
            byte[] png = null;
            Path avatar = entry.directory().resolve("avatar.png");
            if (Files.isRegularFile(avatar)) {
                byte[] candidate = Files.readAllBytes(avatar);
                if (!GENERIC_PREVIEW_SHA256.equalsIgnoreCase(sha256(candidate))) {
                    png = candidate;
                }
            }
            if (png == null) {
                ModelGeo geo = ModelGeo.get(entry);
                if (geo != null) {
                    png = geo.previewTexture();
                }
            }
            if (png == null || png.length == 0) {
                return null;
            }

            NativeImage source = NativeImage.read(new ByteArrayInputStream(png));
            NativeImage square;
            try {
                square = cropAndPad(source);
            } finally {
                source.close();
            }
            if (square == null) {
                return null;
            }

            String key = sha256(png);
            Identifier id = Identifier.fromNamespaceAndPath("lunavisuals", "models/preview_" + key);
            int size = square.getWidth();
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, new DynamicTexture(() -> "luna model preview", square));
            return new Preview(id, size);
        } catch (Exception e) {
            System.err.println("[LunaVisuals] model preview " + entry.name() + ": " + e);
            return null;
        }
    }

    private static NativeImage cropAndPad(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((source.getPixel(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            minX = 0;
            minY = 0;
            maxX = width - 1;
            maxY = height - 1;
        }

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        int size = Math.max(cropW, cropH);
        NativeImage out = new NativeImage(size, size, true);
        int ox = (size - cropW) / 2;
        int oy = (size - cropH) / 2;

        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                out.setPixel(ox + x, oy + y, source.getPixel(minX + x, minY + y));
            }
        }
        return out;
    }

    /** Registers an embedded/external PNG once and returns its texture id, or null on bad data. */
    static Identifier texture(byte[] png) {
        if (png == null || png.length == 0) {
            return null;
        }
        String key = sha256(png);
        Identifier cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = Identifier.fromNamespaceAndPath("lunavisuals", "models/tex_" + key);
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, new DynamicTexture(() -> "luna model texture", image));
            TEXTURES.put(key, id);
            return id;
        } catch (Exception e) {
            System.err.println("[LunaVisuals] model texture: " + e);
            return null;
        }
    }

    private static void extractBundle() {
        Path marker = ROOT.resolve(".models-version");
        try {
            if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(BUNDLE_VERSION)) {
                return;
            }
            Files.createDirectories(ROOT);
            InputStream raw = Models.class.getResourceAsStream(BUNDLE);
            if (raw == null) {
                System.err.println("[LunaVisuals] models bundle is missing");
                return;
            }
            try (InputStream in = raw;
                    ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    if (name.startsWith("__MACOSX/") || name.isBlank()) {
                        continue;
                    }
                    Path output = ROOT.resolve(name).normalize();
                    if (!output.startsWith(ROOT)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        Files.copy(zip, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.writeString(marker, BUNDLE_VERSION);
        } catch (Exception e) {
            System.err.println("[LunaVisuals] models extract: " + e);
        }
    }

    private static List<Entry> scan() {
        List<Entry> found = new ArrayList<>();
        try {
            Files.createDirectories(ROOT);
            try (Stream<Path> paths = Files.walk(ROOT, 6)) {
                paths.filter(path -> path.getFileName().toString().equalsIgnoreCase("avatar.json"))
                        .forEach(path -> {
                            Path directory = path.getParent();
                            if (directory == null || directory.equals(ROOT)) {
                                return;
                            }
                            Path relative = ROOT.relativize(directory);
                            String id = relative.toString().replace('\\', '/');
                            if (id.startsWith(".") || id.contains("/.")) {
                                return;
                            }
                            Path model = findModel(directory);
                            if (model == null) {
                                return;
                            }
                            found.add(new Entry(id, displayName(directory), directory, model));
                        });
            }
        } catch (Exception e) {
            System.err.println("[LunaVisuals] models scan: " + e);
        }

        found.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<Entry> unique = new ArrayList<>(found.size());
        for (Entry entry : found) {
            int count = seen.merge(entry.name(), 1, Integer::sum);
            String name = count == 1 ? entry.name() : entry.name() + " (" + count + ")";
            unique.add(new Entry(entry.id(), name, entry.directory(), entry.model()));
        }
        return List.copyOf(unique);
    }

    private static Path findModel(Path directory) {
        try (Stream<Path> paths = Files.walk(directory, 5)) {
            return paths.filter(path -> path.getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".bbmodel"))
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (Exception ignored) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String displayName(Path directory) {
        String name = directory.getFileName().toString().replace('_', ' ').trim();
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        int cut = name.indexOf(" - ");
        if (cut > 0) {
            name = name.substring(0, cut).trim();
        }
        if (name.toLowerCase(Locale.ROOT).startsWith("weapon-")) {
            name = name.substring(7).trim();
        }
        return name.isEmpty() ? "Модель" : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes)) + "_" + bytes.length;
        }
    }

    public record Entry(String id, String name, Path directory, Path model) {}

    /** A ready-to-draw preview: square texture id and its pixel size. */
    public record Preview(Identifier id, int size) {}

    public record Drive(
            double seconds,
            float walkPos,
            float walk,
            float air,
            float sneak,
            float water,
            boolean sprint,
            long millis) {}

    /** Decodes a Blockbench embedded texture source. */
    static byte[] decodeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            int comma = source.indexOf(',');
            if (source.startsWith("data:") && comma >= 0) {
                String meta = source.substring(0, comma);
                String payload = source.substring(comma + 1);
                return meta.contains("base64")
                        ? Base64.getDecoder().decode(payload)
                        : java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8)
                                .getBytes(StandardCharsets.ISO_8859_1);
            }
            return Base64.getDecoder().decode(source);
        } catch (Exception ignored) {
            return null;
        }
    }

    static int fullBright() {
        return FULL_BRIGHT;
    }
}
