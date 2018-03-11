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

package pl.asie.splashanimation;

import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.SplashProgress;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;

public class SplashAnimationRenderer {
	private static boolean animationSolid = false;
	private static int backgroundColor = 0;
	private static float frameDelay = 0;
	private static float fadeOutTime = 0;
	private static long startTime = 0;
	private static List<BufferedImage> images = new ArrayList<>();
	private static int animTexture;
	private static int animTexWidth, animTexHeight;
	private static int stage = 0;

	public static void run() {
		switch (stage) {
			case 0:
				init();
				if (stage == 2) {
					break;
				}
				stage = 1;
			case 1:
				render();
				break;
			case 2:
				finish();
				break;
			case 3:
				break;
		}
	}

	private static void init() {
		File confDir = new File("config");
		Configuration config = new Configuration(new File(confDir, "splashanimation.cfg"));
		animationSolid = config.getBoolean("areFramesSolid", "animation", true, "Are the animation frames solid?");
		backgroundColor = Integer.parseInt(config.getString("backgroundColor", "animation", "000000", "The background color used during the animation."), 16);
		frameDelay = config.getFloat("frameDelay", "animation", 0.03f, 0.005f, 1.0f, "The delay for each frame of animation, in seconds.");
		fadeOutTime = config.getFloat("fadeOutTime", "animation", 1.0f, 0.0f, 5.0f, "The fade out time after the final frame of animation.");
		String frameStr = config.getString("frameFileFormat", "animation", "%03d.png", "The filename template for each frame of animation, starting from 0.");

		if (config.hasChanged()) {
			config.save();
		}

		boolean addImages = true;
		File imgDir = new File("animation");
		int i = 0;
		while (addImages) {
			File imgFile = new File(imgDir, String.format(frameStr, i));
			try {
				// System.out.println(imgFile.getAbsolutePath());
				BufferedImage image = ImageIO.read(imgFile);
				images.add(image);
				if (images.size() > 1) {
					if ((images.get(0).getWidth() != image.getWidth())
						|| (images.get(0).getHeight() != image.getHeight())) {
						throw new RuntimeException("Mismatched animation frame sizes: 0 =/= " + i);
					}
				}
			} catch (Exception e) {
				break;
			}
			i++;
		}

		if (images.isEmpty()) {
			System.err.println("Found no images!");
			stage = 2;
			return;
		}

		animTexWidth = MathHelper.smallestEncompassingPowerOfTwo(images.get(0).getWidth());
		animTexHeight = MathHelper.smallestEncompassingPowerOfTwo(images.get(0).getHeight());

		int maxSize = SplashProgress.getMaxTextureSize();
		if (animTexWidth > maxSize || animTexHeight > maxSize) {
			System.err.println("Could not fit animation: " + maxSize + " too small");
			stage = 2;
			return;
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		animTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, animTexture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, animTexWidth, animTexHeight, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer)null);
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		startTime = System.currentTimeMillis();
	}

	private static int uploadedFrame = -1;

	private static void bindFrame(int i) {
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, animTexture);

		if (i != uploadedFrame && (i >= 0 && i < images.size())) {
			BufferedImage img = images.get(i);

			int[] t = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
			IntBuffer buf = BufferUtils.createIntBuffer(t.length);
			buf.put(t);
			buf.position(0);

			GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, img.getWidth(), img.getHeight(), GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buf);
			uploadedFrame = i;
		}
	}

	private static void glColor(int v) {
		GL11.glColor4f(
				((v >> 16) & 0xFF) / 255.0f,
				((v >> 8) & 0xFF) / 255.0f,
				(v & 0xFF) / 255.0f,
				((v >> 24) & 0xFF) / 255.0f
		);
	}

	private static void glColor(int v, float a) {
		GL11.glColor4f(
				((v >> 16) & 0xFF) / 255.0f,
				((v >> 8) & 0xFF) / 255.0f,
				(v & 0xFF) / 255.0f,
				a
		);
	}

	private static void render() {
		int finalElapsedMs = (int) (images.size() * (frameDelay * 1000));

		int elapsedMs = (int) (System.currentTimeMillis() - startTime);
		int i = (int) ((elapsedMs / 1000.0f) / frameDelay);
		float alpha = 1.0f;

		if (elapsedMs >= finalElapsedMs) {
			alpha = 1.0f - ((elapsedMs - finalElapsedMs) / (fadeOutTime * 1000));
			if (alpha < 0.0f) {
				stage = 2;
				return;
			}
		}

		int w = Display.getWidth();
		int h = Display.getHeight();
		int iw = images.get(0).getWidth();
		int ih = images.get(0).getHeight();

		GL11.glViewport(0, 0, w, h);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glOrtho(-w/2, w/2, h/2, -h/2, -1, 1);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();

		float maxU = (float) iw / animTexWidth;
		float maxV = (float) ih / animTexHeight;

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		glColor(backgroundColor, alpha);
		GL11.glBegin(GL11.GL_QUADS);
		if (animationSolid) {
			GL11.glVertex2f(-w / 2, -h / 2);
			GL11.glVertex2f(-w / 2, -ih / 2);
			GL11.glVertex2f(w / 2, -ih / 2);
			GL11.glVertex2f(w / 2, -h / 2);
			GL11.glVertex2f(-w / 2, ih / 2);
			GL11.glVertex2f(-w / 2, h / 2);
			GL11.glVertex2f(w / 2, h / 2);
			GL11.glVertex2f(w / 2, ih / 2);
			GL11.glVertex2f(-w / 2, -ih / 2);
			GL11.glVertex2f(-w / 2, ih / 2);
			GL11.glVertex2f(-iw / 2, ih / 2);
			GL11.glVertex2f(-iw / 2, -ih / 2);
			GL11.glVertex2f(iw / 2, -ih / 2);
			GL11.glVertex2f(iw / 2, ih / 2);
			GL11.glVertex2f(w / 2, ih / 2);
			GL11.glVertex2f(w / 2, -ih / 2);
		} else {
			GL11.glVertex2f(-w / 2, -h / 2);
			GL11.glVertex2f(-w / 2, h / 2);
			GL11.glVertex2f(w / 2, h / 2);
			GL11.glVertex2f(w / 2, -h / 2);
		}
		GL11.glEnd();

		bindFrame(i);

		GL11.glColor4f(1, 1, 1, alpha);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0, 0);
		GL11.glVertex2f(-iw/2, -ih/2);
		GL11.glTexCoord2f(0, maxV);
		GL11.glVertex2f(-iw/2, ih/2);
		GL11.glTexCoord2f(maxU, maxV);
		GL11.glVertex2f(iw/2, ih/2);
		GL11.glTexCoord2f(maxU, 0);
		GL11.glVertex2f(iw/2, -ih/2);
		GL11.glEnd();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
	}

	public static void finish() {
		if (stage <= 2) {
			GL11.glDeleteTextures(animTexture);
			images.clear();
			stage = 3;
		}
	}
}
