package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.Completion;
import io.github.wesleym.augur.context.CaretClassifier;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionEdit;
import io.github.wesleym.augur.samples.CaretText;
import io.github.wesleym.augur.samples.DemoCatalog;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * An interactive Swing playground for Augur: a syntax-highlighted SQL editor
 * with a live completion popup, a structured documentation pane, and a schema
 * explorer — all driven by the pure-Java engine in real time.
 */
public final class SwingDemo {
	private record Sample(String label, String sql) { }

	private static final List<Sample> SAMPLES = List.of(
			new Sample("Table", "select * from bat|"),
			new Sample("Column", "select l.| from legionary l"),
			new Sample("Join", "select * from battle b join leg|"),
			new Sample("Join path", "select * from legionary l join gen|"),
			new Sample("ON", "select * from battle b join legionary l on |"),
			new Sample("Value", "select * from battle b where outcome = |"),
			new Sample("Group by", "select l.name, count(*) from legionary l group by |"),
			new Sample("Insert", "insert into legionary |"));

	private final Augur augur = DemoCatalog.augur();
	private final JFrame frame = new JFrame("Augur SQL");
	private final SqlTextPane editor = new SqlTextPane(DemoCatalog.DIALECT);
	private final DocPanel docPanel = new DocPanel();
	private final CompletionPopup popup;
	private final StatusBar statusBar = new StatusBar();
	private final Timer debounce;

	private Completion completion;

	private SwingDemo() {
		this.popup = new CompletionPopup(frame, docPanel::setCandidate);
		this.debounce = new Timer(90, event -> complete(false));
		this.debounce.setRepeats(false);
		buildUi();
		installKeys();
		editor.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				schedule();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				schedule();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				// Attribute-only (highlighting) changes do not affect completion.
			}
		});
	}

	public static void main(String[] args) {
		if (args != null && List.of(args).contains("--smoke")) {
			Completion smoke = DemoCatalog.augur().complete("select * from battle b join leg");
			System.out.println(smoke.first().map(Candidate::insertText).orElse("(none)"));
			return;
		}
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("The Swing demo needs a graphical desktop. Try runDemoConsole instead.");
			return;
		}
		SwingUtilities.invokeLater(() -> new SwingDemo().show());
	}

	private void show() {
		frame.setVisible(true);
		editor.requestFocusInWindow();
		loadSample(SAMPLES.get(2));
	}

	private void buildUi() {
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setMinimumSize(new Dimension(1180, 720));
		frame.setLocationByPlatform(true);
		frame.getContentPane().setBackground(Theme.BG);
		frame.setLayout(new BorderLayout());

		frame.add(new Header(), BorderLayout.NORTH);
		frame.add(center(), BorderLayout.CENTER);
		frame.add(statusBar, BorderLayout.SOUTH);
		frame.pack();
	}

	private JComponent center() {
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.BG);
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel columns = new JPanel(new BorderLayout(12, 0));
		columns.setOpaque(false);
		columns.add(card(schemaColumn(), "SCHEMA", 250), BorderLayout.WEST);
		columns.add(editorColumn(), BorderLayout.CENTER);
		columns.add(card(docPanel, "CANDIDATE", 340), BorderLayout.EAST);
		root.add(columns, BorderLayout.CENTER);
		return root;
	}

	private JComponent schemaColumn() {
		JScrollPane scroll = new JScrollPane(new SchemaPanel(augur.catalog()),
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(Theme.PANEL);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private JComponent editorColumn() {
		JPanel column = new JPanel(new BorderLayout(0, 10));
		column.setOpaque(false);
		column.add(sampleBar(), BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(editor);
		scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
		scroll.getViewport().setBackground(Theme.PANEL);
		column.add(scroll, BorderLayout.CENTER);
		return column;
	}

	private JComponent sampleBar() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		bar.setOpaque(false);
		JLabel hint = new JLabel("try:");
		hint.setForeground(Theme.TEXT_FAINT);
		hint.setFont(Theme.ui(12));
		bar.add(hint);
		for (Sample sample : SAMPLES) {
			bar.add(new PillButton(sample.label(), () -> loadSample(sample)));
		}
		return bar;
	}

	private JComponent card(JComponent content, String title, int width) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(Theme.PANEL);
		card.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
		card.setPreferredSize(new Dimension(width, 10));

		JLabel header = new JLabel(title);
		header.setFont(Theme.ui(10, Font.BOLD));
		header.setForeground(Theme.TEXT_FAINT);
		header.setBorder(BorderFactory.createEmptyBorder(10, 16, 8, 16));
		card.add(header, BorderLayout.NORTH);
		card.add(content, BorderLayout.CENTER);
		return card;
	}

	// --- completion flow -------------------------------------------------

	private void schedule() {
		if (editor.hasFocus()) {
			debounce.restart();
		}
	}

	private void complete(boolean explicit) {
		String text = editor.getText();
		int caret = editor.getCaretPosition();
		long start = System.nanoTime();
		completion = augur.complete(text, caret);
		double micros = (System.nanoTime() - start) / 1000.0;

		Context context = CaretClassifier.classify(text, caret, DemoCatalog.DIALECT);
		List<Candidate> candidates = completion.candidates();
		statusBar.update(context, candidates.size(), micros);

		if (candidates.isEmpty()) {
			popup.hide();
			docPanel.setCandidate(null);
			return;
		}
		popup.setCandidates(candidates.size() > 120 ? candidates.subList(0, 120) : candidates);
		docPanel.setCandidate(popup.selected());
		if (explicit || editor.hasFocus()) {
			positionPopup();
		}
	}

	private void positionPopup() {
		try {
			Rectangle2D caretRect = editor.modelToView2D(editor.getCaretPosition());
			Point point = new Point((int) caretRect.getX(), (int) (caretRect.getY() + caretRect.getHeight()) + 4);
			SwingUtilities.convertPointToScreen(point, editor);
			popup.showAt(point);
		} catch (BadLocationException ex) {
			popup.hide();
		}
	}

	private void accept() {
		Candidate selected = popup.selected();
		if (selected == null || completion == null) {
			return;
		}
		InsertionEdit edit = completion.edit(selected);
		editor.setContent(edit.applyTo(editor.getText()), edit.absoluteCaret());
		popup.hide();
		editor.requestFocusInWindow();
	}

	private void loadSample(Sample sample) {
		CaretText parsed = CaretText.parse(sample.sql());
		editor.setContent(parsed.sql(), parsed.caret());
		editor.requestFocusInWindow();
		SwingUtilities.invokeLater(() -> complete(true));
	}

	// --- key bindings ----------------------------------------------------

	private void installKeys() {
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "trigger",
				e -> complete(true));
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismiss", e -> popup.hide());
		bindOrDefault(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down", DefaultEditorKit.downAction,
				() -> popup.move(1));
		bindOrDefault(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up", DefaultEditorKit.upAction,
				() -> popup.move(-1));
		// Enter accepts only after the popup has been navigated with the arrow keys;
		// a fresh auto-popup yields to a plain newline so typing multi-line SQL works.
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "accept", event -> {
			if (popup.isShowing() && popup.size() > 0 && popup.navigated()) {
				accept();
			} else {
				runDefault(DefaultEditorKit.insertBreakAction);
			}
		});
		bindOrDefault(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "acceptTab", DefaultEditorKit.insertTabAction,
				this::accept);
	}

	private void bind(KeyStroke stroke, String name, java.util.function.Consumer<ActionEvent> action) {
		editor.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
		editor.getActionMap().put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				action.accept(event);
			}
		});
	}

	private void bindOrDefault(KeyStroke stroke, String name, String fallbackAction, Runnable whenOpen) {
		bind(stroke, name, event -> {
			if (popup.isShowing() && popup.size() > 0) {
				whenOpen.run();
			} else {
				runDefault(fallbackAction);
			}
		});
	}

	private void runDefault(String actionName) {
		javax.swing.Action fallback = editor.getActionMap().get(actionName);
		if (fallback != null) {
			fallback.actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, actionName));
		}
	}

	// --- header + status widgets ----------------------------------------

	private static final class Header extends JPanel {
		private Header() {
			setBackground(Theme.RAISED);
			setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
			setLayout(new BorderLayout());

			JPanel titles = new JPanel();
			titles.setOpaque(false);
			titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
			JLabel title = new JLabel("Augur SQL");
			title.setFont(Theme.ui(18, Font.BOLD));
			title.setForeground(Theme.TEXT);
			JLabel subtitle = new JLabel("Deterministic, schema-aware SQL completion — live");
			subtitle.setFont(Theme.ui(12));
			subtitle.setForeground(Theme.TEXT_MUTED);
			titles.add(title);
			titles.add(Box.createVerticalStrut(2));
			titles.add(subtitle);
			add(titles, BorderLayout.WEST);

			JLabel dialect = new Pill(DemoCatalog.DIALECT.name(), Theme.ACCENT);
			JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			right.setOpaque(false);
			right.add(dialect);
			add(right, BorderLayout.EAST);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(Theme.BORDER);
			g.fillRect(0, getHeight() - 1, getWidth(), 1);
		}
	}

	private static final class Pill extends JLabel {
		private final Color accent;

		private Pill(String text, Color accent) {
			super(text);
			this.accent = accent;
			setFont(Theme.mono(12, Font.BOLD));
			setForeground(accent);
			setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Theme.mix(Theme.RAISED, accent, 0.18));
			g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class PillButton extends JComponent {
		private boolean hover;

		private PillButton(String text, Runnable action) {
			setFont(Theme.ui(12, Font.BOLD));
			Dimension size = new Dimension(getFontMetrics(getFont()).stringWidth(text) + 26, 28);
			setPreferredSize(size);
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseEntered(java.awt.event.MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e) {
					hover = false;
					repaint();
				}

				@Override
				public void mouseClicked(java.awt.event.MouseEvent e) {
					action.run();
				}
			});
			putClientProperty("text", text);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(hover ? Theme.RAISED_HOVER : Theme.RAISED);
			g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
			g.setColor(hover ? Theme.ACCENT : Theme.BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
			String text = String.valueOf(getClientProperty("text"));
			g.setFont(getFont());
			g.setColor(hover ? Theme.TEXT : Theme.TEXT_MUTED);
			int tx = (getWidth() - g.getFontMetrics().stringWidth(text)) / 2;
			int ty = (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
			g.drawString(text, tx, ty);
			g.dispose();
		}
	}

	private static final class StatusBar extends JPanel {
		private final JLabel left = new JLabel(" ");
		private final JLabel right = new JLabel(
				"Ctrl+Space complete   ·   ↑ ↓ navigate   ·   Tab accept   ·   Enter accept after ↑ ↓, else newline   ·   Esc dismiss");

		private StatusBar() {
			setBackground(Theme.RAISED);
			setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
			setLayout(new BorderLayout());
			left.setFont(Theme.ui(12));
			left.setForeground(Theme.TEXT_MUTED);
			right.setFont(Theme.ui(11));
			right.setForeground(Theme.TEXT_FAINT);
			add(left, BorderLayout.WEST);
			add(right, BorderLayout.EAST);
		}

		private void update(Context context, int count, double micros) {
			String name = context.getClass().getSimpleName();
			String prefix = context.prefix().isEmpty() ? "" : " \"" + context.prefix() + "\"";
			left.setText(String.format("context: %s%s     ·     %d candidate%s     ·     %.0f µs",
					name, prefix, count, count == 1 ? "" : "s", micros));
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(Theme.BORDER);
			g.fillRect(0, 0, getWidth(), 1);
		}
	}
}
