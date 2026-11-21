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

import net.lunavisuals.core.Modules;
import net.lunavisuals.fx.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clear sight under water and in lava: the fog is that of open air. */
@Mixin(FogRenderer.class)
public abstract class FluidFogMixin {
    @Inject(method = "getFogType", at = @At("RETURN"), cancellable = true)
    private void lunavisuals$clearFluids(Camera camera, CallbackInfoReturnable<FogType> cir) {
        FogType type = cir.getReturnValue();
        if ((type == FogType.WATER || type == FogType.LAVA) && NoRender.hides(Modules.NO_RENDER.fluidFog)) {
            cir.setReturnValue(FogType.NONE);
        }
    }
}
