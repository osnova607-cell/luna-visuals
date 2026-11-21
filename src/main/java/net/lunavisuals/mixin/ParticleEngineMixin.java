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
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ParticleEngine.class})
public abstract class ParticleEngineMixin {
    @Inject(
            method = {
                "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"
            },
            at = {@At("HEAD")},
            cancellable = true,
            require = 0)
    private void lunavisuals$hideVanillaTotem(Entity entity, ParticleOptions options, int life, CallbackInfo ci) {
        if (options.getType() == ParticleTypes.TOTEM_OF_UNDYING && Modules.TOTEM.hides(entity)) {
            ci.cancel();
        }
    }
}
