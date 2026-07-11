package io.github.wesleym.augur.samples.gif;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small median-cut color quantizer. It builds one shared palette across all
 * frames of a clip and maps every frame onto it, so the animated GIF stores a
 * single compact color table and indexes into it — smaller than letting the
 * encoder pick a fresh 256-color palette per frame. The flat UI tolerates the
 * reduced palette without visible banding.
 */
final class Quantizer {
	private Quantizer() { }

	static List<BufferedImage> quantize(List<BufferedImage> frames, int maxColors, boolean dither) {
		Map<Integer, int[]> histogram = new HashMap<>();
		for (BufferedImage frame : frames) {
			int[] pixels = frame.getRGB(0, 0, frame.getWidth(), frame.getHeight(), null, 0, frame.getWidth());
			for (int argb : pixels) {
				int rgb = argb & 0xFFFFFF;
				int[] entry = histogram.computeIfAbsent(rgb, k -> new int[] {(k >> 16) & 0xFF, (k >> 8) & 0xFF, k & 0xFF, 0});
				entry[3]++;
			}
		}

		List<int[]> palette = medianCut(new ArrayList<>(histogram.values()), maxColors);
		int size = palette.size();
		byte[] reds = new byte[size];
		byte[] greens = new byte[size];
		byte[] blues = new byte[size];
		for (int i = 0; i < size; i++) {
			reds[i] = (byte) palette.get(i)[0];
			greens[i] = (byte) palette.get(i)[1];
			blues[i] = (byte) palette.get(i)[2];
		}
		int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, size - 1)));
		IndexColorModel model = new IndexColorModel(bits, size, reds, greens, blues);

		Map<Integer, Integer> nearest = new HashMap<>();
		List<BufferedImage> out = new ArrayList<>(frames.size());
		for (BufferedImage frame : frames) {
			BufferedImage indexed = new BufferedImage(frame.getWidth(), frame.getHeight(),
					BufferedImage.TYPE_BYTE_INDEXED, model);
			if (dither) {
				ditherFrame(frame, indexed, palette);
			} else {
				mapFrame(frame, indexed, palette, nearest);
			}
			out.add(indexed);
		}
		return out;
	}

	private static void mapFrame(BufferedImage frame, BufferedImage indexed, List<int[]> palette,
			Map<Integer, Integer> nearest) {
		WritableRaster raster = indexed.getRaster();
		int w = frame.getWidth();
		int[] pixels = frame.getRGB(0, 0, w, frame.getHeight(), null, 0, w);
		for (int p = 0; p < pixels.length; p++) {
			int rgb = pixels[p] & 0xFFFFFF;
			int index = nearest.computeIfAbsent(rgb, c -> nearestIndex(palette, c));
			raster.setSample(p % w, p / w, 0, index);
		}
	}

	/** Floyd–Steinberg error diffusion: smooths shadow ramps and antialiased edges at small palettes. */
	private static void ditherFrame(BufferedImage frame, BufferedImage indexed, List<int[]> palette) {
		int w = frame.getWidth();
		int h = frame.getHeight();
		WritableRaster raster = indexed.getRaster();
		int[] pixels = frame.getRGB(0, 0, w, h, null, 0, w);
		float[] r = new float[w * h];
		float[] g = new float[w * h];
		float[] b = new float[w * h];
		for (int i = 0; i < pixels.length; i++) {
			r[i] = (pixels[i] >> 16) & 0xFF;
			g[i] = (pixels[i] >> 8) & 0xFF;
			b[i] = pixels[i] & 0xFF;
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				int cr = clamp(r[i]);
				int cg = clamp(g[i]);
				int cb = clamp(b[i]);
				int index = nearestIndex(palette, cr, cg, cb);
				int[] c = palette.get(index);
				raster.setSample(x, y, 0, index);
				float er = cr - c[0];
				float eg = cg - c[1];
				float eb = cb - c[2];
				spread(r, g, b, i + 1, x + 1 < w, er, eg, eb, 7f / 16);
				spread(r, g, b, i + w - 1, y + 1 < h && x > 0, er, eg, eb, 3f / 16);
				spread(r, g, b, i + w, y + 1 < h, er, eg, eb, 5f / 16);
				spread(r, g, b, i + w + 1, y + 1 < h && x + 1 < w, er, eg, eb, 1f / 16);
			}
		}
	}

	private static void spread(float[] r, float[] g, float[] b, int i, boolean inBounds,
			float er, float eg, float eb, float weight) {
		if (inBounds) {
			r[i] += er * weight;
			g[i] += eg * weight;
			b[i] += eb * weight;
		}
	}

	private static int clamp(float v) {
		return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
	}

	private static List<int[]> medianCut(List<int[]> colors, int maxColors) {
		List<List<int[]>> boxes = new ArrayList<>();
		boxes.add(colors);
		while (boxes.size() < maxColors) {
			List<int[]> target = null;
			int targetRange = 0;
			int targetChannel = 0;
			for (List<int[]> box : boxes) {
				if (box.size() < 2) {
					continue;
				}
				for (int ch = 0; ch < 3; ch++) {
					int min = 255;
					int max = 0;
					for (int[] color : box) {
						min = Math.min(min, color[ch]);
						max = Math.max(max, color[ch]);
					}
					if (max - min > targetRange) {
						targetRange = max - min;
						targetChannel = ch;
						target = box;
					}
				}
			}
			if (target == null) {
				break;
			}
			final int channel = targetChannel;
			target.sort((a, b) -> Integer.compare(a[channel], b[channel]));
			long total = 0;
			for (int[] color : target) {
				total += color[3];
			}
			long half = total / 2;
			long acc = 0;
			int split = 1;
			for (int i = 0; i < target.size(); i++) {
				acc += target.get(i)[3];
				if (acc >= half) {
					split = Math.max(1, Math.min(target.size() - 1, i + 1));
					break;
				}
			}
			boxes.remove(target);
			boxes.add(new ArrayList<>(target.subList(0, split)));
			boxes.add(new ArrayList<>(target.subList(split, target.size())));
		}

		List<int[]> palette = new ArrayList<>(boxes.size());
		for (List<int[]> box : boxes) {
			long r = 0;
			long g = 0;
			long b = 0;
			long n = 0;
			for (int[] color : box) {
				r += (long) color[0] * color[3];
				g += (long) color[1] * color[3];
				b += (long) color[2] * color[3];
				n += color[3];
			}
			n = Math.max(1, n);
			palette.add(new int[] {(int) (r / n), (int) (g / n), (int) (b / n)});
		}
		return palette;
	}

	private static int nearestIndex(List<int[]> palette, int rgb) {
		return nearestIndex(palette, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
	}

	private static int nearestIndex(List<int[]> palette, int r, int g, int b) {
		int best = 0;
		long bestDist = Long.MAX_VALUE;
		for (int i = 0; i < palette.size(); i++) {
			int[] c = palette.get(i);
			long dr = r - c[0];
			long dg = g - c[1];
			long db = b - c[2];
			long dist = dr * dr + dg * dg + db * db;
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}
}
