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

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The held items and how far each is raised, swapped for a moment by the knife studio's picture. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccess {
    @Accessor("mainHandItem")
    ItemStack lunavisuals$mainHandItem();

    @Accessor("mainHandItem")
    void lunavisuals$setMainHandItem(ItemStack stack);

    @Accessor("offHandItem")
    ItemStack lunavisuals$offHandItem();

    @Accessor("offHandItem")
    void lunavisuals$setOffHandItem(ItemStack stack);

    @Accessor("mainHandHeight")
    float lunavisuals$mainHandHeight();

    @Accessor("mainHandHeight")
    void lunavisuals$setMainHandHeight(float height);

    @Accessor("oMainHandHeight")
    float lunavisuals$oMainHandHeight();

    @Accessor("oMainHandHeight")
    void lunavisuals$setOMainHandHeight(float height);

    @Accessor("offHandHeight")
    float lunavisuals$offHandHeight();

    @Accessor("offHandHeight")
    void lunavisuals$setOffHandHeight(float height);

    @Accessor("oOffHandHeight")
    float lunavisuals$oOffHandHeight();

    @Accessor("oOffHandHeight")
    void lunavisuals$setOOffHandHeight(float height);
}
