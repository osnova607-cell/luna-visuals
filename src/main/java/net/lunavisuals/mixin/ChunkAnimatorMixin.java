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
package net.lunavisuals.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.lunavisuals.core.Modules;
import net.lunavisuals.fx.WorldPlus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({LevelRenderer.class})
public abstract class ChunkAnimatorMixin {
    @ModifyArg(
            require = 0,
            method = {"prepareChunkRenders"},
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/DynamicUniforms$ChunkSectionInfo;<init>(Lorg/joml/Matrix4fc;IIIFII)V")
            },
            index = 0)
    private Matrix4fc lunavisuals$rise(Matrix4fc mv, @Local SectionRenderDispatcher.RenderSection section) {
        WorldPlus.ChunkAnimator m = Modules.CHUNKS;
        if (m != null && m.enabled()) {
            BlockPos o = section.getRenderOrigin();
            return m.move(mv, ((SectionAccess) section).lunavisuals$uploaded(), o.getX(), o.getY(), o.getZ());
        } else {
            return mv;
        }
    }
}
