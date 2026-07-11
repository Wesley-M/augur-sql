package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.Candidate;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Paints one completion candidate: a kind chip, match-highlighted label, detail, and kind tag. */
final class CandidateCellRenderer extends JComponent implements ListCellRenderer<Candidate> {
	private static final int HEIGHT = 46;
	private static final int CHIP = 26;

	private transient Candidate candidate;
	private boolean selected;

	@Override
	public Component getListCellRendererComponent(JList<? extends Candidate> list, Candidate value, int index,
			boolean isSelected, boolean cellHasFocus) {
		this.candidate = value;
		this.selected = isSelected;
		return this;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(360, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int width = getWidth();
		int height = getHeight();
		if (candidate == null) {
			g.dispose();
			return;
		}

		KindStyle style = KindStyle.of(candidate.kind());
		if (selected) {
			g.setColor(Theme.mix(Theme.PANEL, style.color(), 0.16));
			g.fillRect(0, 0, width, height);
			g.setColor(style.color());
			g.fillRect(0, 0, 3, height);
		}

		// Kind chip.
		int chipY = (height - CHIP) / 2;
		g.setColor(Theme.mix(Theme.PANEL, style.color(), selected ? 0.34 : 0.22));
		g.fillRoundRect(12, chipY, CHIP, CHIP, 8, 8);
		g.setColor(style.color());
		g.setFont(Theme.mono(14, Font.BOLD));
		drawCentered(g, style.glyph(), 12, chipY, CHIP, CHIP);

		int textX = 12 + CHIP + 12;
		int labelBaseline = 20;
		int detailBaseline = 36;

		// Kind tag, right aligned on the label line.
		g.setFont(Theme.ui(10, Font.BOLD));
		FontMetrics tagMetrics = g.getFontMetrics();
		int tagWidth = tagMetrics.stringWidth(style.label());
		int tagX = width - tagWidth - 14;
		g.setColor(selected ? style.color() : Theme.TEXT_FAINT);
		g.drawString(style.label(), tagX, labelBaseline);

		// Display label with match highlights.
		String label = candidate.display().isEmpty() ? candidate.insertText() : candidate.display();
		g.setFont(Theme.mono(14, Font.BOLD));
		FontMetrics labelMetrics = g.getFontMetrics();
		drawHighlighted(g, label, candidate.matchedChars(), textX, labelBaseline, tagX - 10,
				selected ? Theme.TEXT : Theme.mix(Theme.TEXT, Theme.PANEL, 0.06), Theme.MATCH, labelMetrics);

		// Detail line.
		if (!candidate.detail().isEmpty()) {
			g.setFont(Theme.ui(11));
			g.setColor(Theme.TEXT_MUTED);
			FontMetrics detailMetrics = g.getFontMetrics();
			g.drawString(ellipsize(candidate.detail(), detailMetrics, width - textX - 20), textX, detailBaseline);
		}

		g.dispose();
	}

	private static void drawHighlighted(Graphics2D g, String text, int[] matched, int x, int baseline, int maxX,
			Color normal, Color highlight, FontMetrics metrics) {
		int cursor = x;
		int matchIndex = 0;
		for (int i = 0; i < text.length(); i++) {
			int charWidth = metrics.charWidth(text.charAt(i));
			if (cursor + charWidth > maxX) {
				g.setColor(Theme.TEXT_MUTED);
				g.drawString("…", cursor, baseline);
				break;
			}
			boolean isMatch = matchIndex < matched.length && matched[matchIndex] == i;
			if (isMatch) {
				matchIndex++;
			}
			g.setColor(isMatch ? highlight : normal);
			g.drawString(String.valueOf(text.charAt(i)), cursor, baseline);
			cursor += charWidth;
		}
	}

	private static void drawCentered(Graphics2D g, String text, int x, int y, int w, int h) {
		FontMetrics metrics = g.getFontMetrics();
		int tx = x + (w - metrics.stringWidth(text)) / 2;
		int ty = y + (h - metrics.getHeight()) / 2 + metrics.getAscent();
		g.drawString(text, tx, ty);
	}

	private static String ellipsize(String text, FontMetrics metrics, int maxWidth) {
		if (maxWidth <= 0 || metrics.stringWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (metrics.stringWidth(out.toString() + text.charAt(i) + ellipsis) > maxWidth) {
				break;
			}
			out.append(text.charAt(i));
		}
		return out + ellipsis;
	}
}
