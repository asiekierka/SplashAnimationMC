/*
 * Copyright (c) 2018 Adrian Siekierka
 *
 * This file is part of SplashAnimation.
 *
 * SplashAnimation is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SplashAnimation is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SplashAnimation.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.asie.splashanimation.core;

import com.elytradev.mini.MiniCoremod;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.TransformerExclusions({"com.elytradev.mini", "pl.asie.splashanimation.core"})
@IFMLLoadingPlugin.SortingIndex(1001)
public class SplashAnimationCoremod extends MiniCoremod {
	public SplashAnimationCoremod() {
		super(SplashProgressTransformer.class);
	}
}
