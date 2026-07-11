package io.github.wesleym.augur.stmt;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.lex.LexResult;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;

import java.util.ArrayList;
import java.util.List;

/** Semicolon-aware splitter over lexer tokens. */
public final class StatementSplitter {
	private final Dialect dialect;

	public StatementSplitter(Dialect dialect) {
		this.dialect = dialect;
	}

	public static List<StatementSpan> split(String sql, Dialect dialect) {
		return new StatementSplitter(dialect).split(sql);
	}

	public static StatementSpan statementAt(String sql, int caretOffset, Dialect dialect) {
		return new StatementSplitter(dialect).statementAt(sql, caretOffset);
	}

	public List<StatementSpan> split(String sqlText) {
		String sql = text(sqlText);
		LexResult result = SqlLexer.scan(sql, dialect);
		List<StatementSpan> out = new ArrayList<>();
		int tokenStart = 0;
		int statementStart = 0;
		List<Token> tokens = result.tokens();
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isText(";")) {
				addStatement(sql, statementStart, token.start(), tokens.subList(tokenStart, i), out);
				statementStart = token.end();
				tokenStart = i + 1;
			}
		}
		addStatement(sql, statementStart, sql.length(), tokens.subList(tokenStart, tokens.size()), out);
		return out;
	}

	public StatementSpan statementAt(String sqlText, int caretOffset) {
		String sql = text(sqlText);
		if (caretOffset < 0 || caretOffset > sql.length()) {
			throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
		}
		LexResult result = SqlLexer.scan(sql, dialect);
		List<Token> tokens = result.tokens();
		int tokenStart = 0;
		int statementStart = 0;
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isText(";")) {
				if (caretOffset <= token.start()) {
					return statement(sql, statementStart, token.start(), tokens.subList(tokenStart, i));
				}
				statementStart = token.end();
				tokenStart = i + 1;
			}
		}
		return statement(sql, statementStart, sql.length(), tokens.subList(tokenStart, tokens.size()));
	}

	private static void addStatement(String sql, int rawStart, int rawEnd, List<Token> tokens,
			List<StatementSpan> out) {
		StatementSpan statement = statement(sql, rawStart, rawEnd, tokens);
		if (statement.start() < statement.end() || !statement.tokens().isEmpty()) {
			out.add(statement);
		}
	}

	private static StatementSpan statement(String sql, int rawStart, int rawEnd, List<Token> tokens) {
		int start = trimStart(sql, rawStart, rawEnd);
		int end = trimEnd(sql, start, rawEnd);
		if (!tokens.isEmpty()) {
			start = Math.max(start, tokens.get(0).start());
			end = Math.min(end, tokens.get(tokens.size() - 1).end());
		}
		return new StatementSpan(sql, start, end, tokens);
	}

	private static int trimStart(String sql, int start, int end) {
		int i = start;
		while (i < end && Character.isWhitespace(sql.charAt(i))) {
			i++;
		}
		return i;
	}

	private static int trimEnd(String sql, int start, int end) {
		int i = end;
		while (i > start && Character.isWhitespace(sql.charAt(i - 1))) {
			i--;
		}
		return i;
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
