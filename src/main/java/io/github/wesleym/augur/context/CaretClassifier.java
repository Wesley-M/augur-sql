package io.github.wesleym.augur.context;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.lex.LexResult;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;
import io.github.wesleym.augur.stmt.StatementSpan;
import io.github.wesleym.augur.stmt.StatementSplitter;

import java.util.ArrayList;
import java.util.List;

/** Heuristic classifier for the caret's SQL completion context. */
public final class CaretClassifier {
	private final Dialect dialect;

	public CaretClassifier(Dialect dialect) {
		this.dialect = dialect == null ? Dialects.ANSI : dialect;
	}

	public static Context classify(String sql, int caretOffset, Dialect dialect) {
		return new CaretClassifier(dialect).classify(sql, caretOffset);
	}

	public Context classify(String sqlText, int caretOffset) {
		String sql = text(sqlText);
		if (caretOffset < 0 || caretOffset > sql.length()) {
			throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
		}
		LexResult lex = SqlLexer.scan(sql, dialect);
		if (lex.quietAt(caretOffset)) {
			return new Context.Quiet("literal-or-comment");
		}
		StatementSpan statement = StatementSplitter.statementAt(sql, caretOffset, dialect);
		List<Token> significant = significantBeforeCaret(statement.tokens(), caretOffset);
		String prefix = prefixAtCaret(statement.tokens(), caretOffset);
		if (significant.isEmpty()) {
			return new Context.StatementHead(prefix);
		}

		Token last = last(significant);
		Token previous = previous(significant);
		if (last.isText(".")) {
			return new Context.ColumnRef(prefix, identifierText(previous));
		}
		if (isKeyword(last, "FROM") || isKeyword(last, "UPDATE") || isKeyword(last, "INTO")
				|| isKeyword(last, "TABLE")) {
			return new Context.TableRef(prefix);
		}
		if (isKeyword(last, "JOIN")) {
			return new Context.JoinTarget(prefix);
		}
		if (isKeyword(last, "ON")) {
			return new Context.OnCondition(prefix);
		}
		if (isKeyword(last, "BY") && isKeyword(previous, "GROUP")) {
			return new Context.GroupByRef(prefix, GroupByExpressionExtractor.extract(sql, statement.tokens()));
		}
		if (isKeyword(last, "BY") && isKeyword(previous, "ORDER")) {
			return new Context.OrderByRef(prefix);
		}
		if (isInsertColumnList(significant)) {
			return new Context.InsertColumns(prefix, true);
		}
		if (isInsertTargetTail(significant)) {
			return new Context.InsertColumns(prefix, false);
		}
		if (isSetAssignment(significant)) {
			return new Context.SetAssignment(prefix);
		}
		if (isValueStart(last)) {
			ValueTarget target = valueTarget(significant);
			return new Context.ValueLiteral(prefix, target.qualifier(), target.column());
		}
		if (isColumnStart(last)) {
			return new Context.ColumnRef(prefix);
		}
		if (isStatementHead(last)) {
			return new Context.StatementHead(prefix);
		}
		return new Context.ExpressionRef(prefix);
	}

	private static List<Token> significantBeforeCaret(List<Token> tokens, int caretOffset) {
		Token current = currentWord(tokens, caretOffset);
		int stop = current == null ? caretOffset : current.start();
		List<Token> out = new ArrayList<>();
		for (Token token : tokens) {
			if (token.end() <= stop) {
				out.add(token);
			}
		}
		return out;
	}

	private static String prefixAtCaret(List<Token> tokens, int caretOffset) {
		Token current = currentWord(tokens, caretOffset);
		if (current == null) {
			return "";
		}
		int length = Math.max(0, Math.min(caretOffset - current.start(), current.text().length()));
		return current.text().substring(0, length);
	}

	private static Token currentWord(List<Token> tokens, int caretOffset) {
		for (Token token : tokens) {
			if (token.isWordLike() && token.start() < caretOffset && caretOffset <= token.end()) {
				return token;
			}
		}
		return null;
	}

	private static boolean isInsertColumnList(List<Token> tokens) {
		int intoIndex = insertIntoIndex(tokens);
		if (intoIndex < 0 || hasInsertBodyAfter(tokens, intoIndex)) {
			return false;
		}
		int depth = 0;
		for (int i = intoIndex + 1; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isText("(")) {
				depth++;
			} else if (token.isText(")") && depth > 0) {
				depth--;
			}
		}
		return depth > 0;
	}

	private static boolean isInsertTargetTail(List<Token> tokens) {
		int intoIndex = insertIntoIndex(tokens);
		if (intoIndex < 0 || hasInsertBodyAfter(tokens, intoIndex)) {
			return false;
		}
		int afterTarget = indexAfterTargetName(tokens, intoIndex + 1);
		return afterTarget >= 0 && afterTarget == tokens.size();
	}

	private static int insertIntoIndex(List<Token> tokens) {
		int insertIndex = lastKeyword(tokens, "INSERT");
		int intoIndex = lastKeyword(tokens, "INTO");
		if (insertIndex < 0 || intoIndex <= insertIndex) {
			return -1;
		}
		return intoIndex;
	}

	private static boolean hasInsertBodyAfter(List<Token> tokens, int intoIndex) {
		int valuesIndex = lastKeyword(tokens, "VALUES");
		int selectIndex = lastKeyword(tokens, "SELECT");
		return valuesIndex > intoIndex || selectIndex > intoIndex;
	}

	private static int indexAfterTargetName(List<Token> tokens, int start) {
		if (start >= tokens.size() || !isNameToken(tokens.get(start))) {
			return -1;
		}
		int i = start + 1;
		while (i + 1 < tokens.size() && tokens.get(i).isText(".") && isNameToken(tokens.get(i + 1))) {
			i += 2;
		}
		return i;
	}

	private static boolean isSetAssignment(List<Token> tokens) {
		int setIndex = lastKeyword(tokens, "SET");
		if (setIndex < 0) {
			return false;
		}
		int whereIndex = lastKeyword(tokens, "WHERE");
		if (whereIndex > setIndex) {
			return false;
		}
		Token last = last(tokens);
		return isKeyword(last, "SET") || last != null && last.isText(",");
	}

	private static boolean isValueStart(Token token) {
		return (token != null && token.kind() == TokenKind.OPERATOR)
				|| isKeyword(token, "LIKE") || isKeyword(token, "IN") || isKeyword(token, "IS");
	}

	private static ValueTarget valueTarget(List<Token> tokens) {
		int columnIndex = tokens.size() - 2;
		if (columnIndex < 0 || !isNameToken(tokens.get(columnIndex))) {
			return ValueTarget.empty();
		}
		String column = identifierText(tokens.get(columnIndex));
		String qualifier = "";
		if (columnIndex >= 2 && tokens.get(columnIndex - 1).isText(".")
				&& isNameToken(tokens.get(columnIndex - 2))) {
			qualifier = identifierText(tokens.get(columnIndex - 2));
		}
		return new ValueTarget(qualifier, column);
	}

	private static boolean isColumnStart(Token token) {
		return isKeyword(token, "SELECT") || isKeyword(token, "WHERE") || isKeyword(token, "HAVING")
				|| isKeyword(token, "AND") || isKeyword(token, "OR") || isKeyword(token, "WHEN")
				|| isKeyword(token, "THEN") || token != null && token.isText(",");
	}

	private static boolean isStatementHead(Token token) {
		return isKeyword(token, "WITH");
	}

	private static int lastKeyword(List<Token> tokens, String keyword) {
		for (int i = tokens.size() - 1; i >= 0; i--) {
			if (isKeyword(tokens.get(i), keyword)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isKeyword(Token token, String keyword) {
		return token != null && token.kind() == TokenKind.KEYWORD && token.isText(keyword);
	}

	private static boolean isNameToken(Token token) {
		return token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.QUOTED_IDENTIFIER
				|| token.kind() == TokenKind.KEYWORD;
	}

	private static Token last(List<Token> tokens) {
		return tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
	}

	private static Token previous(List<Token> tokens) {
		return tokens.size() < 2 ? null : tokens.get(tokens.size() - 2);
	}

	private static String identifierText(Token token) {
		if (token == null) {
			return "";
		}
		String value = token.text();
		if (token.kind() != TokenKind.QUOTED_IDENTIFIER || value.isEmpty()) {
			return value;
		}
		// Unescape only the actual quote character, and tolerate an unterminated
		// identifier (no trailing quote) rather than dropping its last character.
		char open = value.charAt(0);
		String close = String.valueOf(open == '[' ? ']' : open);
		int end = value.length();
		if (end >= 2 && value.endsWith(close)) {
			end--;
		}
		return value.substring(1, end).replace(close + close, close);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	private record ValueTarget(String qualifier, String column) {
		private static ValueTarget empty() {
			return new ValueTarget("", "");
		}
	}
}
