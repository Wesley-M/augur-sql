package io.github.wesleym.augur.samples;

/**
 * A snippet of SQL with a caret position, parsed from text that marks the caret
 * with a {@code |} character. Shared by every sample so the caret convention is
 * defined in exactly one place.
 */
public record CaretText(String sql, int caret) {
	/** The character used to mark the caret in sample input. */
	public static final char MARKER = '|';

	public CaretText {
		sql = sql == null ? "" : sql;
		if (caret < 0 || caret > sql.length()) {
			throw new IllegalArgumentException("caret out of range: " + caret);
		}
	}

	/**
	 * Parses input in which the caret is marked by the first {@code |}. If no
	 * marker is present the caret is placed at the end of the text.
	 */
	public static CaretText parse(String input) {
		String text = input == null ? "" : input;
		int marker = text.indexOf(MARKER);
		if (marker < 0) {
			return new CaretText(text, text.length());
		}
		return new CaretText(text.substring(0, marker) + text.substring(marker + 1), marker);
	}
}
