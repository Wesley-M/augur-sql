package io.github.wesleym.augur.lex;

/** Coarse SQL token kinds. */
public enum TokenKind {
	KEYWORD,
	IDENTIFIER,
	QUOTED_IDENTIFIER,
	STRING,
	NUMBER,
	SYMBOL,
	OPERATOR
}
