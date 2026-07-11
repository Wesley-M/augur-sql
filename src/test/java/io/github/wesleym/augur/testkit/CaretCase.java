package io.github.wesleym.augur.testkit;

import java.util.Objects;

/** Test helper for fixtures that mark the caret with a single pipe character. */
public record CaretCase(String sql, int caretOffset) {
	public CaretCase {
		sql = Objects.requireNonNull(sql, "sql");
		if (caretOffset < 0 || caretOffset > sql.length()) {
			throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
		}
	}

	public static CaretCase parse(String markedSql) {
		Objects.requireNonNull(markedSql, "markedSql");
		int first = markedSql.indexOf('|');
		if (first < 0) {
			throw new IllegalArgumentException("missing caret marker");
		}
		if (markedSql.indexOf('|', first + 1) >= 0) {
			throw new IllegalArgumentException("multiple caret markers");
		}
		return new CaretCase(markedSql.substring(0, first) + markedSql.substring(first + 1), first);
	}
}
