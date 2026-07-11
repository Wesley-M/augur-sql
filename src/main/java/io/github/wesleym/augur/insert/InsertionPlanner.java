package io.github.wesleym.augur.insert;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.TextSpan;
import io.github.wesleym.augur.lex.IdentifierQuote;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;

import java.util.List;
import java.util.Locale;

/** Dialect-aware insertion text and replacement-span helper. */
public final class InsertionPlanner {
	private final Dialect dialect;

	public InsertionPlanner(Dialect dialect) {
		this.dialect = dialect == null ? Dialects.ANSI : dialect;
	}

	public static TextSpan replacementSpan(String sql, int caretOffset, Dialect dialect) {
		return new InsertionPlanner(dialect).replacementSpan(sql, caretOffset);
	}

	public static InsertionEdit plan(String sql, int caretOffset, String insertText, int caretAfter,
			Dialect dialect) {
		return new InsertionPlanner(dialect).plan(sql, caretOffset, insertText, caretAfter);
	}

	public TextSpan replacementSpan(String sqlText, int caretOffset) {
		String sql = text(sqlText);
		if (caretOffset < 0 || caretOffset > sql.length()) {
			throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
		}
		for (Token token : SqlLexer.lex(sql, dialect)) {
			if (token.start() < caretOffset && caretOffset <= token.end()) {
				return spanForToken(token, caretOffset);
			}
			if (caretOffset == token.start()) {
				return TextSpan.of(caretOffset, caretOffset);
			}
		}
		return scanBareIdentifier(sql, caretOffset);
	}

	public InsertionEdit plan(String sql, int caretOffset, String insertText, int caretAfter) {
		String text = text(insertText);
		return new InsertionEdit(replacementSpan(sql, caretOffset), text, caretAfter);
	}

	public String keyword(String typedPrefix, String keyword) {
		String prefix = text(typedPrefix);
		String value = text(keyword);
		if (prefix.isEmpty() || value.isEmpty()) {
			return value;
		}
		if (prefix.equals(prefix.toUpperCase(Locale.ROOT))) {
			return value.toUpperCase(Locale.ROOT);
		}
		if (prefix.equals(prefix.toLowerCase(Locale.ROOT))) {
			return value.toLowerCase(Locale.ROOT);
		}
		if (Character.isUpperCase(prefix.charAt(0)) && prefix.substring(1).equals(prefix.substring(1)
				.toLowerCase(Locale.ROOT))) {
			return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
		}
		return value;
	}

	public String identifier(String identifier) {
		String value = text(identifier);
		if (!needsQuoting(value)) {
			return value;
		}
		IdentifierQuote quote = identifierQuote();
		String close = String.valueOf(quote.close());
		return quote.open() + value.replace(close, close + close) + quote.close();
	}

	private TextSpan spanForToken(Token token, int caretOffset) {
		if (replaceableToken(token)) {
			return TextSpan.of(token.start(), caretOffset);
		}
		return TextSpan.of(caretOffset, caretOffset);
	}

	private static boolean replaceableToken(Token token) {
		return token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.KEYWORD
				|| token.kind() == TokenKind.NUMBER || token.kind() == TokenKind.QUOTED_IDENTIFIER;
	}

	private TextSpan scanBareIdentifier(String sql, int caretOffset) {
		int start = caretOffset;
		while (start > 0 && dialect.lex().isIdentifierPart(sql.charAt(start - 1))) {
			start--;
		}
		return TextSpan.of(start, caretOffset);
	}

	private boolean needsQuoting(String value) {
		return dialect.needsQuoting(value);
	}

	private IdentifierQuote identifierQuote() {
		List<IdentifierQuote> quotes = dialect.lex().identifierQuotes();
		return quotes.isEmpty() ? new IdentifierQuote('"', '"') : quotes.get(0);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
