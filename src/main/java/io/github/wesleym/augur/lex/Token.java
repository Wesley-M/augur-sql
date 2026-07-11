package io.github.wesleym.augur.lex;

/** One SQL token with source offsets. */
public record Token(TokenKind kind, String text, int start, int end) {
	public Token {
		if (kind == null) {
			throw new NullPointerException("kind");
		}
		text = text == null ? "" : text;
		if (start < 0 || end < start) {
			throw new IllegalArgumentException("invalid token span: " + start + ".." + end);
		}
	}

	public boolean isText(String value) {
		return text.equalsIgnoreCase(value);
	}

	public boolean isWordLike() {
		return kind == TokenKind.IDENTIFIER || kind == TokenKind.KEYWORD || kind == TokenKind.QUOTED_IDENTIFIER
				|| kind == TokenKind.NUMBER || kind == TokenKind.STRING;
	}
}
