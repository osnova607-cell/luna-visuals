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

import net.lunavisuals.gui.FrontEndLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LoadingOverlay.class})
public abstract class LoadingOverlayMixin {
    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private long fadeOutStart;

    @Shadow
    private long fadeInStart;

    @Unique
    private long lunavisuals$last;

    @Unique
    private float lunavisuals$progress;

    @Unique
    private float lunavisuals$target;

    @Inject(
            method = {"render"},
            at = {@At("HEAD")},
            cancellable = true)
    private void lunavisuals$loading(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        ci.cancel();
        long now = Util.getMillis();
        if (this.fadeIn && this.fadeInStart < 0L) {
            this.fadeInStart = now;
        }

        float dt = this.lunavisuals$last == 0L ? 0.0F : Math.min(0.1F, (float) (now - this.lunavisuals$last) / 1000.0F);
        this.lunavisuals$last = now;
        this.lunavisuals$target = Math.max(
                this.lunavisuals$target,
                this.fadeOutStart >= 0L ? 1.0F : Math.min(0.995F, this.reload.getActualProgress()));
        this.lunavisuals$progress += (this.lunavisuals$target - this.lunavisuals$progress)
                * (1.0F - (float) Math.exp((double) (-dt * 12.0F)));
        if (this.lunavisuals$target == 1.0F && this.lunavisuals$progress > 0.998F) {
            this.lunavisuals$progress = 1.0F;
        }

        float leaving = -1.0F;
        if (this.fadeOutStart >= 0L) {
            FrontEndLoading.splashLeaving();
            leaving = FrontEndLoading.splashMs();
            if (leaving < 0.0F) {
                leaving = (float) Math.max(0L, now - this.fadeOutStart) * 1.6F;
            }
        }

        float in = this.fadeIn ? Math.max(0.0F, Math.min(1.0F, (float) (now - this.fadeInStart) / 300.0F)) : 1.0F;
        float alpha = in * in * (3.0F - 2.0F * in);
        if ((leaving >= 0.0F || this.fadeIn && in < 1.0F) && this.minecraft.screen != null) {
            this.minecraft.screen.renderWithTooltipAndSubtitles(g, mx, my, delta);
        }

        FrontEndLoading.scene(
                g,
                this.lunavisuals$progress,
                alpha,
                "Загрузка ресурсов",
                this.fadeIn || this.fadeOutStart >= 0L,
                true,
                leaving);
        if (leaving >= 1500.0F && this.minecraft.getOverlay() == (Object) this) {
            FrontEndLoading.splashGone();
            this.minecraft.setOverlay((Overlay) null);
        }
    }
}
