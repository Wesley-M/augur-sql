package io.github.wesleym.augur.lex;

import java.util.ArrayList;
import java.util.List;

/** Lexer output: comments are absent from tokens but retained as quiet spans for caret classification. */
public record LexResult(String sql, List<Token> tokens, List<QuietSpan> quietSpans) {
	public LexResult {
		sql = text(sql);
		tokens = copy(tokens);
		quietSpans = copy(quietSpans);
	}

	public boolean quietAt(int caretOffset) {
		for (QuietSpan span : quietSpans) {
			if (span.containsCaret(caretOffset)) {
				return true;
			}
		}
		return false;
	}

	private static <T> List<T> copy(List<T> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<T> out = new ArrayList<>(values.size());
		for (T value : values) {
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
