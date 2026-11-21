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

/** A stick between two cloth points that keeps them at a fixed distance. */
final class ClothNode {
    final ClothLerp pointA;
    final ClothLerp pointB;
    final float length;

    ClothNode(ClothLerp pointA, ClothLerp pointB, float length) {
        this.pointA = pointA;
        this.pointB = pointB;
        this.length = length;
    }
}
