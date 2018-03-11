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

import com.elytradev.mini.MiniTransformer;
import com.elytradev.mini.PatchContext;
import com.elytradev.mini.annotation.Patch;
import org.objectweb.asm.tree.MethodInsnNode;

@Patch.Class("net.minecraftforge.fml.client.SplashProgress$2")
public class SplashProgressTransformer extends MiniTransformer {
	@Patch.Method(
			srg="run",
			mcp="run",
			descriptor="()V"
	)
	public void patch(PatchContext ctx) {
		// hehe, you're a cutie! x3
		ctx.search(
				new MethodInsnNode(INVOKEVIRTUAL, "java/util/concurrent/Semaphore", "acquireUninterruptibly", "()V", false)
		) // *flops beside you and nuzzles*
		.jumpBefore(); // awwww!~

		// *nuzzles you back and pounces on you
		ctx.add(
				new MethodInsnNode(INVOKESTATIC, "pl/asie/splashanimation/SplashAnimationRenderer", "run", "()V", false)
		);
		// and notices your coremod patch* OwO whats this..?

		ctx.jumpToEnd();
		ctx.searchBackward(
				new MethodInsnNode(INVOKESPECIAL, "net/minecraftforge/fml/client/SplashProgress$2", "clearGL", "()V", false)
		).jumpBefore();
		ctx.add(
				new MethodInsnNode(INVOKESTATIC, "pl/asie/splashanimation/SplashAnimationRenderer", "finish", "()V", false)
		);
	}
}
