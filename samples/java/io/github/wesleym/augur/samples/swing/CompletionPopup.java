package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.Candidate;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;

/** A floating, non-focus-stealing completion list anchored near the caret. */
final class CompletionPopup {
	private static final int MAX_ROWS = 8;
	private static final int CELL_HEIGHT = 46;
	private static final int WIDTH = 420;

	private final JWindow window;
	private final JList<Candidate> list;
	private final CandidateModel model = new CandidateModel();
	private boolean navigated;

	CompletionPopup(Window owner, Consumer<Candidate> onSelect) {
		this.window = new JWindow(owner);
		this.window.setFocusableWindowState(false);

		this.list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(CELL_HEIGHT);
		list.setBackground(Theme.PANEL);
		list.setCellRenderer(new CandidateCellRenderer());
		list.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				onSelect.accept(list.getSelectedValue());
			}
		});

		JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(Theme.PANEL);
		scroll.getVerticalScrollBar().setUnitIncrement(CELL_HEIGHT);

		javax.swing.JPanel frame = new javax.swing.JPanel(new BorderLayout());
		frame.setBackground(Theme.PANEL);
		frame.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
		frame.add(scroll, BorderLayout.CENTER);
		window.setContentPane(frame);
	}

	void setCandidates(List<Candidate> candidates) {
		model.set(candidates);
		navigated = false;
		if (!candidates.isEmpty()) {
			list.setSelectedIndex(0);
			list.ensureIndexIsVisible(0);
		}
		int rows = Math.min(MAX_ROWS, Math.max(1, candidates.size()));
		window.setSize(new Dimension(WIDTH, rows * CELL_HEIGHT + 2));
	}

	/**
	 * Whether the user has actively moved the selection since this candidate set
	 * was shown. Enter only accepts once the popup has been navigated, so a fresh
	 * auto-popup never swallows a newline.
	 */
	boolean navigated() {
		return navigated;
	}

	void showAt(Point screenPoint) {
		window.setLocation(screenPoint);
		window.setVisible(true);
	}

	void hide() {
		window.setVisible(false);
	}

	boolean isShowing() {
		return window.isVisible();
	}

	Candidate selected() {
		return list.getSelectedValue();
	}

	int size() {
		return model.getSize();
	}

	void move(int delta) {
		int size = model.getSize();
		if (size == 0) {
			return;
		}
		int next = Math.floorMod(list.getSelectedIndex() + delta, size);
		list.setSelectedIndex(next);
		list.ensureIndexIsVisible(next);
		navigated = true;
	}

	private static final class CandidateModel extends AbstractListModel<Candidate> {
		private List<Candidate> items = List.of();

		@Override
		public int getSize() {
			return items.size();
		}

		@Override
		public Candidate getElementAt(int index) {
			return items.get(index);
		}

		private void set(List<Candidate> next) {
			int oldSize = items.size();
			items = List.copyOf(next);
			if (oldSize > 0) {
				fireIntervalRemoved(this, 0, oldSize - 1);
			}
			if (!items.isEmpty()) {
				fireIntervalAdded(this, 0, items.size() - 1);
			}
		}
	}
}
