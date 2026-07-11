package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.lex.LexResult;
import io.github.wesleym.augur.lex.QuietSpan;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.HashSet;
import java.util.Set;

/**
 * A SQL editor pane that syntax-highlights using Augur's own {@link SqlLexer} —
 * the demo colors the buffer with the very tokenizer that drives completion.
 */
public final class SqlTextPane extends JTextPane {
	private final transient Dialect dialect;
	private boolean styling;

	public SqlTextPane(Dialect dialect) {
		this.dialect = dialect;
		setBackground(Theme.PANEL);
		setForeground(Theme.TEXT);
		setCaretColor(Theme.CARET);
		setSelectionColor(Theme.SELECTION);
		setSelectedTextColor(Theme.TEXT);
		setFont(Theme.mono(16));
		setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
		putClientProperty("caretWidth", 2);
		getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				scheduleHighlight();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				scheduleHighlight();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				// Ignore our own attribute changes.
			}
		});
	}

	private void scheduleHighlight() {
		if (styling) {
			return;
		}
		javax.swing.SwingUtilities.invokeLater(this::highlight);
	}

	/** Re-tokenizes the whole buffer and repaints token colors. */
	public void highlight() {
		styling = true;
		try {
			StyledDocument doc = getStyledDocument();
			String text = getText();
			doc.setCharacterAttributes(0, text.length(), style(Theme.TEXT, false), true);
			LexResult result = SqlLexer.scan(text, dialect);
			Set<Integer> stringStarts = new HashSet<>();
			for (Token token : result.tokens()) {
				if (token.kind() == TokenKind.STRING) {
					stringStarts.add(token.start());
				}
				apply(doc, token);
			}
			for (QuietSpan span : result.quietSpans()) {
				if (!stringStarts.contains(span.start())) {
					doc.setCharacterAttributes(span.start(), span.end() - span.start(),
							style(Theme.SYN_COMMENT, true), true);
				}
			}
		} finally {
			styling = false;
		}
	}

	private void apply(StyledDocument doc, Token token) {
		Color color = switch (token.kind()) {
			case KEYWORD -> Theme.SYN_KEYWORD;
			case STRING -> Theme.SYN_STRING;
			case NUMBER -> Theme.SYN_NUMBER;
			case QUOTED_IDENTIFIER -> Theme.SYN_QUOTED;
			case OPERATOR, SYMBOL -> Theme.SYN_PUNCT;
			case IDENTIFIER -> Theme.TEXT;
		};
		doc.setCharacterAttributes(token.start(), token.end() - token.start(), style(color, false), true);
	}

	private SimpleAttributeSet style(Color color, boolean italic) {
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, color);
		StyleConstants.setItalic(attrs, italic);
		StyleConstants.setFontFamily(attrs, getFont().getFamily());
		StyleConstants.setFontSize(attrs, getFont().getSize());
		return attrs;
	}

	/** Replaces the buffer and positions the caret, then re-highlights. */
	public void setContent(String text, int caret) {
		StyledDocument doc = getStyledDocument();
		styling = true;
		try {
			doc.remove(0, doc.getLength());
			doc.insertString(0, text, null);
		} catch (javax.swing.text.BadLocationException ex) {
			throw new IllegalStateException(ex);
		} finally {
			styling = false;
		}
		highlight();
		setCaretPosition(Math.max(0, Math.min(doc.getLength(), caret)));
	}

	@Override
	public Font getFont() {
		Font font = super.getFont();
		return font == null ? Theme.mono(16) : font;
	}
}
