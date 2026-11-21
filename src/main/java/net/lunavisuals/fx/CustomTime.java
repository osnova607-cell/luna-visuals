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
package net.lunavisuals.fx;

import net.lunavisuals.core.Category;
import net.lunavisuals.core.Module;
import net.lunavisuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class CustomTime extends Module {
    public final Setting.Num timeSutok =
            this.add(new Setting.Num("time", "Время суток", 6000.0, 0.0, 24000.0, 100.0, ""));

    public CustomTime() {
        super(
                "custom_time",
                "CustomTime",
                "Время суток: 0 — рассвет, 6000 — полдень, 12000 — закат, 18000 — полночь",
                Category.WORLD,
                false);
    }

    public long getLong() {
        return Math.round(this.timeSutok.get());
    }

    public void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.getLevelData().setDayTime(this.getLong());
        }
    }
}
