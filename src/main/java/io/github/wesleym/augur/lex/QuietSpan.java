package io.github.wesleym.augur.lex;

/** A source span where completion should stay quiet, such as a string literal or comment. */
public record QuietSpan(int start, int end, boolean includeEnd) {
	public QuietSpan {
		if (start < 0 || end < start) {
			throw new IllegalArgumentException("invalid quiet span: " + start + ".." + end);
		}
	}

	public boolean containsCaret(int caretOffset) {
		return caretOffset > start && (caretOffset < end || (includeEnd && caretOffset == end));
	}
}
