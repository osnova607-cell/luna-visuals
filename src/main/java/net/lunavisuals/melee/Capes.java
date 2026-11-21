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
package net.lunavisuals.melee;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.lunavisuals.core.Category;
import net.lunavisuals.core.Module;
import net.lunavisuals.core.Modules;
import net.lunavisuals.core.Setting;
import net.lunavisuals.core.Theme;
import net.lunavisuals.render.TexWarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;

public final class Capes extends Module {
    private static final Map<String, int[]> TIMING = new LinkedHashMap<>();
    private static final Map<String, String> FILES = new LinkedHashMap<>();
    public final Setting.Mode design;
    private static final Map<String, Identifier[]> textures;

    private static void add(String name, String file, int frames, int ms) {
        FILES.put(name, file);
        TIMING.put(name, new int[] {frames, ms});
    }

    public static List<String> designs() {
        return List.copyOf(FILES.keySet());
    }

    public Capes() {
        super(
                "capes",
                "Плащи",
                "Живая ткань Luna и анимированные плащи Luna: фирменные и забавные.",
                Category.COSMETICS,
                false);
        this.design = this.add(new Setting.Mode(
                "design", "Рисунок", "Luna", (String[]) FILES.keySet().toArray((x$0) -> new String[x$0])));
    }

    public static Identifier resolve(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || player.isInvisible() || player != mc.player) {
            return null;
        }
        return Modules.CAPES.enabled() ? worldFrame((String) Modules.CAPES.design.get()) : null;
    }

    public static String id(String design) {
        return FILES.get(known(design));
    }

    private static String known(String design) {
        return FILES.containsKey(design) ? design : "Luna";
    }

    private static int frame(String design) {
        int[] timing = TIMING.get(design);
        return Theme.animations ? (int) (System.currentTimeMillis() / (long) timing[1] % (long) timing[0]) : 0;
    }

    public static Strip strip(String design) {
        design = known(design);
        Identifier id = Identifier.fromNamespaceAndPath("lunavisuals", "textures/cape/" + FILES.get(design) + ".png");
        return !TexWarm.ready(id) ? null : new Strip(id, frame(design), TIMING.get(design)[0]);
    }

    private static Identifier worldFrame(String design) {
        design = known(design);
        Identifier[] ids = (Identifier[]) textures.computeIfAbsent(design, Capes::requestWorld);
        int n = ids.length;
        int i = frame(design);

        for (int k = 0; k < n; k++) {
            Identifier id = ids[(i - k + n) % n];
            if (TexWarm.registered(id)) {
                return id;
            }
        }

        return null;
    }

    private static Identifier[] requestWorld(String design) {
        String file = FILES.get(design);
        int count = TIMING.get(design)[0];
        Identifier[] out = new Identifier[count];
        NativeImage[] strip = {null};
        int[] left = {count};

        for (int f = 0; f < count; f++) {
            out[f] = Identifier.fromNamespaceAndPath("lunavisuals", "cape/" + file + "_" + f);
            final int frame = f;
            TexWarm.request(out[f], () -> {
                if (strip[0] == null) {
                    try (InputStream in =
                            Capes.class.getResourceAsStream("/assets/lunavisuals/textures/cape/" + file + ".png")) {
                        strip[0] = NativeImage.read(in);
                    }
                }

                NativeImage image = layout(strip[0], frame);
                if (--left[0] == 0) {
                    strip[0].close();
                    strip[0] = null;
                }

                return image;
            });
        }

        return out;
    }

    private static NativeImage layout(NativeImage strip, int frame) {
        NativeImage image = new NativeImage(1024, 512, false);
        int oy = frame * 256;
        int edge = strip.getPixel(2, oy + 128) | 0xFF000000;
        image.fillRect(0, 0, 1024, 512, edge);

        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 160; x++) {
                int c = strip.getPixel(x, oy + y) | 0xFF000000;
                image.setPixel(16 + x, 16 + y, c);
                image.setPixel(192 + (159 - x), 16 + y, c);
            }
        }

        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixel(x, 16 + y, strip.getPixel(0, oy + y) | 0xFF000000);
                image.setPixel(176 + x, 16 + y, strip.getPixel(159, oy + y) | 0xFF000000);
            }
        }

        for (int x = 0; x < 160; x++) {
            for (int y = 0; y < 16; y++) {
                image.setPixel(16 + x, y, strip.getPixel(x, oy) | 0xFF000000);
                image.setPixel(176 + x, y, strip.getPixel(x, oy + 255) | 0xFF000000);
            }
        }

        return image;
    }

    static {
        add("Luna", "luna", 16, 140);
        add("Luna Neon", "neon", 12, 110);
        add("Leopard", "leopard", 1, 1000);
        add("Potion", "potion", 1, 1000);
        add("Diamond Sword", "diamond_sword", 11, 150);
        add("Glitch", "glitch", 14, 50);
        add("Sweatgod", "sweatgod", 5, 200);
        textures = new LinkedHashMap<>();
    }

    public static record Strip(Identifier id, int frame, int frames) {}
}
