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

import net.lunavisuals.core.Keys;
import net.lunavisuals.core.Modules;
import net.lunavisuals.hud.DynamicIsland;
import net.lunavisuals.hud.HudDrag;
import net.lunavisuals.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MouseHandler.class})
public abstract class MouseHandlerMixin {
    private static double pixelX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos()
                * (double) mc.getWindow().getWidth()
                / (double) mc.getWindow().getScreenWidth();
    }

    private static double pixelY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos()
                * (double) mc.getWindow().getHeight()
                / (double) mc.getWindow().getScreenHeight();
    }

    @Inject(
            method = {"onMove"},
            at = {@At("TAIL")})
    private void lunavisuals$drag(long window, double xpos, double ypos, CallbackInfo ci) {
        if (HudDrag.holding() && HudDrag.chatOpen()) {
            HudDrag.move(pixelX(), pixelY());
        }
    }

    @Inject(
            method = {"onButton"},
            at = {@At("HEAD")},
            cancellable = true)
    private void lunavisuals$bind(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action == 1 && mc.screen instanceof ChatScreen) {
            double x = mc.mouseHandler.xpos()
                    * (double) mc.getWindow().getWidth()
                    / (double) mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos()
                    * (double) mc.getWindow().getHeight()
                    / (double) mc.getWindow().getScreenHeight();
            if (info.button() == 0 && Modules.HOLY_EVENTS_HUD.clickRow(x, y)) {
                ci.cancel();
                return;
            }

            HudModule under = info.button() == 0 ? HudDrag.at(x, y, false) : null;
            if (under instanceof DynamicIsland || under == Modules.HOLY_EVENTS_HUD) {
                under = null;
            }

            if (under != null) {
                HudDrag.press(under, x, y);
                ci.cancel();
                return;
            }

            if (Modules.MARKS.click(x, y, info.button())) {
                ci.cancel();
                return;
            }

            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland) {
                    DynamicIsland island = (DynamicIsland) h;
                    if (island.musicClick(x, y, info.button())) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        if (action == 0 && HudDrag.holding()) {
            HudModule clicked = HudDrag.release();
            if (clicked instanceof DynamicIsland) {
                DynamicIsland island = (DynamicIsland) clicked;
                island.musicClick(pixelX(), pixelY(), 0);
            }

            ci.cancel();
        } else {
            if (action == 1 && info.button() >= 2) {
                Object before = mc.screen;
                try {
                    Keys.pressed(1000 + info.button());
                } catch (RuntimeException ignored) {
                }

                // a bound middle click is the local friend list: it must not also pick a block
                if (mc.screen != before
                        || (info.button() == 2 && Modules.FRIENDS.enabled() && Modules.FRIENDS.middleClick.on())) {
                    ci.cancel();
                }
            }
        }
    }
}
