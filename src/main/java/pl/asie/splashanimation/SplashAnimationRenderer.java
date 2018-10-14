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
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;

public class SplashAnimationRenderer {
	public static class ImageProvider implements Runnable {
		private final LinkedList<File> fileList = new LinkedList<>();
		private final LinkedList<BufferedImage> imageList = new LinkedList<>();
		private final int bufferSize;
		private int lastImage = 0;
		private boolean run = true;

		public ImageProvider(Collection<File> files) {
			fileList.addAll(files);
			// ~1 second
			bufferSize = Math.max(5, (int) Math.ceil(1 / frameDelay));
		}

		public BufferedImage getImage(int pos) {
			while (lastImage < pos) {
				if (imageList.size() == 1) {
					return imageList.peekFirst();
				} else if (imageList.isEmpty()) {
					return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
				}

				imageList.remove();
				lastImage++;
			}

			if (imageList.isEmpty()) {
				return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			}

			return imageList.peekFirst();
		}

		@Override
		public void run() {
			while (run) {
				try {
					while (imageList.size() < bufferSize && !fileList.isEmpty()) {
						File f = fileList.remove();

						try {
							BufferedImage img = ImageIO.read(f);

							synchronized (imageList) {
								imageList.add(img);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					Thread.sleep(Math.round(frameDelay * 1000));
				} catch (InterruptedException e) {

				}
			}
		}

		public void stop() {
			this.run = false;
		}
	}

	private static boolean animationSolid = false;
	private static boolean animationScaleUp = false;
	private static boolean animationScaleDown = false;
	private static boolean animationScaleFilter = false;
	private static int backgroundColor = 0;
	private static float frameDelay = 0;
	private static float fadeOutTime = 0;
	private static long startTime = 0;
	private static int animTexture;
	private static int animTexWidth, animTexHeight;
	private static int stage = 0;
	private static int width = -1;
	private static int height = -1;

	private static ImageProvider provider;
	private static Thread providerThread;
	private static int frameCount;

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
		animationScaleUp = config.getBoolean("enableScalingUp", "animation", false, "Should the animation scale up to fill the screen?");
		animationScaleDown = config.getBoolean("enableScalingDown", "animation", true, "Should the animation scale down to fill the screen?");
		animationScaleFilter = config.getBoolean("enableScalingFilter", "animation", true, "Should the animation, if scaled, use a bilinear filter?");

		backgroundColor = Integer.parseInt(config.getString("backgroundColor", "animation", "000000", "The background color used during the animation."), 16);
		frameDelay = config.getFloat("frameDelay", "animation", 0.03f, 0.005f, 1.0f, "The delay for each frame of animation, in seconds.");
		fadeOutTime = config.getFloat("fadeOutTime", "animation", 1.0f, 0.0f, 5.0f, "The fade out time after the final frame of animation.");
		String frameStr = config.getString("frameFileDirectory", "animation", "animation", "The directory containing the animation frames, which should be of the filename format [number].[extension].");

		if (config.hasChanged()) {
			config.save();
		}

		File imgDir = new File(frameStr);

		TreeMap<Integer, File> files = new TreeMap<>();

		if (imgDir.exists() && imgDir.isDirectory()) {
			for (File imgFile : imgDir.listFiles()) {
				String s = imgFile.getName().split("\\.")[0];
				try {
					Integer i = Integer.valueOf(s);
					if (i >= 0) {
						files.put(i, imgFile);
					}
				} catch (NumberFormatException e) {
					// pass
				}
			}
		}

		List<File> imageFiles = new ArrayList<>();

		for (Map.Entry<Integer, File> entry : files.entrySet()) {
			imageFiles.add(entry.getValue());
			try {
				BufferedImage image = ImageIO.read(entry.getValue());
				width = image.getWidth();
				height = image.getHeight();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		if (imageFiles.isEmpty() || width < 0 || height < 0) {
			System.err.println("Found no images!");
			stage = 2;
			return;
		}

		frameCount = imageFiles.size();
		provider = new ImageProvider(imageFiles);
		providerThread = new Thread(provider);
		providerThread.start();

		animTexWidth = MathHelper.smallestEncompassingPowerOfTwo(width);
		animTexHeight = MathHelper.smallestEncompassingPowerOfTwo(height);

		int maxSize = SplashProgress.getMaxTextureSize();
		if (animTexWidth > maxSize || animTexHeight > maxSize) {
			System.err.println("Could not fit animation: " + maxSize + " too small");
			stage = 2;
			return;
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		animTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, animTexture);
		if (animationScaleFilter) {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		} else {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		}
		GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, animTexWidth, animTexHeight, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer)null);
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		startTime = System.currentTimeMillis();
	}

	private static int uploadedFrame = -1;

	private static void bindFrame(int i) {
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, animTexture);

		if (i != uploadedFrame) {
			BufferedImage img = provider.getImage(i);

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
		int finalElapsedMs = (int) (frameCount * (frameDelay * 1000));

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

		float w = Display.getWidth();
		float h = Display.getHeight();
		float iw = width;
		float ih = height;

		GL11.glViewport(0, 0, (int) w, (int) h);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glOrtho(-w/2f, w/2f, h/2f, -h/2f, -1, 1);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();

		float maxU = iw / animTexWidth;
		float maxV = ih / animTexHeight;

		// scale iw/ih to w/h
		float imgRatio = iw / ih;
		float dispRatio = w / h;

		float scalingFactor = (dispRatio > imgRatio) ? (h / ih) : (w / iw);

		if ((animationScaleUp && scalingFactor > 1.0f) || (animationScaleDown && scalingFactor < 1.0f)) {
			iw *= scalingFactor;
			ih *= scalingFactor;
		}

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		glColor(backgroundColor, alpha);
		GL11.glBegin(GL11.GL_QUADS);
		if (animationSolid && alpha < 1.0f) {
			GL11.glVertex2f(-w / 2f, -h / 2f);
			GL11.glVertex2f(-w / 2f, -ih / 2f);
			GL11.glVertex2f(w / 2f, -ih / 2f);
			GL11.glVertex2f(w / 2f, -h / 2f);

			GL11.glVertex2f(-w / 2f, ih / 2f);
			GL11.glVertex2f(-w / 2f, h / 2f);
			GL11.glVertex2f(w / 2f, h / 2f);
			GL11.glVertex2f(w / 2f, ih / 2f);

			GL11.glVertex2f(-w / 2f, -ih / 2f);
			GL11.glVertex2f(-w / 2f, ih / 2f);
			GL11.glVertex2f(-iw / 2f, ih / 2f);
			GL11.glVertex2f(-iw / 2f, -ih / 2f);

			GL11.glVertex2f(iw / 2f, -ih / 2f);
			GL11.glVertex2f(iw / 2f, ih / 2f);
			GL11.glVertex2f(w / 2f, ih / 2f);
			GL11.glVertex2f(w / 2f, -ih / 2f);
		} else {
			GL11.glVertex2f(-w / 2f, -h / 2f);
			GL11.glVertex2f(-w / 2f, h / 2f);
			GL11.glVertex2f(w / 2f, h / 2f);
			GL11.glVertex2f(w / 2f, -h / 2f);
		}
		GL11.glEnd();

		bindFrame(i);

		GL11.glColor4f(1, 1, 1, alpha);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0, 0);
		GL11.glVertex2f(-iw/2f, -ih/2f);
		GL11.glTexCoord2f(0, maxV);
		GL11.glVertex2f(-iw/2f, ih/2f);
		GL11.glTexCoord2f(maxU, maxV);
		GL11.glVertex2f(iw/2f, ih/2f);
		GL11.glTexCoord2f(maxU, 0);
		GL11.glVertex2f(iw/2f, -ih/2f);
		GL11.glEnd();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
	}

	public static void finish() {
		if (stage <= 2) {
			GL11.glDeleteTextures(animTexture);
			if (provider != null) {
				provider.stop();
			}
			provider = null;
			providerThread = null;
			stage = 3;
		}
	}
}
