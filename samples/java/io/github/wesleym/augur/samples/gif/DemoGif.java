package io.github.wesleym.augur.samples.gif;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.Completion;
import io.github.wesleym.augur.context.CaretClassifier;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionEdit;
import io.github.wesleym.augur.lex.LexResult;
import io.github.wesleym.augur.lex.QuietSpan;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;
import io.github.wesleym.augur.samples.DemoCatalog;
import io.github.wesleym.augur.samples.swing.KindStyle;
import io.github.wesleym.augur.samples.swing.Theme;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the real Augur completion flow to compact, optimized animated GIFs,
 * headlessly. Each clip tells the story of one <em>operation</em>: a short
 * trigger is typed, the engine offers a schema-derived candidate, and accepting
 * it rewrites the statement — the result frame names the operation that ran.
 *
 * <p>Every frame is driven by an actual {@code augur.complete(text, caret)}
 * call: syntax colors come from {@link SqlLexer}, candidates and ranking from
 * the engine, and the applied edit from {@link InsertionEdit}.
 */
public final class DemoGif {
	/** prefix is present from the start; typed is animated; tail follows the caret. */
	private record Scenario(String name, String title, String detail,
			String prefix, String typed, String tail) { }

	private static final List<Scenario> SCENARIOS = List.of(
			new Scenario("join", "Foreign-key join", "battle.legionary_id → legionary.id",
					"select * from battle b join ", "leg", ""),
			new Scenario("join-path", "Two-hop join path", "bridged through the oath junction",
					"select * from legionary l join ", "gen", ""),
			new Scenario("on", "Join predicate", "recovered from the foreign key",
					"select * from battle b join legionary l ", "on ", ""),
			new Scenario("group-by", "GROUP BY backfill", "the non-aggregated select columns",
					"select l.name, count(*) from legionary l group ", "by ", ""),
			new Scenario("insert", "INSERT scaffold", "columns paired with value placeholders",
					"insert into legionary", " ", ""),
			new Scenario("value", "Value from profile", "ranked by observed frequency",
					"select * from battle b where outcome ", "= ", ""));

	private static final int W = 560;
	private static final int H = 232;
	private static final int PAD = 22;
	private static final int CARD_Y = 18;
	private static final int CARD_H = 48;
	private static final int TEXT_X = PAD + 16;
	private static final int ZONE_Y = CARD_Y + CARD_H + 10;
	private static final int POPUP_W = W - 2 * PAD;
	private static final int ROW_H = 38;
	private static final int CHIP = 22;
	private static final int MAX_ROWS = 3;
	private static final int EDITOR_FONT = 17;

	private static final int TYPE_MS = 72;
	private static final int PREFIX_HOLD_MS = 480;
	private static final int TYPED_HOLD_MS = 1050;
	private static final int ACCEPT_HOLD_MS = 2000;

	// A 32-color shared palette keeps the flat UI crisp while cutting file size ~40%.
	// Dithering is available but off by default: with no gradients to band it only adds
	// LZW-hostile noise and enlarges the GIFs. Override with -Daugur.gif.colors / .dither.
	private static final int MAX_COLORS = Integer.getInteger("augur.gif.colors", 32);
	private static final boolean DITHER = Boolean.parseBoolean(System.getProperty("augur.gif.dither", "false"));

	private final Augur augur = DemoCatalog.augur();

	private DemoGif() {
		// Warm the JIT so the status bar reports steady-state latency, not the
		// cold first-call cost that dwarfs the actual microsecond compute time.
		for (int i = 0; i < 20_000; i++) {
			for (Scenario scenario : SCENARIOS) {
				String full = scenario.prefix() + scenario.typed() + scenario.tail();
				augur.complete(full, scenario.prefix().length() + scenario.typed().length());
			}
		}
	}

	public static void main(String[] args) throws IOException {
		// Rasterize with the JDK's headless font engine so output is identical
		// everywhere (a display-backed JVM would antialias text differently).
		System.setProperty("java.awt.headless", "true");
		File dir = new File(args.length > 0 ? args[0] : "docs/media");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("Cannot create output directory: " + dir);
		}
		DemoGif app = new DemoGif();
		for (Scenario scenario : SCENARIOS) {
			app.render(scenario, dir);
		}
		System.out.println("Done. Wrote " + SCENARIOS.size() + " GIFs to " + dir.getPath());
	}

	private void render(Scenario scenario, File dir) throws IOException {
		String head = scenario.prefix() + scenario.typed();
		String full = head + scenario.tail();
		int caret = head.length();
		File gif = new File(dir, scenario.name() + ".gif");

		State poster = state(scenario, full, caret, Mode.POPUP);
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();

		String startText = scenario.prefix() + scenario.tail();
		add(frames, delays, paint(state(scenario, startText, scenario.prefix().length(), Mode.POPUP)), PREFIX_HOLD_MS);
		for (int i = 1; i <= scenario.typed().length(); i++) {
			String typedText = scenario.prefix() + scenario.typed().substring(0, i) + scenario.tail();
			add(frames, delays, paint(state(scenario, typedText, scenario.prefix().length() + i, Mode.POPUP)), TYPE_MS);
		}
		add(frames, delays, paint(poster), TYPED_HOLD_MS);

		Completion completion = augur.complete(full, caret);
		if (completion.first().isPresent()) {
			Candidate top = completion.first().get();
			InsertionEdit edit = completion.edit(top);
			String result = edit.applyTo(full);
			State done = state(scenario, result, edit.absoluteCaret(), Mode.RESULT);
			done.highlight = new int[] {edit.replaceSpan().start(), edit.insertText().length()};
			add(frames, delays, paint(done), ACCEPT_HOLD_MS);
		}

		List<BufferedImage> indexed = Quantizer.quantize(frames, MAX_COLORS, DITHER);
		try (GifSequenceWriter writer = new GifSequenceWriter(gif)) {
			for (int i = 0; i < indexed.size(); i++) {
				writer.writeFrame(indexed.get(i), delays.get(i));
			}
		}

		ImageIO.write(paint(poster), "png", new File(dir, scenario.name() + ".png"));
		System.out.printf("  %-11s %-40s -> %s (%.0f µs)%n", scenario.name(),
				poster.candidates.stream().findFirst().map(Candidate::insertText).orElse("(none)"),
				gif.getName(), poster.micros);
	}

	private static void add(List<BufferedImage> frames, List<Integer> delays, BufferedImage frame, int delayMs) {
		frames.add(frame);
		delays.add(delayMs);
	}

	// --- state ------------------------------------------------------------

	private enum Mode { POPUP, RESULT }

	private static final class State {
		final Scenario scenario;
		final String text;
		final int caret;
		final Mode mode;
		int[] highlight;
		List<Candidate> candidates;
		Context context;
		double micros;

		State(Scenario scenario, String text, int caret, Mode mode) {
			this.scenario = scenario;
			this.text = text;
			this.caret = caret;
			this.mode = mode;
		}
	}

	private State state(Scenario scenario, String text, int caret, Mode mode) {
		State s = new State(scenario, text, caret, mode);
		Completion completion = augur.complete(text, caret);
		double best = Double.MAX_VALUE;
		for (int i = 0; i < 50; i++) {
			long t0 = System.nanoTime();
			augur.complete(text, caret);
			best = Math.min(best, (System.nanoTime() - t0) / 1000.0);
		}
		s.micros = best;
		s.candidates = completion.candidates();
		s.context = CaretClassifier.classify(text, caret, DemoCatalog.DIALECT);
		return s;
	}

	// --- painting ---------------------------------------------------------

	private BufferedImage paint(State s) {
		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		g.setColor(Theme.BG);
		g.fillRect(0, 0, W, H);

		shadow(g, PAD, CARD_Y, W - 2 * PAD, CARD_H, 14);
		roundFill(g, PAD, CARD_Y, W - 2 * PAD, CARD_H, 14, Theme.PANEL);
		roundStroke(g, PAD, CARD_Y, W - 2 * PAD, CARD_H, 14, Theme.BORDER, 1f);

		paintEditor(g, s);
		if (s.mode == Mode.RESULT) {
			paintBanner(g, s);
		} else if (!s.candidates.isEmpty()) {
			paintPopup(g, s);
		}
		paintStatus(g, s);

		g.dispose();
		return image;
	}

	private void paintEditor(Graphics2D g, State s) {
		Font font = Theme.mono(EDITOR_FONT);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int baseline = CARD_Y + (CARD_H + fm.getAscent() - fm.getDescent()) / 2;
		int avail = (W - PAD - 16) - TEXT_X;
		int caretPixel = advance(fm, s.text, 0, s.caret);
		int scroll = Math.max(0, caretPixel - (avail - 12));

		Shape clip = g.getClip();
		g.setClip(new Rectangle(TEXT_X - 4, CARD_Y, avail + 12, CARD_H));
		Color[] colors = colorize(s.text);

		if (s.highlight != null) {
			int hx = TEXT_X + advance(fm, s.text, 0, s.highlight[0]) - scroll;
			int hw = advance(fm, s.text, s.highlight[0], s.highlight[0] + s.highlight[1]);
			roundFill(g, hx - 3, baseline - fm.getAscent() + 1, hw + 6, fm.getAscent() + fm.getDescent(),
					6, Theme.mix(Theme.PANEL, Theme.AMBER, 0.24));
		}

		int x = TEXT_X - scroll;
		for (int i = 0; i < s.text.length(); i++) {
			g.setColor(colors[i]);
			g.drawString(String.valueOf(s.text.charAt(i)), x, baseline);
			x += fm.charWidth(s.text.charAt(i));
		}

		int caretX = TEXT_X + caretPixel - scroll;
		g.setColor(Theme.CARET);
		g.fillRect(caretX, baseline - fm.getAscent(), 2, fm.getAscent() + fm.getDescent());
		g.setClip(clip);
	}

	private void paintPopup(Graphics2D g, State s) {
		int rows = Math.min(MAX_ROWS, s.candidates.size());
		int popupH = rows * ROW_H + 2;
		int x = PAD;

		shadow(g, x, ZONE_Y, POPUP_W, popupH, 12);
		roundFill(g, x, ZONE_Y, POPUP_W, popupH, 12, Theme.PANEL);
		roundStroke(g, x, ZONE_Y, POPUP_W, popupH, 12, Theme.BORDER, 1f);
		for (int i = 0; i < rows; i++) {
			paintRow(g, s.candidates.get(i), x, ZONE_Y + 1 + i * ROW_H, i == 0);
		}
	}

	private void paintRow(Graphics2D g, Candidate candidate, int x, int y, boolean selected) {
		KindStyle style = KindStyle.of(candidate.kind());
		Color kind = style.color();
		if (selected) {
			g.setColor(Theme.mix(Theme.PANEL, kind, 0.13));
			g.fillRect(x + 1, y, POPUP_W - 2, ROW_H);
			g.setColor(kind);
			g.fillRect(x + 1, y, 3, ROW_H);
		}

		int chipY = y + (ROW_H - CHIP) / 2;
		roundFill(g, x + 12, chipY, CHIP, CHIP, 7, Theme.mix(Theme.PANEL, kind, selected ? 0.30 : 0.18));
		g.setColor(kind);
		g.setFont(Theme.mono(14, Font.BOLD));
		center(g, style.glyph(), x + 12, chipY, CHIP, CHIP);

		int textX = x + 12 + CHIP + 12;

		g.setFont(Theme.ui(10, Font.BOLD));
		FontMetrics tagFm = g.getFontMetrics();
		int tagX = x + POPUP_W - tagFm.stringWidth(style.label()) - 14;
		g.setColor(selected ? kind : Theme.TEXT_FAINT);
		g.drawString(style.label(), tagX, y + ROW_H / 2 + 4);

		String label = candidate.display().isEmpty() ? candidate.insertText() : candidate.display();
		g.setFont(Theme.mono(14, Font.BOLD));
		FontMetrics labelFm = g.getFontMetrics();
		Set<Integer> matched = new HashSet<>();
		for (int m : candidate.matchedChars()) {
			matched.add(m);
		}
		int cx = textX;
		int labelMax = tagX - 12;
		boolean detail = !candidate.detail().isEmpty();
		int labelBaseline = detail ? y + 17 : y + ROW_H / 2 + 5;
		for (int i = 0; i < label.length() && cx < labelMax; i++) {
			g.setColor(matched.contains(i) ? Theme.MATCH : Theme.TEXT);
			g.drawString(String.valueOf(label.charAt(i)), cx, labelBaseline);
			cx += labelFm.charWidth(label.charAt(i));
		}
		if (cx >= labelMax) {
			g.setColor(Theme.TEXT_MUTED);
			g.drawString("…", labelMax, labelBaseline);
		}

		if (detail) {
			g.setFont(Theme.ui(11));
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(ellipsize(g.getFontMetrics(), candidate.detail(), POPUP_W - (textX - x) - 18),
					textX, y + 32);
		}
	}

	private void paintBanner(Graphics2D g, State s) {
		int h = 52;
		int x = PAD;
		shadow(g, x, ZONE_Y, POPUP_W, h, 12);
		roundFill(g, x, ZONE_Y, POPUP_W, h, 12, Theme.mix(Theme.PANEL, Theme.RED, 0.07));
		roundStroke(g, x, ZONE_Y, POPUP_W, h, 12, Theme.mix(Theme.BORDER, Theme.RED, 0.30), 1f);

		// A wax-seal stamp: the completion has been "sealed" into the statement.
		int cy = ZONE_Y + h / 2;
		int cxc = x + 27;
		g.setColor(Theme.RED);
		g.fill(new java.awt.geom.Ellipse2D.Float(cxc - 12, cy - 12, 24, 24));
		g.setColor(Theme.mix(Theme.RED, Color.BLACK, 0.18));
		g.setStroke(new BasicStroke(1f));
		g.draw(new java.awt.geom.Ellipse2D.Float(cxc - 8.5f, cy - 8.5f, 17, 17));
		g.setColor(Theme.mix(Theme.PANEL, Theme.RED, 0.12));
		g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawPolyline(new int[] {cxc - 5, cxc - 1, cxc + 6}, new int[] {cy, cy + 4, cy - 5}, 3);

		int textX = x + 52;
		g.setFont(Theme.serif(15, Font.BOLD));
		g.setColor(Theme.TEXT);
		g.drawString(s.scenario.title(), textX, cy - 2);
		g.setFont(Theme.ui(12));
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(s.scenario.detail(), textX, cy + 15);
	}

	private void paintStatus(Graphics2D g, State s) {
		int y = H - 13;
		g.setColor(Theme.BORDER);
		g.fillRect(PAD, H - 32, W - 2 * PAD, 1);

		g.setFont(Theme.serif(15, Font.BOLD));
		g.setColor(Theme.TEXT);
		int wx = PAD;
		FontMetrics wm = g.getFontMetrics();
		for (char c : "AUGUR".toCharArray()) {
			g.drawString(String.valueOf(c), wx, y);
			wx += wm.charWidth(c) + 2;
		}

		String status = String.format("%s   ·   %d candidate%s   ·   %.0f µs",
				s.context.getClass().getSimpleName(), s.candidates.size(),
				s.candidates.size() == 1 ? "" : "s", s.micros);
		g.setFont(Theme.mono(11));
		FontMetrics fm = g.getFontMetrics();
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(status, W - PAD - fm.stringWidth(status), y);
	}

	// --- helpers ----------------------------------------------------------

	private Color[] colorize(String text) {
		Color[] colors = new Color[text.length()];
		java.util.Arrays.fill(colors, Theme.TEXT);
		LexResult result = SqlLexer.scan(text, DemoCatalog.DIALECT);
		Set<Integer> stringStarts = new HashSet<>();
		for (Token token : result.tokens()) {
			if (token.kind() == TokenKind.STRING) {
				stringStarts.add(token.start());
			}
			paintRange(colors, token.start(), token.end(), tokenColor(token.kind()));
		}
		for (QuietSpan span : result.quietSpans()) {
			if (!stringStarts.contains(span.start())) {
				paintRange(colors, span.start(), span.end(), Theme.SYN_COMMENT);
			}
		}
		return colors;
	}

	private static void paintRange(Color[] colors, int start, int end, Color color) {
		for (int i = Math.max(0, start); i < Math.min(colors.length, end); i++) {
			colors[i] = color;
		}
	}

	private static Color tokenColor(TokenKind kind) {
		return switch (kind) {
			case KEYWORD -> Theme.SYN_KEYWORD;
			case STRING -> Theme.SYN_STRING;
			case NUMBER -> Theme.SYN_NUMBER;
			case QUOTED_IDENTIFIER -> Theme.SYN_QUOTED;
			case OPERATOR, SYMBOL -> Theme.SYN_PUNCT;
			case IDENTIFIER -> Theme.TEXT;
		};
	}

	private static int advance(FontMetrics fm, String text, int from, int to) {
		int w = 0;
		for (int i = from; i < to && i < text.length(); i++) {
			w += fm.charWidth(text.charAt(i));
		}
		return w;
	}

	private static void center(Graphics2D g, String text, int x, int y, int w, int h) {
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
	}

	private static String ellipsize(FontMetrics fm, String text, int maxWidth) {
		if (fm.stringWidth(text) <= maxWidth) {
			return text;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (fm.stringWidth(out.toString() + text.charAt(i) + "…") > maxWidth) {
				break;
			}
			out.append(text.charAt(i));
		}
		return out + "…";
	}

	private static void shadow(Graphics2D g, int x, int y, int w, int h, int arc) {
		for (int i = 3; i >= 1; i--) {
			g.setColor(new Color(15, 23, 42, 9));
			g.fill(new RoundRectangle2D.Float(x - i, y + i, w + 2f * i, h + 2f * i, arc + i, arc + i));
		}
	}

	private static void roundFill(Graphics2D g, int x, int y, int w, int h, int arc, Color color) {
		g.setColor(color);
		g.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
	}

	private static void roundStroke(Graphics2D g, int x, int y, int w, int h, int arc, Color color, float width) {
		g.setColor(color);
		g.setStroke(new BasicStroke(width));
		g.draw(new RoundRectangle2D.Float(x, y, w - 1f, h - 1f, arc, arc));
	}
}
