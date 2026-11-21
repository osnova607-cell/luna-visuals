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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.lunavisuals.core.Modules;
import net.lunavisuals.helper.SoundMixer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundEngine.class})
public abstract class SoundEngineMixin {
    @Inject(
            method = {"play"},
            at = {@At("HEAD")},
            cancellable = true,
            require = 0)
    private void lunavisuals$hitsoundOnly(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        try {
            if (sound != null && Modules.HITSOUND.silences(sound)) {
                cir.setReturnValue(PlayResult.NOT_STARTED);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @ModifyReturnValue(
            require = 0,
            method = {"calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"},
            at = {@At("RETURN")})
    private float lunavisuals$mixer(float volume, @Local(argsOnly = true) SoundInstance sound) {
        try {
            return SoundMixer.scale(sound, volume);
        } catch (RuntimeException e) {
            return volume;
        }
    }
}
