package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** A read-only schema explorer for the demo catalog: tables, columns, types, and roles. */
final class SchemaPanel extends JPanel implements Scrollable {
	private static final int PAD = 16;
	private static final int ROW = 22;
	private static final int HEADER = 30;

	private final transient Catalog catalog;

	SchemaPanel(Catalog catalog) {
		this.catalog = catalog;
		setBackground(Theme.PANEL);
		setPreferredSize(new Dimension(250, contentHeight()));
	}

	private int contentHeight() {
		int height = PAD;
		for (Catalog.Table table : catalog.tables()) {
			height += HEADER + table.columns().size() * ROW + 8;
		}
		return height + PAD;
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int width = getWidth();
		int y = PAD;

		for (Catalog.Table table : catalog.tables()) {
			boolean view = table.view();
			Color accent = view ? Theme.CYAN : Theme.ACCENT;
			g.setColor(Theme.mix(Theme.PANEL, accent, 0.22));
			g.fillRoundRect(PAD - 6, y - 2, 20, 20, 6, 6);
			g.setColor(accent);
			g.setFont(Theme.mono(12, Font.BOLD));
			g.drawString(view ? "V" : "T", PAD, y + 13);

			g.setColor(Theme.TEXT);
			g.setFont(Theme.ui(13, Font.BOLD));
			g.drawString(table.name(), PAD + 22, y + 14);

			if (table.hasKnownRowCount()) {
				g.setFont(Theme.ui(10));
				g.setColor(Theme.TEXT_FAINT);
				String rows = compact(table.rowCount());
				g.drawString(rows, width - PAD - g.getFontMetrics().stringWidth(rows), y + 13);
			}
			y += HEADER;

			for (Catalog.Column column : table.columns()) {
				g.setFont(Theme.mono(12));
				g.setColor(Theme.TEXT_MUTED);
				g.drawString(column.name(), PAD + 22, y + 14);
				int nameWidth = g.getFontMetrics().stringWidth(column.name());

				g.setFont(Theme.ui(10));
				g.setColor(Theme.TEXT_FAINT);
				g.drawString(column.typeName(), PAD + 30 + nameWidth, y + 13);

				drawRoleDot(g, column, width, y);
				y += ROW;
			}
			y += 8;
		}
		g.dispose();
	}

	private void drawRoleDot(Graphics2D g, Catalog.Column column, int width, int y) {
		String tag = null;
		Color color = null;
		if (column.primaryKey()) {
			tag = "PK";
			color = Theme.AMBER;
		} else if (column.foreignKey()) {
			tag = "FK";
			color = Theme.PURPLE;
		} else if (column.role() == ColumnRole.SENSITIVE) {
			tag = "•";
			color = Theme.RED;
		} else if (column.role() == ColumnRole.SYSTEM) {
			tag = "•";
			color = Theme.CYAN;
		}
		if (tag == null) {
			return;
		}
		g.setFont(Theme.ui(9, Font.BOLD));
		g.setColor(color);
		int tagWidth = g.getFontMetrics().stringWidth(tag);
		g.drawString(tag, width - PAD - tagWidth, y + 12);
	}

	private static String compact(long count) {
		if (count >= 1_000_000) {
			return Math.round(count / 100_000.0) / 10.0 + "M";
		}
		if (count >= 1_000) {
			return Math.round(count / 100.0) / 10.0 + "k";
		}
		return String.valueOf(count);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return ROW;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return visibleRect.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
