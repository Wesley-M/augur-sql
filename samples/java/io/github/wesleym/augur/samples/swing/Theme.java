package io.github.wesleym.augur.samples.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Set;

/**
 * Central palette, fonts, and geometry for the Swing demo so the whole app
 * reads as one warm, premium theme consistent with the augural-seal logo:
 * ink on parchment, with a single rubric accent (the red scribes used to
 * emphasize text). Kept dependency-free: colors and fonts only.
 */
public final class Theme {
	private Theme() { }

	// Surfaces (warm parchment and vellum).
	public static final Color BG = new Color(0xE7DCC4);
	public static final Color PANEL = new Color(0xFBF7EC);
	public static final Color RAISED = new Color(0xF2EADA);
	public static final Color RAISED_HOVER = new Color(0xEADFC9);
	public static final Color BORDER = new Color(0xD7CBB0);
	public static final Color BORDER_SOFT = new Color(0xE6DBC5);

	// Text (warm ink).
	public static final Color TEXT = new Color(0x2A241C);
	public static final Color TEXT_MUTED = new Color(0x6E6353);
	public static final Color TEXT_FAINT = new Color(0xA3967D);

	// Accents (muted, earthy — a hand-tinted-manuscript palette).
	public static final Color ACCENT = new Color(0x415A6E);
	public static final Color GREEN = new Color(0x5C6B39);
	public static final Color PURPLE = new Color(0x6E4C6B);
	public static final Color AMBER = new Color(0x8A6A2E);
	public static final Color PINK = new Color(0xA0503C);
	public static final Color RED = new Color(0x9C3B2E);
	public static final Color CYAN = new Color(0x3C6B62);

	// Selection / highlight (rubric).
	public static final Color SELECTION = new Color(0xE6D6B4);
	public static final Color CARET = new Color(0x9C3B2E);
	public static final Color MATCH = new Color(0x9C3B2E);

	// Syntax (rubric keywords, earthy literals — ink identifiers).
	public static final Color SYN_KEYWORD = new Color(0x9C3B2E);
	public static final Color SYN_STRING = new Color(0x5C6B39);
	public static final Color SYN_NUMBER = new Color(0x8A6A2E);
	public static final Color SYN_COMMENT = new Color(0xA3967D);
	public static final Color SYN_QUOTED = new Color(0x3C6B62);
	public static final Color SYN_PUNCT = new Color(0x8A7C64);

	private static final String MONO = pick(Set.of("JetBrains Mono", "SF Mono", "Menlo",
			"DejaVu Sans Mono", "Consolas"), Font.MONOSPACED);
	private static final String SANS = pick(Set.of("Inter", "SF Pro Text", "Helvetica Neue",
			"Segoe UI", "DejaVu Sans"), Font.SANS_SERIF);
	private static final String SERIF = pick(Set.of("Iowan Old Style", "Palatino Linotype",
			"Palatino", "Georgia", "Times New Roman"), Font.SERIF);

	public static Font mono(int size) {
		return new Font(MONO, Font.PLAIN, size);
	}

	public static Font mono(int size, int style) {
		return new Font(MONO, style, size);
	}

	public static Font ui(int size) {
		return new Font(SANS, Font.PLAIN, size);
	}

	public static Font ui(int size, int style) {
		return new Font(SANS, style, size);
	}

	public static Font serif(int size) {
		return new Font(SERIF, Font.PLAIN, size);
	}

	public static Font serif(int size, int style) {
		return new Font(SERIF, style, size);
	}

	private static String pick(Set<String> preferred, String fallback) {
		try {
			for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
				if (preferred.contains(name)) {
					return name;
				}
			}
		} catch (RuntimeException ignored) {
			// Headless or restricted environments fall back to the logical font.
		}
		return fallback;
	}

	/** Blends {@code base} toward {@code over} by {@code ratio} in [0,1]. */
	public static Color mix(Color base, Color over, double ratio) {
		double r = Math.max(0.0, Math.min(1.0, ratio));
		int red = (int) Math.round(base.getRed() + (over.getRed() - base.getRed()) * r);
		int green = (int) Math.round(base.getGreen() + (over.getGreen() - base.getGreen()) * r);
		int blue = (int) Math.round(base.getBlue() + (over.getBlue() - base.getBlue()) * r);
		return new Color(red, green, blue);
	}
}
