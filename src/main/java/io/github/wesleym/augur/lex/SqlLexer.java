package io.github.wesleym.augur.lex;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;

import java.util.ArrayList;
import java.util.List;

/** Offset-bearing SQL tokenizer. Whitespace and comments are omitted from the token stream. */
public final class SqlLexer {
	private final Dialect dialect;

	public SqlLexer(Dialect dialect) {
		this.dialect = dialect == null ? Dialects.ANSI : dialect;
	}

	public static LexResult scan(String sql, Dialect dialect) {
		return new SqlLexer(dialect).scan(sql);
	}

	public static List<Token> lex(String sql, Dialect dialect) {
		return scan(sql, dialect).tokens();
	}

	public LexResult scan(String sqlText) {
		String sql = text(sqlText);
		LexProfile profile = dialect.lex();
		List<Token> tokens = new ArrayList<>();
		List<QuietSpan> quietSpans = new ArrayList<>();
		int i = 0;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			String lineComment = lineCommentAt(sql, i, profile);
			if (lineComment != null) {
				i = skipLineComment(sql, i, lineComment, quietSpans);
				continue;
			}
			LexProfile.BlockComment blockComment = blockCommentAt(sql, i, profile);
			if (blockComment != null) {
				i = skipBlockComment(sql, i, blockComment, quietSpans);
				continue;
			}
			IdentifierQuote quote = profile.quoteStartingWith(c);
			if (quote != null) {
				i = readQuotedIdentifier(sql, i, quote, tokens);
				continue;
			}
			if (c == '\'') {
				i = readString(sql, i, tokens, quietSpans);
				continue;
			}
			if (Character.isDigit(c)) {
				i = readNumber(sql, i, tokens);
				continue;
			}
			if (profile.isIdentifierStart(c)) {
				i = readIdentifier(sql, i, profile, tokens);
				continue;
			}
			if (isSymbol(c)) {
				tokens.add(new Token(TokenKind.SYMBOL, String.valueOf(c), i, i + 1));
				i++;
				continue;
			}
			i = readOperator(sql, i, tokens);
		}
		return new LexResult(sql, tokens, quietSpans);
	}

	private String lineCommentAt(String sql, int offset, LexProfile profile) {
		for (String opener : profile.lineComments()) {
			if (sql.startsWith(opener, offset)) {
				return opener;
			}
		}
		return null;
	}

	private LexProfile.BlockComment blockCommentAt(String sql, int offset, LexProfile profile) {
		for (LexProfile.BlockComment comment : profile.blockComments()) {
			if (sql.startsWith(comment.opener(), offset)) {
				return comment;
			}
		}
		return null;
	}

	private static int skipLineComment(String sql, int start, String opener, List<QuietSpan> quietSpans) {
		int end = start + opener.length();
		while (end < sql.length() && sql.charAt(end) != '\n' && sql.charAt(end) != '\r') {
			end++;
		}
		quietSpans.add(new QuietSpan(start, end, true));
		return end;
	}

	private static int skipBlockComment(String sql, int start, LexProfile.BlockComment comment,
			List<QuietSpan> quietSpans) {
		int bodyStart = start + comment.opener().length();
		int closer = sql.indexOf(comment.closer(), bodyStart);
		if (closer < 0) {
			quietSpans.add(new QuietSpan(start, sql.length(), true));
			return sql.length();
		}
		int end = closer + comment.closer().length();
		quietSpans.add(new QuietSpan(start, end, false));
		return end;
	}

	private static int readQuotedIdentifier(String sql, int start, IdentifierQuote quote, List<Token> tokens) {
		int i = start + 1;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (c == quote.close()) {
				if (i + 1 < sql.length() && sql.charAt(i + 1) == quote.close()) {
					i += 2;
					continue;
				}
				i++;
				break;
			}
			i++;
		}
		tokens.add(new Token(TokenKind.QUOTED_IDENTIFIER, sql.substring(start, i), start, i));
		return i;
	}

	private static int readString(String sql, int start, List<Token> tokens, List<QuietSpan> quietSpans) {
		int i = start + 1;
		boolean closed = false;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (c == '\'') {
				if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
					i += 2;
					continue;
				}
				i++;
				closed = true;
				break;
			}
			i++;
		}
		tokens.add(new Token(TokenKind.STRING, sql.substring(start, i), start, i));
		quietSpans.add(new QuietSpan(start, i, !closed));
		return i;
	}

	private static int readNumber(String sql, int start, List<Token> tokens) {
		int i = start;
		boolean sawDot = false;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (Character.isDigit(c)) {
				i++;
			} else if (c == '.' && !sawDot && i + 1 < sql.length() && Character.isDigit(sql.charAt(i + 1))) {
				sawDot = true;
				i++;
			} else {
				break;
			}
		}
		tokens.add(new Token(TokenKind.NUMBER, sql.substring(start, i), start, i));
		return i;
	}

	private int readIdentifier(String sql, int start, LexProfile profile, List<Token> tokens) {
		int i = start + 1;
		while (i < sql.length() && profile.isIdentifierPart(sql.charAt(i))) {
			i++;
		}
		String text = sql.substring(start, i);
		TokenKind kind = dialect.isKeyword(text) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER;
		tokens.add(new Token(kind, text, start, i));
		return i;
	}

	private static int readOperator(String sql, int start, List<Token> tokens) {
		int end = start + 1;
		if (end < sql.length()) {
			String two = sql.substring(start, end + 1);
			if (two.equals("<>") || two.equals("!=") || two.equals("<=") || two.equals(">=")
					|| two.equals("||") || two.equals("&&") || two.equals("::") || two.equals(":=")
					|| two.equals("=>") || two.equals("->")) {
				end++;
				if (two.equals("->") && end < sql.length() && sql.charAt(end) == '>') {
					end++;
				}
			}
		}
		tokens.add(new Token(TokenKind.OPERATOR, sql.substring(start, end), start, end));
		return end;
	}

	private static boolean isSymbol(char c) {
		return c == '(' || c == ')' || c == ',' || c == ';' || c == '.' || c == '*';
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
