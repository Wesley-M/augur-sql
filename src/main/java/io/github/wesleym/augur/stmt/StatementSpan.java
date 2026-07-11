package io.github.wesleym.augur.stmt;

import io.github.wesleym.augur.lex.Token;

import java.util.ArrayList;
import java.util.List;

/** One semicolon-delimited SQL statement and its token subset. */
public record StatementSpan(String sql, int start, int end, List<Token> tokens) {
	public StatementSpan {
		sql = text(sql);
		if (start < 0 || end < start || end > sql.length()) {
			throw new IllegalArgumentException("invalid statement span: " + start + ".." + end);
		}
		tokens = copy(tokens);
	}

	public String text() {
		return sql.substring(start, end);
	}

	public boolean containsCaret(int caretOffset) {
		return caretOffset >= start && caretOffset <= end;
	}

	private static List<Token> copy(List<Token> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<Token> out = new ArrayList<>(values.size());
		for (Token value : values) {
			if (value != null) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
