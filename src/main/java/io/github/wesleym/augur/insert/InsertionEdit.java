package io.github.wesleym.augur.insert;

import io.github.wesleym.augur.TextSpan;

import java.util.Objects;

/** Text edit produced when a completion candidate is accepted. */
public record InsertionEdit(TextSpan replaceSpan, String insertText, int caretAfter) {
	public InsertionEdit {
		replaceSpan = Objects.requireNonNull(replaceSpan, "replaceSpan");
		insertText = insertText == null ? "" : insertText;
		if (caretAfter < 0 || caretAfter > insertText.length()) {
			throw new IllegalArgumentException("caretAfter out of range: " + caretAfter);
		}
	}

	public int absoluteCaret() {
		return replaceSpan.start() + caretAfter;
	}

	public String applyTo(String sql) {
		String text = sql == null ? "" : sql;
		if (replaceSpan.end() > text.length()) {
			throw new IllegalArgumentException("replace span exceeds text length");
		}
		return text.substring(0, replaceSpan.start()) + insertText + text.substring(replaceSpan.end());
	}
}
