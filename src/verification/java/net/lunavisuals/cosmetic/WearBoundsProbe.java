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

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the whole wear catalogue and reports what cannot be drawn: a model that fails to parse, an empty one, a
 * missing texture, a broken pose (not a number) or bounds wildly out of the player's own size. Prints one line per
 * problem and a summary, so a broken cosmetic is found before the game shows it.
 */
public final class WearBoundsProbe {
    private WearBoundsProbe() {}

    /** The part each slot hangs from, as the player model places it: the pivot in model pixels. */
    private static float[] pivot(int pos) {
        return switch (pos) {
            case 1 -> new float[] {0.0F, 12.0F, 0.0F};
            case 2 -> new float[] {0.0F, 24.0F, 0.0F};
            case 3 -> new float[] {0.0F, 0.0F, 0.0F};
            case 4 -> new float[] {-5.0F, 22.0F, 0.0F};
            case 5 -> new float[] {5.0F, 22.0F, 0.0F};
            case 6 -> new float[] {-1.9F, 12.0F, 0.0F};
            case 7 -> new float[] {1.9F, 12.0F, 0.0F};
            default -> new float[] {0.0F, 0.0F, 0.0F};
        };
    }

    private static float lift(int pos, int category) {
        return switch (pos) {
            case 1 -> -0.3F;
            case 2 -> category == 3 ? 0.5F : 0.0F;
            // a pet's frame origin stands 1.5 blocks up; the renderer brings it back down to the ground
            case 3 -> -1.5F;
            case 4, 5 -> -0.25F;
            case 6, 7 -> -0.35F;
            default -> 0.0F;
        };
    }

    /** The player's own box, in model pixels, with room for a hat and wings: what a wearable should stay near. */
    private static final float LOW = -24.0F;

    private static final float HIGH = 48.0F;
    private static final float WIDE = 48.0F;

    public static void main(String[] args) {
        List<String> problems = new ArrayList<>();
        int n = 0;
        int ok = 0;
        int quads = 0;

        for (Wearables.Item it : Wearables.all()) {
            ++n;
            String tag = it.n() + " " + it.type() + " \"" + it.name() + "\"";
            Wearables.Geo geo = Wearables.Geo.get(it.n());
            if (geo == null) {
                problems.add(tag + ": the model does not parse");
                continue;
            }
            if (geo.quadCount == 0) {
                problems.add(tag + ": the model has no faces");
                continue;
            }
            if (Wearables.class.getResourceAsStream("/assets/lunavisuals/textures/wear/cosmetic_" + it.n() + ".png")
                    == null) {
                problems.add(tag + ": the texture is missing");
            }
            if (Wearables.class.getResourceAsStream("/assets/lunavisuals/textures/wear/icon_" + it.n() + ".png")
                    == null) {
                problems.add(tag + ": the wardrobe icon is missing");
            }
            float[] b = geo.bounds();
            for (float v : b) {
                if (!Float.isFinite(v)) {
                    problems.add(tag + ": the pose is not a number");
                    break;
                }
            }
            if (!Float.isFinite(b[0]) || !Float.isFinite(b[4])) {
                continue;
            }
            float[] p = pivot(it.pos());
            float tY = -(it.y() + lift(it.pos(), it.category()));
            float yo0 = p[1] + tY * 16.0F - b[4] * 16.0F;
            float yo1 = p[1] + tY * 16.0F - b[1] * 16.0F;
            float xo0 = p[0] - b[3] * 16.0F + it.x() * 16.0F;
            float xo1 = p[0] - b[0] * 16.0F + it.x() * 16.0F;
            if (yo0 < LOW || yo1 > HIGH) {
                problems.add(
                        String.format("%s: sits from y %.1f to %.1f, outside the player (-24..48)", tag, yo0, yo1));
            }
            if (xo0 < -WIDE || xo1 > WIDE) {
                problems.add(String.format("%s: reaches x %.1f to %.1f, wider than a player (48)", tag, xo0, xo1));
            }
            if (it.pos() < 1 || it.pos() > 7) {
                problems.add(tag + ": unknown slot " + it.pos());
            }
            ++ok;
            quads += geo.quadCount;
        }

        System.out.println("catalogue: " + n + " pieces, " + ok + " drawable, " + quads + " faces in all");

        // A pet is anchored on its own lowest point, so what matters is how far each model reaches below its
        // own origin: those are the ones that need lifting to keep their feet out of the ground.
        System.out.println("pets, deepest point of each model below its own origin:");
        for (Wearables.Item it : Wearables.all()) {
            if (!"pet".equals(it.type())) {
                continue;
            }
            Wearables.Geo geo = Wearables.Geo.get(it.n());
            if (geo == null) {
                continue;
            }
            float[] b = geo.bounds();
            float below = Math.min(0.0F, b[1]);
            System.out.printf(
                    "  %d %-16s reaches %.3f below its origin, stands %.3f tall%s%n",
                    it.n(), it.name(), -below, b[4] - b[1], below < -0.0001F ? "  <- needs lifting" : "");
        }
        if (problems.isEmpty()) {
            System.out.println("no problems");
        } else {
            System.out.println(problems.size() + " problems:");
            for (String s : problems) {
                System.out.println("  " + s);
            }
        }
    }
}
