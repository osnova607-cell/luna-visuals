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
package net.lunavisuals.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.lunavisuals.core.Category;
import net.lunavisuals.core.Module;
import net.lunavisuals.core.Modules;
import net.lunavisuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Small looks taken over from Pulse: hurt colour, hit bubbles, body glow, a halo, a hat, your own name, motes. */
public final class Extras {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("lunavisuals", "textures/effect/white.png");

    private Extras() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level.isClientSide() && target instanceof LivingEntity) Modules.HIT_BUBBLE.hit();
            return InteractionResult.PASS;
        });
    }

    /** Called from the world pass. */
    static void world(WorldRenderContext ctx, Vec3 cam, float pt) {
        Modules.HIT_BUBBLE.render(ctx, cam);
    }

    /** Called right after a living entity's body is drawn, with the model posed and the pose at its root. */
    public static void afterBody(
            EntityModel<?> model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c) {
        if (state.isInvisible) return;
        Entity e = Ducks.entityOf(state);
        if (Modules.BODY_GLOW.enabled() && e instanceof Player) Modules.BODY_GLOW.glow(model, state, pose, c);
        if (e != mc().player || !(model instanceof HumanoidModel<?> humanoid)) return;
        if (Modules.HALO.enabled()) Modules.HALO.draw(humanoid, pose, c);
        if (Modules.CHINA_HAT.enabled()) Modules.CHINA_HAT.draw(humanoid, pose, c);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float w, int c) {
        vc.addVertex(p, x, y, z)
                .setColor(c)
                .setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(p, 0.0F, -1.0F, 0.0F);
    }

    private static RenderType additive(Identifier tex) {
        RenderType t = Fx.glowWith(tex);
        return t != null ? t : RenderTypes.eyes(tex);
    }

    /** Moves the pose to the head: the model's own space there has y pointing down, the top of the head at -0.5. */
    private static void toHead(HumanoidModel<?> m, PoseStack pose) {
        m.root().translateAndRotate(pose);
        m.head.translateAndRotate(pose);
    }

    // ---------------------------------------------------------------- hit colour

    /** The flash of a hurt entity in your own colour: the whole body, or tinting the skin. */
    public static final class HitColor extends Module {
        private final Setting.Mode mode = this.add(new Setting.Mode("mode", "Режим", "Полностью", "Полностью", "Скин"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public HitColor() {
            super("hit_color", "Hit Color", "Цвет сущности, когда она получает урон", Category.COMBAT, false);
        }

        private boolean hurt(Object state) {
            return this.enabled() && state instanceof LivingEntityRenderState l && l.hasRedOverlay;
        }

        public RenderType type(Object state, RenderType type) {
            return this.hurt(state) && this.mode.is("Полностью") ? RenderTypes.entityTranslucent(WHITE) : type;
        }

        public int overlay(Object state, int overlay) {
            return this.hurt(state) ? OverlayTexture.NO_OVERLAY : overlay;
        }

        public int colour(Object state, int colour) {
            if (!this.hurt(state)) return colour;
            int c = this.colour.argb() | 0xFF000000;
            if (this.mode.is("Полностью")) return c;
            int r = (colour >> 16 & 255) * (c >> 16 & 255) / 255;
            int g = (colour >> 8 & 255) * (c >> 8 & 255) / 255;
            int b = (colour & 255) * (c & 255) / 255;
            return colour & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    // ---------------------------------------------------------------- hit bubbles

    /** A ring of light that opens where you strike and fades as it spins out. */
    public static final class HitBubble extends Module {
        private static final Identifier[] TEXTURES = {
            Identifier.fromNamespaceAndPath("lunavisuals", "textures/effect/bubble_1.png"),
            Identifier.fromNamespaceAndPath("lunavisuals", "textures/effect/bubble_2.png")
        };
        private final Setting.Num life = this.add(new Setting.Num("life", "Время жизни", 40.0, 10.0, 150.0, 5.0, " т"));
        private final Setting.Mode texture =
                this.add(new Setting.Mode("texture", "Текстура", "Пузырь 1", "Пузырь 1", "Пузырь 2"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));
        private final List<Bubble> bubbles = new ArrayList<>();

        private record Bubble(Vec3 at, float yaw, float pitch, long born) {}

        public HitBubble() {
            super("hit_bubble", "Hit Bubble", "Пузырь света там, куда ты ударил", Category.COMBAT, false);
        }

        void hit() {
            LocalPlayer me = mc().player;
            if (!this.enabled() || me == null || mc().level == null) return;
            float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 eye = me.getEyePosition(pt);
            Vec3 reach = eye.add(me.getViewVector(pt));
            BlockHitResult block =
                    mc().level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, me));
            Vec3 at = block.getType() != HitResult.Type.MISS ? block.getLocation() : reach;
            this.bubbles.add(new Bubble(at, -me.getViewYRot(pt), me.getViewXRot(pt), System.currentTimeMillis()));
        }

        void render(WorldRenderContext ctx, Vec3 cam) {
            if (this.bubbles.isEmpty()) return;
            long now = System.currentTimeMillis();
            long life = Math.round(this.life.get() * 50.0);
            this.bubbles.removeIf(b -> now - b.born() >= life);
            if (!this.enabled() || this.bubbles.isEmpty()) return;
            Identifier tex = TEXTURES[this.texture.is("Пузырь 2") ? 1 : 0];
            VertexConsumer vc = ctx.consumers().getBuffer(additive(tex));
            int c = this.colour.argb() | 0xFF000000;
            PoseStack ps = ctx.matrices();
            for (Bubble b : this.bubbles) {
                long age = now - b.born();
                float grow = age / 1500.0F;
                float fade = (1.0F - grow) * 0.8F;
                if (fade <= 0.0F) continue;
                float half = grow;
                ps.pushPose();
                ps.translate(b.at().x - cam.x, b.at().y - cam.y, b.at().z - cam.z);
                ps.mulPose(new Quaternionf().rotationY((float) Math.toRadians(b.yaw())));
                ps.mulPose(new Quaternionf().rotationX((float) Math.toRadians(b.pitch())));
                ps.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-age / 4.0F)));
                PoseStack.Pose p = ps.last();
                int col = WorldFx.alpha(c, fade);
                v(vc, p, -half, -half, 0.0F, 0.0F, 0.0F, col);
                v(vc, p, half, -half, 0.0F, 1.0F, 0.0F, col);
                v(vc, p, half, half, 0.0F, 1.0F, 1.0F, col);
                v(vc, p, -half, half, 0.0F, 0.0F, 1.0F, col);
                ps.popPose();
            }
        }

        protected void onDisable() {
            this.bubbles.clear();
        }
    }

    // ---------------------------------------------------------------- body glow

    /** A soft glow sprite on every part of a player's body, so the figure shines from within. */
    public static final class BodyGlow extends Module {
        private final Setting.Num size = this.add(new Setting.Num("size", "Размер", 2.3, 1.5, 3.0, 0.1, "×"));
        private final Setting.Num opacity =
                this.add(new Setting.Num("opacity", "Прозрачность", 100.0, 0.0, 100.0, 1.0, "%"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public BodyGlow() {
            super("body_glow", "Body Glow", "Мягкое свечение по телу игроков", Category.PLAYERS, false);
        }

        void glow(EntityModel<?> model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c) {
            float a = this.opacity.get().floatValue() / 100.0F;
            if (a <= 0.0F) return;
            List<float[]> sprites = new ArrayList<>();
            Vector3f at = new Vector3f();
            model.root().visit(pose, (p, path, index, cube) -> {
                p.pose()
                        .transformPosition(
                                (cube.minX + cube.maxX) * 0.5F / 16.0F,
                                (cube.minY + cube.maxY) * 0.5F / 16.0F,
                                (cube.minZ + cube.maxZ) * 0.5F / 16.0F,
                                at);
                float w = (cube.maxX - cube.minX) / 16.0F;
                float h = (cube.maxY - cube.minY) / 16.0F;
                float d = (cube.maxZ - cube.minZ) / 16.0F;
                sprites.add(new float[] {at.x, at.y, at.z, (float) Math.sqrt(w * w + h * h + d * d) * state.scale});
            });
            if (sprites.isEmpty()) return;
            Quaternionf facing = mc().gameRenderer.getMainCamera().rotation();
            int col = WorldFx.alpha(this.colour.argb() | 0xFF000000, a);
            float scale = this.size.get().floatValue() * 0.5F;
            c.submitCustomGeometry(new PoseStack(), Fx.type(Fx.Tex.GLOW, true), (last, vc) -> {
                Vector3f r = new Vector3f();
                Vector3f u = new Vector3f();
                for (float[] s : sprites) {
                    float half = s[3] * scale;
                    facing.transform(r.set(half, 0.0F, 0.0F));
                    facing.transform(u.set(0.0F, half, 0.0F));
                    Matrix4f m = last.pose();
                    corner(vc, last, m, s, -r.x - u.x, -r.y - u.y, -r.z - u.z, 0.0F, 1.0F, col);
                    corner(vc, last, m, s, r.x - u.x, r.y - u.y, r.z - u.z, 1.0F, 1.0F, col);
                    corner(vc, last, m, s, r.x + u.x, r.y + u.y, r.z + u.z, 1.0F, 0.0F, col);
                    corner(vc, last, m, s, -r.x + u.x, -r.y + u.y, -r.z + u.z, 0.0F, 0.0F, col);
                }
            });
        }

        private static void corner(
                VertexConsumer vc,
                PoseStack.Pose p,
                Matrix4f m,
                float[] s,
                float dx,
                float dy,
                float dz,
                float u,
                float w,
                int c) {
            v(vc, p, s[0] + dx, s[1] + dy, s[2] + dz, u, w, c);
        }
    }

    // ---------------------------------------------------------------- halo

    /** A soft ring of light floating over your head (third person). */
    public static final class Halo extends Module {
        private final Setting.Num radius = this.add(new Setting.Num("radius", "Радиус", 0.45, 0.3, 1.5, 0.05, ""));
        private final Setting.Num height = this.add(new Setting.Num("height", "Высота", 0.0, -0.4, 0.6, 0.05, ""));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public Halo() {
            super("halo", "Нимб", "Светящийся нимб над твоей головой в 3-м лице", Category.PLAYERS, false);
        }

        void draw(HumanoidModel<?> m, PoseStack pose, SubmitNodeCollector c) {
            pose.pushPose();
            toHead(m, pose);
            float y = -0.72F - this.height.get().floatValue();
            float r = this.radius.get().floatValue();
            float t = (System.nanoTime() % 100000000000L) / 1.0E9F;
            float breathe = 0.85F + 0.15F * (float) Math.sin(t * 2.4);
            int col = this.colour.argb() | 0xFF000000;
            c.submitCustomGeometry(pose, Fx.type(Fx.Tex.RING_SOFT, true), (last, vc) -> {
                flat(vc, last, y, r * 1.08F, WorldFx.alpha(col, 0.35F * breathe));
                flat(vc, last, y, r, WorldFx.alpha(Fx.lerp(col, -1, 0.35F), 0.95F));
            });
            pose.popPose();
        }

        private static void flat(VertexConsumer vc, PoseStack.Pose p, float y, float r, int c) {
            v(vc, p, -r, y, -r, 0.0F, 0.0F, c);
            v(vc, p, r, y, -r, 1.0F, 0.0F, c);
            v(vc, p, r, y, r, 1.0F, 1.0F, c);
            v(vc, p, -r, y, r, 0.0F, 1.0F, c);
        }
    }

    // ---------------------------------------------------------------- china hat

    /** A wide shallow cone hat of soft colour over your head (third person). */
    public static final class ChinaHat extends Module {
        private static final int SEGMENTS = 48;
        private final Setting.Num width = this.add(new Setting.Num("width", "Ширина", 0.65, 0.4, 1.2, 0.05, ""));
        private final Setting.Num rise = this.add(new Setting.Num("rise", "Высота конуса", 0.35, 0.1, 0.8, 0.05, ""));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public ChinaHat() {
            super(
                    "china_hat",
                    "China Hat",
                    "Китайская шляпа-конус на твоей голове в 3-м лице",
                    Category.PLAYERS,
                    false);
        }

        void draw(HumanoidModel<?> m, PoseStack pose, SubmitNodeCollector c) {
            pose.pushPose();
            toHead(m, pose);
            float base = -0.52F;
            float apex = base - this.rise.get().floatValue();
            float r = this.width.get().floatValue();
            int col = this.colour.argb() | 0xFF000000;
            int top = WorldFx.alpha(Fx.lerp(col, -1, 0.25F), 0.55F);
            int rim = WorldFx.alpha(col, 0.3F);
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (last, vc) -> {
                for (int i = 0; i < SEGMENTS; i++) {
                    double a0 = i * Math.PI * 2.0 / SEGMENTS;
                    double a1 = (i + 1) * Math.PI * 2.0 / SEGMENTS;
                    float x0 = (float) Math.cos(a0) * r;
                    float z0 = (float) Math.sin(a0) * r;
                    float x1 = (float) Math.cos(a1) * r;
                    float z1 = (float) Math.sin(a1) * r;
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                    v(vc, last, x0, base, z0, 0.0F, 0.0F, rim);
                    v(vc, last, x1, base, z1, 1.0F, 0.0F, rim);
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                    v(vc, last, x1, base, z1, 1.0F, 0.0F, rim);
                    v(vc, last, x0, base, z0, 0.0F, 0.0F, rim);
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                }
            });
            c.submitCustomGeometry(pose, Fx.type(Fx.Tex.RING_SOFT, true), (last, vc) -> {
                int glow = WorldFx.alpha(Fx.lerp(col, -1, 0.3F), 0.7F);
                float rr = r * 1.04F;
                v(vc, last, -rr, base, -rr, 0.0F, 0.0F, glow);
                v(vc, last, rr, base, -rr, 1.0F, 0.0F, glow);
                v(vc, last, rr, base, rr, 1.0F, 1.0F, glow);
                v(vc, last, -rr, base, rr, 0.0F, 1.0F, glow);
            });
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- self nametag

    /** Your own name over your head when you look at yourself from behind or the front. */
    public static final class SelfName extends Module {
        public SelfName() {
            super("self_nametag", "Self Nametag", "Свой ник над головой в 3-м лице", Category.PLAYERS, false);
        }

        public boolean shows(Entity e) {
            Minecraft mc = mc();
            return this.enabled()
                    && e == mc.player
                    && !mc.options.getCameraType().isFirstPerson();
        }
    }

    // ---------------------------------------------------------------- world particles

    /** Motes, stars, snowflakes and more, drifting through the air around you. */
    public static final class WorldParticles extends Module {
        private final Setting.Mode kind = this.add(new Setting.Mode(
                "kind",
                "Вид",
                "Огоньки",
                "Огоньки",
                "Звёзды",
                "Снежинки",
                "Лепестки",
                "Пыльца",
                "Искры",
                "Сердца",
                "Перья"));
        private final Setting.Num rate =
                this.add(new Setting.Num("rate", "Сколько в секунду", 24.0, 2.0, 120.0, 1.0, ""));
        private final Setting.Num range = this.add(new Setting.Num("range", "Радиус", 9.0, 3.0, 32.0, 1.0, " бл"));
        private final Setting.Num height = this.add(new Setting.Num("height", "Высота", 6.0, 1.0, 20.0, 1.0, " бл"));
        private final Setting.Num speed = this.add(new Setting.Num("speed", "Скорость", 1.0, 0.0, 2.5, 0.05, ""));
        private final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.5, 2.5, 0.05, ""));
        private final Setting.Num life = this.add(new Setting.Num("life", "Время жизни", 6.0, 2.0, 12.0, 0.5, " с"));
        private final Setting.Num spin = this.add(new Setting.Num("spin", "Вращение", 50.0, 0.0, 100.0, 5.0, "%"));
        private final Setting.Num twinkle =
                this.add(new Setting.Num("twinkle", "Мерцание", 55.0, 0.0, 100.0, 5.0, "%"));
        private final Setting.Num glow = this.add(new Setting.Num("glow", "Свечение", 60.0, 0.0, 100.0, 5.0, "%"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));
        private float owed;

        public WorldParticles() {
            super(
                    "world_particles",
                    "World Particles",
                    "Частицы вокруг тебя: огоньки, звёзды, снежинки, лепестки и другое",
                    Category.WORLD,
                    false);
        }

        public void tick() {
            LocalPlayer me = mc().player;
            if (me == null || mc().level == null || mc().isPaused()) return;

            this.owed += this.rate.f() / 20.0F;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            while (this.owed >= 1.0F) {
                this.owed -= 1.0F;
                this.spawn(me, rnd);
            }
        }

        private void spawn(LocalPlayer me, ThreadLocalRandom rnd) {
            Level level = mc().level;
            if (level == null) return;
            float night = Math.min(1.0F, level.getSkyDarken() / 11.0F);

            double radius = this.range.get();
            double span = this.height.get();
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            // Slight bias towards the player, so flakes and sparks read as shapes instead of far-away dots.
            double distance = Math.pow(0.04 + rnd.nextDouble() * 0.96, 0.68) * radius;
            Vec3 at = new Vec3(
                    me.getX() + Math.cos(angle) * distance,
                    me.getY() - span * 0.18 + rnd.nextDouble() * span * 1.18,
                    me.getZ() + Math.sin(angle) * distance);
            if (at.distanceToSqr(me.getEyePosition()) < 0.7 || insideBlock(level, at)) return;

            float scale = this.size.f();
            float lifetime = this.life.f() * (0.85F + rnd.nextFloat() * 0.3F);
            float flow = this.speed.f();
            float shimmer = this.twinkle.f() / 100.0F;
            float shine = this.glow.f() / 100.0F;
            float turn = this.spin.f() / 100.0F;
            int base = this.colour.argb() | 0xFF000000;

            Fx.Tex tex;
            boolean additive;
            float core;
            float white;
            float lift;
            float fall;
            float sway;
            float swaySpeed;
            float spinSpeed;
            float kindTwinkle;
            float kindGlow;
            float twinkleSpeed;
            float halo;
            float glint;
            float gravity;
            switch (this.kind.index()) {
                case 1 -> { // stars
                    tex = Fx.Tex.STAR;
                    additive = true;
                    core = 0.16F;
                    white = 0.5F;
                    lift = 0.035F;
                    fall = 0.0F;
                    sway = 0.09F;
                    swaySpeed = 0.55F;
                    spinSpeed = 0.35F;
                    kindTwinkle = 1.0F;
                    kindGlow = 1.0F;
                    twinkleSpeed = 11.0F;
                    halo = 3.8F;
                    glint = 0.0F;
                    gravity = 0.0F;
                }
                case 2 -> { // snowflakes
                    tex = Fx.Tex.SNOWFLAKE;
                    additive = false;
                    core = 0.22F;
                    white = 0.75F;
                    lift = 0.0F;
                    fall = 0.15F;
                    sway = 0.22F;
                    swaySpeed = 0.85F;
                    spinSpeed = 0.9F;
                    kindTwinkle = 0.16F;
                    kindGlow = 0.35F;
                    twinkleSpeed = 7.0F;
                    halo = 2.0F;
                    glint = 1.0F;
                    gravity = 0.05F;
                }
                case 3 -> { // petals
                    tex = Fx.Tex.SAKURA;
                    additive = false;
                    core = 0.19F;
                    white = 0.18F;
                    lift = 0.0F;
                    fall = 0.12F;
                    sway = 0.3F;
                    swaySpeed = 0.95F;
                    spinSpeed = 1.8F;
                    kindTwinkle = 0.1F;
                    kindGlow = 0.5F;
                    twinkleSpeed = 6.0F;
                    halo = 2.7F;
                    glint = 0.55F;
                    gravity = 0.04F;
                }
                case 4 -> { // pollen
                    tex = Fx.Tex.SPARKLE;
                    additive = true;
                    core = 0.11F;
                    white = 0.42F;
                    lift = 0.06F;
                    fall = 0.0F;
                    sway = 0.14F;
                    swaySpeed = 0.9F;
                    spinSpeed = 0.55F;
                    kindTwinkle = 0.65F;
                    kindGlow = 0.9F;
                    twinkleSpeed = 14.0F;
                    halo = 3.2F;
                    glint = 0.0F;
                    gravity = 0.0F;
                }
                case 5 -> { // sparks
                    tex = Fx.Tex.FLAME;
                    additive = true;
                    core = 0.12F;
                    white = 0.35F;
                    lift = 0.22F;
                    fall = 0.0F;
                    sway = 0.2F;
                    swaySpeed = 1.5F;
                    spinSpeed = 0.2F;
                    kindTwinkle = 0.35F;
                    kindGlow = 1.1F;
                    twinkleSpeed = 24.0F;
                    halo = 3.4F;
                    glint = 0.0F;
                    gravity = -0.04F;
                }
                case 6 -> { // hearts
                    tex = Fx.Tex.HEART;
                    additive = false;
                    core = 0.16F;
                    white = 0.12F;
                    lift = 0.18F;
                    fall = 0.0F;
                    sway = 0.1F;
                    swaySpeed = 0.7F;
                    spinSpeed = 0.3F;
                    kindTwinkle = 0.08F;
                    kindGlow = 0.7F;
                    twinkleSpeed = 8.0F;
                    halo = 3.0F;
                    glint = 0.5F;
                    gravity = -0.015F;
                }
                case 7 -> { // feathers
                    tex = Fx.Tex.FEATHER;
                    additive = false;
                    core = 0.17F;
                    white = 0.28F;
                    lift = 0.0F;
                    fall = 0.16F;
                    sway = 0.34F;
                    swaySpeed = 1.1F;
                    spinSpeed = 1.05F;
                    kindTwinkle = 0.05F;
                    kindGlow = 0.48F;
                    twinkleSpeed = 5.0F;
                    halo = 2.5F;
                    glint = 0.0F;
                    gravity = 0.05F;
                }
                default -> { // motes
                    tex = Fx.Tex.MOTE;
                    additive = true;
                    core = 0.13F;
                    white = 0.34F;
                    lift = 0.07F;
                    fall = 0.0F;
                    sway = 0.15F;
                    swaySpeed = 0.75F;
                    spinSpeed = 0.0F;
                    kindTwinkle = 0.55F;
                    kindGlow = 1.0F;
                    twinkleSpeed = 12.0F;
                    halo = 3.5F;
                    glint = 0.0F;
                    gravity = 0.0F;
                }
            }

            float actual = core * scale * (0.8F + rnd.nextFloat() * 0.45F);
            // Shrink only a little over the lifetime: a flake should stay a flake, not fade into a dot.
            float endScale =
                    switch (this.kind.index()) {
                        case 1 -> 0.45F;
                        case 2 -> 0.7F;
                        case 3 -> 0.7F;
                        case 4 -> 0.4F;
                        case 5 -> 0.2F;
                        case 6 -> 0.55F;
                        case 7 -> 0.65F;
                        default -> 0.4F;
                    };
            double vx = rnd.nextGaussian() * 0.035 * flow;
            double vy = (lift + rnd.nextFloat() * lift * 0.7F - fall * rnd.nextFloat()) * flow;
            double vz = rnd.nextGaussian() * 0.035 * flow;
            float phase = rnd.nextFloat() * 6.2831855F;
            int tint = Fx.lerp(base, -1, Math.min(1.0F, white + rnd.nextFloat() * 0.14F));
            if (this.kind.index() == 2 && rnd.nextFloat() < 0.3F) tint = 0xFFFFFFFF;

            Fx.spawn()
                    .at(at)
                    .vel(vx, vy, vz)
                    .tex(tex)
                    .life(lifetime)
                    .size(actual, actual * endScale)
                    .colour(tint, base & 0xFFFFFF)
                    .drag(0.55F + rnd.nextFloat() * 0.35F)
                    .gravity(gravity * flow)
                    .blend(additive)
                    .fadeIn(0.7F + rnd.nextFloat() * 0.9F)
                    .twinkle(shimmer * kindTwinkle, twinkleSpeed * (0.8F + rnd.nextFloat() * 0.4F))
                    .spin(phase, spinSpeed * turn * (rnd.nextBoolean() ? 1.0F : -1.0F))
                    .sway(sway * flow, swaySpeed * (0.8F + rnd.nextFloat() * 0.4F), phase);

            if (shine > 0.01F) {
                int glowColor = Fx.lerp(base, -1, this.kind.index() == 2 ? 0.55F : 0.25F);
                int alpha = Math.round((26.0F + shine * 70.0F)
                        * kindGlow
                        * (0.35F + night * 0.65F)
                        * (0.7F + rnd.nextFloat() * 0.45F));
                Fx.spawn()
                        .at(at)
                        .vel(vx * 0.7, vy * 0.7, vz * 0.7)
                        .tex(Fx.Tex.GLOW)
                        .life(lifetime * 0.94F)
                        .size(actual * halo * (0.8F + shine * 0.5F), actual * 0.2F)
                        .colour((alpha << 24) | (glowColor & 0xFFFFFF), base & 0xFFFFFF)
                        .drag(0.55F)
                        .gravity(gravity * flow * 0.5F)
                        .fadeIn(1.0F + rnd.nextFloat())
                        .twinkle(shimmer * kindTwinkle * 0.7F, twinkleSpeed * 0.55F)
                        .spin(phase, 0.0F)
                        .sway(sway * flow * 0.7F, swaySpeed, phase);
            }

            if (glint > 0.01F) {
                // A tiny four-point star inside flakes, petals and hearts: it catches the light and keeps
                // the shape readable at dusk without turning the particle into a featureless blob.
                int glintColor = Fx.lerp(tint, -1, 0.65F);
                int alpha = Math.round((90.0F + shimmer * 70.0F) * glint * (0.2F + night * 0.8F));
                Fx.spawn()
                        .at(at)
                        .vel(vx * 0.8, vy * 0.8, vz * 0.8)
                        .tex(Fx.Tex.STAR)
                        .life(lifetime * 0.9F)
                        .size(actual * (0.24F + glint * 0.08F), actual * (0.12F + glint * 0.04F))
                        .colour((alpha << 24) | (glintColor & 0xFFFFFF), 0xFFFFFF)
                        .drag(0.55F)
                        .gravity(gravity * flow * 0.6F)
                        .fadeIn(0.6F + rnd.nextFloat() * 0.6F)
                        .twinkle(0.4F + shimmer * 0.45F, twinkleSpeed * 0.9F)
                        .spin(-phase, -spinSpeed * turn * 0.4F)
                        .sway(sway * flow * 0.8F, swaySpeed, phase);
            }
        }

        private static boolean insideBlock(Level level, Vec3 at) {
            BlockPos pos = BlockPos.containing(at);
            if (level.isOutsideBuildHeight(pos.getY())) return true;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return false;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) return false;
            Vec3 local = at.subtract((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
            for (AABB box : shape.toAabbs()) {
                if (box.inflate(0.01).contains(local)) return true;
            }
            return false;
        }
    }
}
