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
package net.lunavisuals;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.lunavisuals.core.Config;
import net.lunavisuals.core.Module;
import net.lunavisuals.core.Modules;
import net.lunavisuals.emote.EmoteWheel;
import net.lunavisuals.emote.Emotes;
import net.lunavisuals.fx.AmbienceWeather;
import net.lunavisuals.fx.Effects;
import net.lunavisuals.fx.ExplosiveThing;
import net.lunavisuals.fx.ItemScroller;
import net.lunavisuals.fx.LunaChams;
import net.lunavisuals.fx.OptimizerMod;
import net.lunavisuals.fx.Studio;
import net.lunavisuals.fx.TntZone;
import net.lunavisuals.fx.TrapZone;
import net.lunavisuals.fx.WorldFx;
import net.lunavisuals.fx.impact.ImpactEffects;
import net.lunavisuals.gui.LunaMenu;
import net.lunavisuals.helper.EnchantSearch;
import net.lunavisuals.helper.HolyFeatures;
import net.lunavisuals.helper.ItemCatalog;
import net.lunavisuals.helper.Kits;
import net.lunavisuals.helper.ShulkerPreview;
import net.lunavisuals.helper.Waypoints;
import net.lunavisuals.hud.DynamicIsland;
import net.lunavisuals.hud.HudElements;
import net.lunavisuals.hud.HudModule;
import net.lunavisuals.hud.HudRenderer;
import net.lunavisuals.hud.Notify;
import net.lunavisuals.hud.TargetHud;
import net.lunavisuals.lua.Scripts;
import net.lunavisuals.media.Music;
import net.lunavisuals.melee.ClothSimulations;
import net.lunavisuals.melee.HandMovement;
import net.lunavisuals.melee.HandShaders;
import net.lunavisuals.melee.KnifeStudio;
import net.lunavisuals.melee.MeleeAnimator;
import net.lunavisuals.melee.MeleeShots;
import net.lunavisuals.melee.MeleeSkins;
import net.lunavisuals.render.Px;
import net.lunavisuals.render.TexWarm;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LunaVisuals implements ClientModInitializer {
    public static final String ID = "lunavisuals";
    private static final Logger LOGGER = LoggerFactory.getLogger("lunavisuals");
    private static KeyMapping menuKey;
    private static KeyMapping inspectKey;
    private static KeyMapping emoteKey;
    private static KeyMapping markKey;

    public void onInitializeClient() {
        Config.load();
        OptimizerMod.set(Modules.OPTIMIZER.enabled());
        ClientTickEvents.END_CLIENT_TICK.register(OptimizerMod::guard);
        ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join) (handler, sender, mc) -> {
            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland island) {
                    island.joined();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect) (handler, mc) -> {
            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland island) {
                    island.disconnected();
                }
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping) (mc) -> {
            HandShaders.close();
            Music.close();
        });
        KeyMapping.Category cat = Category.register(Identifier.fromNamespaceAndPath("lunavisuals", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunavisuals.menu", Type.KEYSYM, 344, cat));
        inspectKey =
                KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunavisuals.inspect", Type.KEYSYM, 71, cat));
        markKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunavisuals.mark", Type.KEYSYM, 78, cat));
        emoteKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunavisuals.emotes", Type.KEYSYM, 66, cat));
        Emotes.register();
        TrapZone.register();
        HolyFeatures.register();
        ItemScroller.register();
        ItemCatalog.register();
        Waypoints.register();
        Studio.register();
        KnifeStudio.register();
        net.lunavisuals.helper.FtEvents.register();
        net.lunavisuals.fx.FtRegions.register();
        net.lunavisuals.hud.Totems.register();
        net.lunavisuals.fx.Extras.register();
        net.lunavisuals.helper.ServerTools.register();
        net.lunavisuals.helper.ChestSorter.register();
        TntZone.register();
        EnchantSearch.register();
        ExplosiveThing.register();
        ShulkerPreview.register();
        Kits.register();
        Scripts.register();
        net.lunavisuals.media.MusicAccounts.load();
        ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick) (mc) -> {
            try {
                Scripts.tick();
                net.lunavisuals.media.MusicAccounts.tick();
            } catch (RuntimeException e) {
                LOGGER.debug("Lua tick failed", e);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick) (mc) -> {
            HandMovement.tick(mc);
            MeleeShots.tick(mc);
            MeleeSkins.tickAnimators();
            if (mc.level != null) {
                ClothSimulations.getInstance().tickAll(mc.level);
            } else {
                ClothSimulations.getInstance().clear();
            }

            while (inspectKey.consumeClick()) {
                if (mc.screen == null && (Modules.KNIVES.enabled() || Modules.GUNS.enabled())) {
                    MeleeAnimator.getInstance().onInspect();
                }
            }

            while (menuKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new LunaMenu());
                }
            }

            while (markKey.consumeClick()) {
                if (mc.screen == null && Modules.MARKS.enabled()) {
                    Modules.MARKS.key();
                }
            }

            while (emoteKey.consumeClick()) {
                if (mc.screen == null && mc.player != null && Modules.EMOTES.enabled()) {
                    mc.setScreen(new EmoteWheel(emoteKey));
                }
            }

            for (Module m : Modules.ALL) {
                if (m.enabled()) {
                    try {
                        m.tick();
                    } catch (RuntimeException e) {
                        LOGGER.debug("Client tick failed", e);
                    }
                }
            }

            Config.tick();
            TexWarm.pump(4000000L);
            Notify.tick(mc);

            try {
                EmoteWheel.warm(mc);
            } catch (RuntimeException e) {
                LOGGER.debug("Client tick failed", e);
            }

            Effects.tick();
            if (Modules.MARKER.enabled()) {
                TargetHud.update();
            }
        });
        AttackEntityCallback.EVENT.register((AttackEntityCallback) (player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) {
                if (Modules.HIT.enabled()) {
                    Modules.HIT.onAttack(player, entity);
                }

                if (Modules.KILL.enabled()) {
                    Modules.KILL.onAttack(entity);
                }

                ImpactEffects.attack(entity);
                if (Modules.KILLSOUND.enabled()) {
                    Modules.KILLSOUND.onAttack(entity);
                }

                if (Modules.HITSOUND.enabled()) {
                    Modules.HITSOUND.onAttack(entity);
                }

                if (Modules.COMBO.enabled()) {
                    Modules.COMBO.onAttack(player, entity);
                }

                TargetHud.hit(entity);
                Scripts.attack(entity);
            }

            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((UseItemCallback) (player, level, hand) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player && Modules.POTION_FX.enabled()) {
                Modules.POTION_FX.used(player.getItemInHand(hand));
            }

            return InteractionResult.PASS;
        });
        ClientPreAttackCallback.EVENT.register((ClientPreAttackCallback) (mc, player, clicks) -> {
            if (clicks > 0) {
                HudElements.Clicks.click();
            }

            return false;
        });
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((WorldRenderEvents.BeforeBlockOutline)
                (ctx, state) -> !Modules.BLOCK.enabled() || !Modules.BLOCK.outline.on());
        WorldRenderEvents.END_MAIN.register(AmbienceWeather::render);
        WorldRenderEvents.END_MAIN.register(AmbienceWeather::potionWaves);
        WorldRenderEvents.END_MAIN.register(LunaChams::render);
        WorldRenderEvents.AFTER_ENTITIES.register((WorldRenderEvents.AfterEntities) (ctx) -> {
            try {
                WorldFx.render(ctx);
            } catch (RuntimeException e) {
                LOGGER.debug("Client tick failed", e);
            }

            try {
                net.lunavisuals.cosmetic.Wearables.world(ctx);
            } catch (RuntimeException e) {
                LOGGER.debug("Pet render failed", e);
            }
        });
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunavisuals", "hud"),
                (g, dt) -> HudRenderer.renderAll(g));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunavisuals", "lua"),
                (g, dt) -> Scripts.render(g));
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, (vanilla) -> (g, dt) -> {
            vanilla.render(g, dt);
            Modules.SATURATION.overlay(g);
            Modules.TOTEM_BAR.render(g);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, (vanilla) -> (g, dt) -> {
            if (!Modules.BOSS_BARS.enabled()) {
                vanilla.render(g, dt);
            }
        });
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, (vanilla) -> (g, dt) -> {
            if (Modules.TAB.enabled()) {
                Modules.TAB.render(g);
            } else {
                Modules.UI_ANIMATIONS.tab(g, () -> vanilla.render(g, dt));
            }
        });
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, (vanilla) -> (g, dt) -> {
            if (Modules.CROSSHAIR.enabled()) {
                if (!Minecraft.getInstance().options.hideGui) {
                    Modules.CROSSHAIR.render(g);
                }
            } else {
                vanilla.render(g, dt);
            }

            if (Modules.COMBO.enabled()
                    && !Minecraft.getInstance().options.hideGui
                    && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                Px.begin(g);

                try {
                    Modules.COMBO.render(g);
                } finally {
                    Px.end(g);
                }
            }
        });
    }
}
