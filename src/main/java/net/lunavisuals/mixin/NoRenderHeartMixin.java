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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws wither hearts as normal ones. Gui.HeartType is private, so it is matched by enum name. */
@Mixin(targets = "net.minecraft.client.gui.Gui$HeartType")
public abstract class NoRenderHeartMixin {
    @Unique
    private static Object lunavisuals$normal;

    @Inject(method = "forPlayer", at = @At("RETURN"), cancellable = true)
    private static void lunavisuals$witherHeart(CallbackInfoReturnable<Object> cir) {
        if (!NoRender.hides(Modules.NO_RENDER.witherHearts)
                || !(cir.getReturnValue() instanceof Enum<?> type)
                || !type.name().equals("WITHERED")) return;
        if (lunavisuals$normal == null) {
            for (Object c : type.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) c).name().equals("NORMAL")) lunavisuals$normal = c;
            }
        }
        if (lunavisuals$normal != null) cir.setReturnValue(lunavisuals$normal);
    }
}
