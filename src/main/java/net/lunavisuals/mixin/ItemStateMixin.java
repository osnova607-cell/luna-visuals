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

import net.lunavisuals.fx.WorldPlus;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({ItemEntityRenderState.class})
public abstract class ItemStateMixin implements WorldPlus.ItemState {
    @Unique
    private boolean lunavisuals$onGround;

    @Unique
    private int lunavisuals$entity;

    public boolean lunavisuals$ground() {
        return this.lunavisuals$onGround;
    }

    public int lunavisuals$id() {
        return this.lunavisuals$entity;
    }

    public void lunavisuals$set(boolean ground, int id) {
        this.lunavisuals$onGround = ground;
        this.lunavisuals$entity = id;
    }
}
