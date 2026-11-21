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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A parsed Blockbench {@code .bbmodel} avatar: cubes, meshes, embedded textures and baked
 * animations. Blockbench coordinates are kept as authored (y up, 1 unit = 1/16 block); the root
 * matrix in {@link #render} applies the same scale/flip Figura uses for avatars.
 */
final class ModelGeo {
    private static final Map<String, ModelGeo> CACHE = new ConcurrentHashMap<>();
    private static final String[] FACE_NAMES = {"west", "east", "down", "up", "north", "south"};
    private static final float[][] FACE_NORMALS = {
        {-1.0F, 0.0F, 0.0F},
        {1.0F, 0.0F, 0.0F},
        {0.0F, -1.0F, 0.0F},
        {0.0F, 1.0F, 0.0F},
        {0.0F, 0.0F, -1.0F},
        {0.0F, 0.0F, 1.0F}
    };
    private static final float[][] FACE_UV = {
        {0.0F, 0.0F},
        {1.0F, 0.0F},
        {1.0F, 1.0F},
        {0.0F, 1.0F}
    };

    private final List<Bone> roots = new ArrayList<>();
    private final List<Skin> skins = new ArrayList<>();
    private final List<Anim> anims = new ArrayList<>();
    private final int[] layerQuads;
    private final float[][] frame;
    private final int[] counters;
    private final Matrix4f[] stack = new Matrix4f[64];
    private final Vector3f tmp = new Vector3f();
    private final Vector3f tmp2 = new Vector3f();
    private int quadCount;
    private byte[] previewTexture;

    private ModelGeo(List<Skin> skins, int[] layerQuads) {
        this.skins.addAll(skins);
        this.layerQuads = layerQuads;
        this.frame = new float[skins.size()][];
        this.counters = new int[skins.size()];
        for (int i = 0; i < skins.size(); i++) {
            if (layerQuads[i] > 0) {
                this.frame[i] = new float[layerQuads[i] * 32];
            }
        }
    }

    static ModelGeo get(Models.Entry entry) {
        if (entry == null || entry.model() == null) {
            return null;
        }
        ModelGeo geo = CACHE.computeIfAbsent(entry.model().toString(), key -> load(entry));
        return geo == null ? null : geo;
    }

    int quadCount() {
        return this.quadCount;
    }

    byte[] previewTexture() {
        return this.previewTexture;
    }

    void render(PoseStack pose, SubmitNodeCollector collector, int light, Models.Drive drive) {
        if (this.quadCount == 0) {
            return;
        }

        Anim anim = this.choose(drive);
        float time = 0.0F;
        if (anim != null) {
            double t = drive.seconds();
            if (anim.loop && anim.length > 0.0F) {
                t = t % (double) anim.length;
                if (t < 0.0) {
                    t += anim.length;
                }
            } else if (anim.length > 0.0F) {
                t = Math.min(t, (double) anim.length);
            }
            time = (float) Math.max(0.0, t);
        }

        Arrays.fill(this.counters, 0);
        Matrix4f root = this.matrix(0).identity();
        for (Bone bone : this.roots) {
            this.bone(bone, root, 0, anim, time);
        }

        pose.pushPose();
        pose.translate(0.0F, 1.5F, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

        for (int i = 0; i < this.skins.size(); i++) {
            Skin skin = this.skins.get(i);
            int count = this.counters[i];
            float[] data = this.frame[i];
            if (skin.id == null || count == 0 || data == null) {
                continue;
            }
            int packedLight = skin.glow ? Models.fullBright() : light;
            collector.submitCustomGeometry(
                    pose,
                    skin.glow
                            ? RenderTypes.entityTranslucentEmissive(skin.id)
                            : RenderTypes.entityCutoutNoCull(skin.id),
                    (pp, vc) -> this.emit(pp, vc, data, count, packedLight));
        }

        pose.popPose();
    }

    private void emit(PoseStack.Pose pp, VertexConsumer vc, float[] data, int count, int light) {
        for (int q = 0; q < count; q++) {
            int base = q * 32;
            for (int v = 0; v < 4; v++) {
                int o = base + v * 8;
                vc.addVertex(pp, data[o], data[o + 1], data[o + 2])
                        .setColor(-1)
                        .setUv(data[o + 3], data[o + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pp, data[o + 5], data[o + 6], data[o + 7]);
            }
        }
    }

    private Matrix4f matrix(int depth) {
        if (this.stack[depth] == null) {
            this.stack[depth] = new Matrix4f();
        }
        return this.stack[depth];
    }

    private void bone(Bone b, Matrix4f parent, int depth, Anim anim, float time) {
        Marker:
        if (depth < this.stack.length - 2) {
            Matrix4f m = this.matrix(depth).set(parent);
            float rx = b.rx;
            float ry = b.ry;
            float rz = b.rz;
            float px = 0.0F;
            float py = 0.0F;
            float pz = 0.0F;
            float sx = 1.0F;
            float sy = 1.0F;
            float sz = 1.0F;

            Track[] tracks = anim == null ? null : b.tracks.get(anim);
            if (tracks != null) {
                float[] out = new float[3];
                if (tracks[0] != null) {
                    tracks[0].at(time, out);
                    rx += out[0];
                    ry += out[1];
                    rz += out[2];
                }
                if (tracks[1] != null) {
                    tracks[1].at(time, out);
                    px = out[0];
                    py = out[1];
                    pz = out[2];
                }
                if (tracks[2] != null) {
                    tracks[2].at(time, out);
                    sx = out[0];
                    sy = out[1];
                    sz = out[2];
                }
            }

            if (px != 0.0F || py != 0.0F || pz != 0.0F) {
                m.translate(px, py, pz);
            }

            m.translate(b.px, b.py, b.pz);
            if (b.mesh) {
                if (rx != 0.0F) {
                    m.rotateX((float) Math.toRadians((double) rx));
                }
                if (ry != 0.0F) {
                    m.rotateY((float) Math.toRadians((double) ry));
                }
                if (rz != 0.0F) {
                    m.rotateZ((float) Math.toRadians((double) rz));
                }
            } else {
                if (rz != 0.0F) {
                    m.rotateZ((float) Math.toRadians((double) rz));
                }
                if (ry != 0.0F) {
                    m.rotateY((float) Math.toRadians((double) ry));
                }
                if (rx != 0.0F) {
                    m.rotateX((float) Math.toRadians((double) rx));
                }
            }
            if (sx != 1.0F || sy != 1.0F || sz != 1.0F) {
                m.scale(sx, sy, sz);
            }
            m.translate(-b.px, -b.py, -b.pz);

            for (Quad quad : b.quads) {
                int layer = quad.layer;
                float[] out = this.frame[layer];
                if (out == null) {
                    continue;
                }
                int at = this.counters[layer] * 32;
                if (at + 32 > out.length) {
                    continue;
                }
                this.counters[layer]++;

                for (int v = 0; v < 4; v++) {
                    int s = v * 5;
                    int o = at + v * 8;
                    m.transformPosition(quad.v[s], quad.v[s + 1], quad.v[s + 2], this.tmp);
                    out[o] = this.tmp.x;
                    out[o + 1] = this.tmp.y;
                    out[o + 2] = this.tmp.z;
                    out[o + 3] = quad.v[s + 3];
                    out[o + 4] = quad.v[s + 4];
                }

                this.tmp.set(out[at + 8] - out[at], out[at + 9] - out[at + 1], out[at + 10] - out[at + 2]);
                this.tmp2.set(out[at + 16] - out[at], out[at + 17] - out[at + 1], out[at + 18] - out[at + 2]);
                this.tmp.cross(this.tmp2);
                if (this.tmp.lengthSquared() > 1.0E-10F) {
                    this.tmp.normalize();
                } else {
                    this.tmp.set(0.0F, 1.0F, 0.0F);
                }

                for (int v = 0; v < 4; v++) {
                    int o = at + v * 8;
                    out[o + 5] = this.tmp.x;
                    out[o + 6] = this.tmp.y;
                    out[o + 7] = this.tmp.z;
                }
            }

            for (Bone kid : b.kids) {
                this.bone(kid, m, depth + 1, anim, time);
            }
            break Marker;
        }
    }

    private Anim choose(Models.Drive d) {
        if (this.anims.isEmpty()) {
            return null;
        }

        int pick = -1;
        if (d.air() > 0.5F) {
            pick = this.find("elytra", "flying", "fly");
        }
        if (pick < 0 && d.water() > 0.5F) {
            if (d.walk() > 0.2F) {
                pick = this.find("swimming", "swim", "waterwalk", "water");
            }
            if (pick < 0) {
                pick = this.find("swimming", "swim", "water");
            }
        }
        if (pick < 0 && d.sneak() > 0.5F) {
            if (d.walk() > 0.2F) {
                pick = this.find("crouchwalk", "crouchingwalking", "sneakwalk", "crouch", "sneak");
            }
            if (pick < 0) {
                pick = this.find("crouch", "crouching", "sneak");
            }
        }
        if (pick < 0 && d.walk() > 0.2F) {
            if (d.sprint()) {
                pick = this.find("sprint", "sprinting", "run");
            }
            if (pick < 0) {
                pick = this.find("walking", "walk", "moving", "move");
            }
        }
        if (pick < 0) {
            pick = this.find("idle", "idled", "idle_1");
        }
        if (pick < 0) {
            pick = 0;
        }
        return pick < 0 ? null : this.anims.get(pick);
    }

    private int find(String... tokens) {
        for (String token : tokens) {
            for (int i = 0; i < this.anims.size(); i++) {
                if (this.anims.get(i).name.equals(token)) {
                    return i;
                }
            }
        }
        for (String token : tokens) {
            for (int i = 0; i < this.anims.size(); i++) {
                if (this.anims.get(i).name.startsWith(token)) {
                    return i;
                }
            }
        }
        for (String token : tokens) {
            for (int i = 0; i < this.anims.size(); i++) {
                if (this.anims.get(i).name.contains(token)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static ModelGeo load(Models.Entry entry) {
        try (Reader reader = Files.newBufferedReader(entry.model(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            float resolutionWidth = 64.0F;
            float resolutionHeight = 64.0F;
            JsonObject resolution = object(root, "resolution");
            if (resolution != null) {
                resolutionWidth = Math.max(1.0F, number(resolution, "width", 64.0F));
                resolutionHeight = Math.max(1.0F, number(resolution, "height", 64.0F));
            }

            List<Skin> skins = new ArrayList<>();
            JsonArray textureArray = array(root, "textures");
            if (textureArray != null) {
                for (int i = 0; i < textureArray.size(); i++) {
                    if (!textureArray.get(i).isJsonObject()) {
                        continue;
                    }
                    JsonObject def = textureArray.get(i).getAsJsonObject();
                    Skin skin = new Skin();
                    skin.uvW = Math.max(1.0F, number(def, "uv_width", number(def, "width", resolutionWidth)));
                    skin.uvH = Math.max(1.0F, number(def, "uv_height", number(def, "height", resolutionHeight)));
                    float pw = number(def, "width", 0.0F);
                    float ph = number(def, "height", 0.0F);
                    skin.area = (int) Math.max(1.0F, pw * ph);
                    String name = string(def, "name", "texture" + i);
                    skin.glow = isGlow(name);
                    skin.bytes = resolveTexture(entry, def);
                    if (skin.bytes != null) {
                        skin.id = Models.texture(skin.bytes);
                        if (skin.area <= 1) {
                            skin.area = skin.bytes.length;
                        }
                    }
                    skins.add(skin);
                }
            }

            List<Skin> finalSkins = skins;
            Map<String, Elem> elements = new HashMap<>();
            JsonArray elementArray = array(root, "elements");
            if (elementArray != null) {
                for (JsonElement el : elementArray) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    Elem elem = element(el.getAsJsonObject(), finalSkins);
                    if (elem != null) {
                        elements.put(elem.uuid, elem);
                    }
                }
            }

            Map<String, Bone> byUuid = new HashMap<>();
            Map<String, Bone> byName = new HashMap<>();
            List<Bone> roots = new ArrayList<>();
            Set<String> used = new HashSet<>();

            JsonArray outliner = array(root, "outliner");
            if (outliner != null) {
                for (JsonElement ce : outliner) {
                    addOutliner(ce, roots, elements, byUuid, byName, used);
                }
            }
            if (roots.isEmpty() && !elements.isEmpty()) {
                for (Elem elem : elements.values()) {
                    roots.add(elem.bone);
                    byUuid.put(elem.uuid, elem.bone);
                    byName.putIfAbsent(elem.bone.name, elem.bone);
                    used.add(elem.uuid);
                }
            }

            int[] layerQuads = new int[skins.size()];
            List<Bone> all = new ArrayList<>();
            for (Bone bone : roots) {
                collect(bone, layerQuads, all);
            }
            int quadCount = 0;
            for (int count : layerQuads) {
                quadCount += count;
            }
            for (Bone bone : all) {
                if (bone.uuid != null) {
                    byUuid.put(bone.uuid, bone);
                }
                if (bone.name != null) {
                    byName.putIfAbsent(bone.name, bone);
                }
            }

            ModelGeo geo = new ModelGeo(skins, layerQuads);
            geo.roots.addAll(roots);
            geo.quadCount = quadCount;
            for (Skin skin : skins) {
                if (skin.bytes != null && (geo.previewTexture == null || skin.area > geo.previewArea())) {
                    geo.previewTexture = skin.bytes;
                }
            }

            JsonArray animations = array(root, "animations");
            if (animations != null) {
                for (JsonElement ae : animations) {
                    if (!ae.isJsonObject()) {
                        continue;
                    }
                    JsonObject anim = ae.getAsJsonObject();
                    String rawName = string(anim, "name", "");
                    String name = rawName.trim().toLowerCase(Locale.ROOT);
                    if (name.isEmpty() || separator(name)) {
                        continue;
                    }
                    float length = Math.max(0.0F, number(anim, "length", number(anim, "animation_length", 0.0F)));
                    boolean loop = loop(anim);
                    Map<Bone, Track[]> tracks = new HashMap<>();

                    JsonObject animators = object(anim, "animators");
                    if (animators != null) {
                        for (Map.Entry<String, JsonElement> entry2 : animators.entrySet()) {
                            if (!entry2.getValue().isJsonObject()) {
                                continue;
                            }
                            JsonObject animator = entry2.getValue().getAsJsonObject();
                            Bone bone = byUuid.get(entry2.getKey());
                            if (bone == null) {
                                bone = byName.get(entry2.getKey());
                            }
                            if (bone == null) {
                                bone = byName.get(string(animator, "name", ""));
                            }
                            if (bone == null) {
                                continue;
                            }
                            JsonArray keyframes = array(animator, "keyframes");
                            if (keyframes == null) {
                                continue;
                            }

                            List<Key> rotations = new ArrayList<>();
                            List<Key> positions = new ArrayList<>();
                            List<Key> scales = new ArrayList<>();
                            for (JsonElement ke : keyframes) {
                                if (!ke.isJsonObject()) {
                                    continue;
                                }
                                JsonObject keyframe = ke.getAsJsonObject();
                                String channel = string(keyframe, "channel", "").toLowerCase(Locale.ROOT);
                                float at = number(keyframe, "time", 0.0F);
                                float[] value = keyValue(keyframe);
                                if (value == null) {
                                    continue;
                                }
                                boolean step = "step".equalsIgnoreCase(string(keyframe, "interpolation", ""));
                                Key key = new Key(at, value[0], value[1], value[2], step);
                                switch (channel) {
                                    case "rotation" -> rotations.add(key);
                                    case "position" -> positions.add(key);
                                    case "scale" -> scales.add(key);
                                    default -> {}
                                }
                                if (at > length) {
                                    length = Math.max(0.0F, at);
                                }
                            }

                            Track[] tr = {track(rotations), track(positions), track(scales)};
                            if (tr[0] != null || tr[1] != null || tr[2] != null) {
                                tracks.put(bone, tr);
                            }
                        }
                    }

                    if (length <= 0.0F) {
                        length = 1.0F;
                    }
                    if (!tracks.isEmpty()) {
                        geo.anims.add(new Anim(name, length, loop));
                        Anim stored = geo.anims.get(geo.anims.size() - 1);
                        for (Map.Entry<Bone, Track[]> entry2 : tracks.entrySet()) {
                            entry2.getKey().tracks.put(stored, entry2.getValue());
                        }
                    }
                }
            }

            return geo;
        } catch (Exception e) {
            System.err.println("[LunaVisuals] model " + entry.name() + ": " + e);
            return null;
        }
    }

    private int previewArea() {
        // Only used while parsing; the largest texture is kept, so a rough byte length is enough.
        return this.previewTexture == null ? 0 : this.previewTexture.length;
    }

    private static void collect(Bone bone, int[] layerQuads, List<Bone> all) {
        all.add(bone);
        for (Quad quad : bone.quads) {
            if (quad.layer >= 0 && quad.layer < layerQuads.length) {
                layerQuads[quad.layer]++;
            }
        }
        for (Bone kid : bone.kids) {
            collect(kid, layerQuads, all);
        }
    }

    private static void addOutliner(
            JsonElement ce,
            List<Bone> parent,
            Map<String, Elem> elements,
            Map<String, Bone> byUuid,
            Map<String, Bone> byName,
            Set<String> used) {
        if (ce.isJsonPrimitive()) {
            Elem elem = elements.get(ce.getAsString());
            if (elem != null && used.add(elem.uuid)) {
                parent.add(elem.bone);
                byUuid.put(elem.uuid, elem.bone);
                byName.putIfAbsent(elem.bone.name, elem.bone);
            }
            return;
        }
        if (!ce.isJsonObject()) {
            return;
        }

        JsonObject group = ce.getAsJsonObject();
        if (!bool(group, "export", true) || !bool(group, "visibility", true)) {
            return;
        }

        Bone bone = new Bone();
        bone.name = string(group, "name", "group");
        bone.uuid = string(group, "uuid", null);
        float[] origin = vec3(group, "origin");
        float[] rotation = vec3(group, "rotation");
        bone.px = origin[0];
        bone.py = origin[1];
        bone.pz = origin[2];
        bone.rx = rotation[0];
        bone.ry = rotation[1];
        bone.rz = rotation[2];
        parent.add(bone);
        if (bone.uuid != null) {
            byUuid.put(bone.uuid, bone);
        }
        byName.putIfAbsent(bone.name, bone);

        JsonArray children = array(group, "children");
        if (children != null) {
            for (JsonElement child : children) {
                addOutliner(child, bone.kids, elements, byUuid, byName, used);
            }
        }
    }

    private static Elem element(JsonObject o, List<Skin> skins) {
        if (!bool(o, "export", true) || !bool(o, "visibility", true)) {
            return null;
        }
        String type = string(o, "type", "cube").toLowerCase(Locale.ROOT);
        boolean mesh = "mesh".equals(type);
        Bone bone = new Bone();
        bone.name = string(o, "name", "part");
        bone.uuid = string(o, "uuid", null);
        float[] origin = vec3(o, "origin");
        float[] rotation = vec3(o, "rotation");
        bone.px = origin[0];
        bone.py = origin[1];
        bone.pz = origin[2];
        bone.rx = rotation[0];
        bone.ry = rotation[1];
        bone.rz = rotation[2];
        bone.mesh = mesh;

        if (mesh) {
            addMesh(bone, o, skins);
        } else {
            addCube(bone, o, skins);
        }
        if (bone.quads.isEmpty()) {
            return null;
        }
        Elem elem = new Elem();
        elem.uuid = bone.uuid == null ? bone.name + "@" + System.identityHashCode(bone) : bone.uuid;
        elem.bone = bone;
        return elem;
    }

    private static void addCube(Bone bone, JsonObject o, List<Skin> skins) {
        float[] from = vec3(o, "from");
        float[] to = vec3(o, "to");
        float inflate = number(o, "inflate", 0.0F);
        float x0 = Math.min(from[0], to[0]) - inflate;
        float y0 = Math.min(from[1], to[1]) - inflate;
        float z0 = Math.min(from[2], to[2]) - inflate;
        float x1 = Math.max(from[0], to[0]) + inflate;
        float y1 = Math.max(from[1], to[1]) + inflate;
        float z1 = Math.max(from[2], to[2]) + inflate;

        JsonObject faces = object(o, "faces");
        if (faces == null) {
            return;
        }

        for (int f = 0; f < FACE_NAMES.length; f++) {
            JsonElement fe = faces.get(FACE_NAMES[f]);
            if (fe == null || !fe.isJsonObject()) {
                continue;
            }
            JsonObject face = fe.getAsJsonObject();
            int texture = (int) number(face, "texture", -1.0F);
            if (texture < 0 || texture >= skins.size() || skins.get(texture).id == null) {
                continue;
            }
            float[] uv = vec4(face, "uv");
            if (uv == null) {
                continue;
            }

            Skin skin = skins.get(texture);
            float u1 = uv[0] / skin.uvW;
            float v1 = uv[1] / skin.uvH;
            float u2 = uv[2] / skin.uvW;
            float v2 = uv[3] / skin.uvH;
            int shift = Math.floorMod(Math.round(number(face, "rotation", 0.0F) / 90.0F), 4);
            float[][] pos = cubeFace(f, x0, y0, z0, x1, y1, z1);
            float[][] uvs = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
            if (f == 2 || f == 3) {
                uvs = new float[][] {{u1, v1}, {u1, v2}, {u2, v2}, {u2, v1}};
            }

            Quad quad = new Quad(texture);
            for (int v = 0; v < 4; v++) {
                int b = v * 5;
                quad.v[b] = pos[v][0];
                quad.v[b + 1] = pos[v][1];
                quad.v[b + 2] = pos[v][2];
                quad.v[b + 3] = uvs[(v + shift) % 4][0];
                quad.v[b + 4] = uvs[(v + shift) % 4][1];
            }
            bone.quads.add(quad);
        }
    }

    private static float[][] cubeFace(int f, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (f) {
            case 0 -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case 1 -> new float[][] {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case 2 -> new float[][] {{x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case 3 -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case 4 -> new float[][] {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            default -> new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
        };
    }

    private static void addMesh(Bone bone, JsonObject o, List<Skin> skins) {
        JsonObject vertices = object(o, "vertices");
        JsonObject faces = object(o, "faces");
        if (vertices == null || faces == null) {
            return;
        }

        Map<String, float[]> positions = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : vertices.entrySet()) {
            float[] p = vec3(entry.getValue());
            if (p != null) {
                positions.put(entry.getKey(), p);
            }
        }

        for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject face = entry.getValue().getAsJsonObject();
            int texture = (int) number(face, "texture", -1.0F);
            if (texture < 0 || texture >= skins.size() || skins.get(texture).id == null) {
                continue;
            }
            JsonArray ids = array(face, "vertices");
            JsonObject uvs = object(face, "uv");
            if (ids == null || uvs == null || ids.size() < 3 || ids.size() > 4) {
                continue;
            }

            Skin skin = skins.get(texture);
            Quad quad = new Quad(texture);
            boolean ok = true;
            for (int v = 0; v < 4; v++) {
                String id = ids.get(Math.min(v, ids.size() - 1)).getAsString();
                float[] p = positions.get(id);
                float[] uv = vec2(uvs.get(id));
                if (p == null || uv == null) {
                    ok = false;
                    break;
                }
                int b = v * 5;
                quad.v[b] = p[0];
                quad.v[b + 1] = p[1];
                quad.v[b + 2] = p[2];
                quad.v[b + 3] = uv[0] / skin.uvW;
                quad.v[b + 4] = uv[1] / skin.uvH;
            }
            if (ok) {
                bone.quads.add(quad);
            }
        }
    }

    private static boolean isGlow(String name) {
        String clean = name.toLowerCase(Locale.ROOT);
        if (clean.endsWith(".png")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        return clean.endsWith("_e") || clean.endsWith("_emissive") || clean.contains("emissive");
    }

    private static boolean separator(String name) {
        String clean = name.replace("-", "").replace("=", "").replace("_", "").trim();
        return clean.isEmpty();
    }

    private static byte[] resolveTexture(Models.Entry entry, JsonObject def) {
        Path modelDir = entry.model().getParent();
        List<Path> candidates = new ArrayList<>();
        String relative = string(def, "relative_path", "");
        if (!relative.isBlank()) {
            candidates.add(modelDir.resolve(relative));
            candidates.add(entry.directory().resolve(relative));
        }
        String name = string(def, "name", "");
        if (!name.isBlank()) {
            addTextureName(candidates, modelDir, name);
            addTextureName(candidates, entry.directory(), name);
        }
        String path = string(def, "path", "");
        if (!path.isBlank()) {
            try {
                Path external = Path.of(path);
                if (external.getFileName() != null) {
                    addTextureName(candidates, modelDir, external.getFileName().toString());
                    addTextureName(
                            candidates,
                            entry.directory(),
                            external.getFileName().toString());
                }
            } catch (Exception ignored) {
            }
        }

        Path base = entry.directory().toAbsolutePath().normalize();
        for (Path candidate : candidates) {
            try {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (!normalized.startsWith(base) || !Files.isRegularFile(normalized)) {
                    continue;
                }
                return Files.readAllBytes(normalized);
            } catch (Exception ignored) {
            }
        }
        return Models.decodeSource(string(def, "source", ""));
    }

    private static void addTextureName(List<Path> candidates, Path dir, String name) {
        candidates.add(dir.resolve(name));
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            candidates.add(dir.resolve(name + ".png"));
        }
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String string(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    private static float number(JsonObject o, String key, float def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return def;
        }
        return e.getAsFloat();
    }

    private static float[] vec3(JsonObject o, String key) {
        return vec3(o.get(key));
    }

    private static float[] vec3(JsonElement e) {
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonArray a = e.getAsJsonArray();
        if (a.size() < 3) {
            return null;
        }
        try {
            return new float[] {
                a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static float[] vec4(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonArray a = e.getAsJsonArray();
        if (a.size() < 4) {
            return null;
        }
        try {
            return new float[] {
                a.get(0).getAsFloat(),
                a.get(1).getAsFloat(),
                a.get(2).getAsFloat(),
                a.get(3).getAsFloat()
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static float[] vec2(JsonElement e) {
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonArray a = e.getAsJsonArray();
        if (a.size() < 2) {
            return null;
        }
        try {
            return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean loop(JsonObject anim) {
        JsonElement e = anim.get("loop");
        if (e == null || !e.isJsonPrimitive()) {
            return true;
        }
        try {
            if (e.getAsJsonPrimitive().isBoolean()) {
                return e.getAsBoolean();
            }
            String value = e.getAsString().toLowerCase(Locale.ROOT);
            return !value.equals("once") && !value.equals("hold") && !value.equals("false");
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static float[] keyValue(JsonObject keyframe) {
        JsonArray points = array(keyframe, "data_points");
        if (points != null) {
            for (JsonElement point : points) {
                float[] value = point(point);
                if (value != null) {
                    return value;
                }
            }
        }
        float[] direct = point(keyframe);
        if (direct != null) {
            return direct;
        }
        JsonElement vector = keyframe.get("vector");
        return vector == null ? null : point(vector);
    }

    private static float[] point(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            JsonElement x = o.get("x");
            JsonElement y = o.get("y");
            JsonElement z = o.get("z");
            if (x != null && y != null && z != null) {
                try {
                    if (!x.getAsJsonPrimitive().isNumber()
                            || !y.getAsJsonPrimitive().isNumber()
                            || !z.getAsJsonPrimitive().isNumber()) {
                        return null;
                    }
                    return new float[] {x.getAsFloat(), y.getAsFloat(), z.getAsFloat()};
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
            if (o.has("post")) {
                return point(o.get("post"));
            }
            if (o.has("vector")) {
                return point(o.get("vector"));
            }
            return null;
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) {
                return null;
            }
            try {
                return new float[] {
                    a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()
                };
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Track track(List<Key> keys) {
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort(Comparator.comparingDouble(key -> key.time));
        float[] t = new float[keys.size()];
        float[][] v = new float[keys.size()][];
        boolean[] step = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            t[i] = key.time;
            v[i] = new float[] {key.x, key.y, key.z};
            step[i] = key.step;
        }
        return new Track(t, v, step);
    }

    private record Key(float time, float x, float y, float z, boolean step) {}

    private static final class Track {
        private final float[] t;
        private final float[][] v;
        private final boolean[] step;

        Track(float[] t, float[][] v, boolean[] step) {
            this.t = t;
            this.v = v;
            this.step = step;
        }

        void at(float time, float[] out) {
            int n = this.t.length;
            if (n == 0) {
                out[0] = out[1] = out[2] = 0.0F;
            } else if (time <= this.t[0]) {
                System.arraycopy(this.v[0], 0, out, 0, 3);
            } else if (time >= this.t[n - 1]) {
                System.arraycopy(this.v[n - 1], 0, out, 0, 3);
            } else {
                int i = 1;
                while (i < n && this.t[i] < time) {
                    i++;
                }
                if (this.step[i - 1]) {
                    System.arraycopy(this.v[i - 1], 0, out, 0, 3);
                } else {
                    float k = (time - this.t[i - 1]) / Math.max(1.0E-6F, this.t[i] - this.t[i - 1]);
                    for (int c = 0; c < 3; c++) {
                        out[c] = this.v[i - 1][c] + (this.v[i][c] - this.v[i - 1][c]) * k;
                    }
                }
            }
        }
    }

    private static final class Anim {
        private final String name;
        private final float length;
        private final boolean loop;

        Anim(String name, float length, boolean loop) {
            this.name = name;
            this.length = length;
            this.loop = loop;
        }
    }

    private static final class Bone {
        private String name;
        private String uuid;
        private float px;
        private float py;
        private float pz;
        private float rx;
        private float ry;
        private float rz;
        private boolean mesh;
        private final List<Bone> kids = new ArrayList<>();
        private final List<Quad> quads = new ArrayList<>();
        private final Map<Anim, Track[]> tracks = new HashMap<>();
    }

    private static final class Quad {
        private final float[] v = new float[20];
        private final int layer;

        Quad(int layer) {
            this.layer = layer;
        }
    }

    private static final class Skin {
        private Identifier id;
        private float uvW;
        private float uvH;
        private boolean glow;
        private int area;
        private byte[] bytes;
    }

    private static final class Elem {
        private String uuid;
        private Bone bone;
    }
}
