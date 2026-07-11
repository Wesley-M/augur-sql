package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.ValueShare;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Renders the structured {@link CandidateDoc} for the selected candidate. */
final class DocPanel extends JPanel {
	private static final int PAD = 20;

	private transient Candidate candidate;

	DocPanel() {
		setBackground(Theme.PANEL);
	}

	void setCandidate(Candidate candidate) {
		this.candidate = candidate;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int width = getWidth();

		if (candidate == null) {
			g.setColor(Theme.TEXT_FAINT);
			g.setFont(Theme.ui(13));
			g.drawString("Select a candidate to inspect it.", PAD, 40);
			g.dispose();
			return;
		}

		KindStyle style = KindStyle.of(candidate.kind());
		CandidateDoc doc = candidate.doc();
		int y = PAD + 6;

		// Header: kind tag + title.
		g.setFont(Theme.ui(10, Font.BOLD));
		g.setColor(style.color());
		g.drawString(style.label().toUpperCase(java.util.Locale.ROOT), PAD, y);
		y += 22;

		String title = candidate.display().isEmpty() ? candidate.insertText() : candidate.display();
		g.setFont(Theme.mono(17, Font.BOLD));
		g.setColor(Theme.TEXT);
		y += drawWrapped(g, title, PAD, y, width - 2 * PAD, 22);

		if (!doc.type().isEmpty()) {
			y += 4;
			g.setFont(Theme.ui(12));
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(doc.type(), PAD, y);
			y += 18;
		}

		// Inserted-text preview.
		y += 12;
		y = drawInsertPreview(g, width, y);

		// Badges.
		if (!doc.badges().isEmpty()) {
			y += 18;
			y = drawBadges(g, doc, style, y);
		}

		// Reference.
		if (!doc.referencedTable().isEmpty()) {
			y += 20;
			g.setFont(Theme.ui(12));
			g.setColor(Theme.TEXT_MUTED);
			g.drawString("references", PAD, y);
			g.setColor(Theme.ACCENT);
			g.setFont(Theme.mono(12, Font.BOLD));
			g.drawString(doc.referencedTable(), PAD + 76, y);
			y += 6;
		}

		// Stats tiles.
		if (doc.nullFraction() != null || doc.distinctCount() != null) {
			y += 18;
			y = drawStats(g, doc, y);
		}

		// Top values.
		if (!doc.topValues().isEmpty()) {
			y += 22;
			drawValues(g, doc, width, y);
		}

		g.dispose();
	}

	private int drawInsertPreview(Graphics2D g, int width, int y) {
		g.setFont(Theme.ui(10, Font.BOLD));
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("INSERTS", PAD, y);
		y += 12;
		int boxHeight = 30;
		g.setColor(Theme.BG);
		g.fillRoundRect(PAD, y, width - 2 * PAD, boxHeight, 8, 8);
		g.setColor(Theme.BORDER);
		g.drawRoundRect(PAD, y, width - 2 * PAD, boxHeight, 8, 8);
		g.setFont(Theme.mono(12));
		g.setColor(Theme.SYN_STRING);
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(clip(candidate.insertText(), metrics, width - 2 * PAD - 24),
				PAD + 12, y + boxHeight / 2 + metrics.getAscent() / 2 - 1);
		return y + boxHeight;
	}

	private int drawBadges(Graphics2D g, CandidateDoc doc, KindStyle style, int y) {
		g.setFont(Theme.ui(11, Font.BOLD));
		FontMetrics metrics = g.getFontMetrics();
		int x = PAD;
		for (String badge : doc.badges()) {
			int textWidth = metrics.stringWidth(badge);
			int pillWidth = textWidth + 20;
			if (x + pillWidth > getWidth() - PAD) {
				x = PAD;
				y += 28;
			}
			Color color = badgeColor(badge, style);
			g.setColor(Theme.mix(Theme.PANEL, color, 0.20));
			g.fillRoundRect(x, y - 14, pillWidth, 22, 11, 11);
			g.setColor(color);
			g.drawString(badge, x + 10, y + 2);
			x += pillWidth + 8;
		}
		return y + 8;
	}

	private int drawStats(Graphics2D g, CandidateDoc doc, int y) {
		int tileWidth = (getWidth() - 2 * PAD - 12) / 2;
		int tileHeight = 50;
		int x = PAD;
		if (doc.nullFraction() != null) {
			drawTile(g, x, y, tileWidth, tileHeight, "null fraction", percent(doc.nullFraction()));
			x += tileWidth + 12;
		}
		if (doc.distinctCount() != null) {
			drawTile(g, x, y, tileWidth, tileHeight, "distinct values", String.valueOf(doc.distinctCount()));
		}
		return y + tileHeight;
	}

	private void drawTile(Graphics2D g, int x, int y, int w, int h, String label, String value) {
		g.setColor(Theme.BG);
		g.fillRoundRect(x, y, w, h, 8, 8);
		g.setColor(Theme.BORDER);
		g.drawRoundRect(x, y, w, h, 8, 8);
		g.setFont(Theme.mono(19, Font.BOLD));
		g.setColor(Theme.TEXT);
		g.drawString(value, x + 12, y + 26);
		g.setFont(Theme.ui(10));
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(label, x + 12, y + 42);
	}

	private void drawValues(Graphics2D g, CandidateDoc doc, int width, int y) {
		g.setFont(Theme.ui(10, Font.BOLD));
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("TOP VALUES", PAD, y);
		y += 18;
		int barX = PAD + 118;
		int barWidth = width - PAD - barX - 52;
		for (ValueShare value : doc.topValues()) {
			g.setFont(Theme.mono(12));
			g.setColor(Theme.TEXT);
			g.drawString(clip(value.value(), g.getFontMetrics(), 108), PAD, y + 4);

			g.setColor(Theme.BG);
			g.fillRoundRect(barX, y - 8, barWidth, 12, 6, 6);
			int filled = (int) Math.round(barWidth * Math.max(0.0, Math.min(1.0, value.share())));
			g.setColor(Theme.ACCENT);
			g.fillRoundRect(barX, y - 8, Math.max(6, filled), 12, 6, 6);

			g.setFont(Theme.ui(11, Font.BOLD));
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(percent(value.share()), barX + barWidth + 10, y + 3);
			y += 26;
		}
	}

	private static Color badgeColor(String badge, KindStyle fallback) {
		String lower = badge.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("pk") || lower.contains("primary")) {
			return Theme.AMBER;
		}
		if (lower.contains("fk") || lower.contains("foreign") || lower.contains("reference")) {
			return Theme.PURPLE;
		}
		if (lower.contains("sensitive")) {
			return Theme.RED;
		}
		if (lower.contains("system")) {
			return Theme.CYAN;
		}
		return fallback.color();
	}

	private static int drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
		FontMetrics metrics = g.getFontMetrics();
		if (metrics.stringWidth(text) <= maxWidth) {
			g.drawString(text, x, y);
			return lineHeight;
		}
		StringBuilder line = new StringBuilder();
		int drawnY = y;
		for (String word : text.split(" ")) {
			String candidateLine = line.isEmpty() ? word : line + " " + word;
			if (metrics.stringWidth(candidateLine) > maxWidth && !line.isEmpty()) {
				g.drawString(line.toString(), x, drawnY);
				drawnY += lineHeight;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidateLine);
			}
		}
		g.drawString(line.toString(), x, drawnY);
		return drawnY - y + lineHeight;
	}

	private static String clip(String text, FontMetrics metrics, int maxWidth) {
		if (metrics.stringWidth(text) <= maxWidth) {
			return text;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (metrics.stringWidth(out.toString() + text.charAt(i) + "…") > maxWidth) {
				break;
			}
			out.append(text.charAt(i));
		}
		return out + "…";
	}

	private static String percent(double value) {
		return Math.round(value * 100.0) + "%";
	}
}
