package io.github.wesleym.augur.context;

import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;

import java.util.ArrayList;
import java.util.List;

/** Extracts non-aggregate SELECT expressions that can backfill a GROUP BY clause. */
final class GroupByExpressionExtractor {
	private GroupByExpressionExtractor() { }

	static List<String> extract(String sql, List<Token> tokens) {
		int selectIndex = firstTopLevelKeyword(tokens, "SELECT", 0);
		if (selectIndex < 0) {
			return List.of();
		}
		int fromIndex = firstTopLevelKeyword(tokens, "FROM", selectIndex + 1);
		if (fromIndex < 0 || fromIndex <= selectIndex + 1) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		int expressionStart = selectIndex + 1;
		int depth = 0;
		for (int i = selectIndex + 1; i < fromIndex; i++) {
			Token token = tokens.get(i);
			if (token.isText("(")) {
				depth++;
			} else if (token.isText(")") && depth > 0) {
				depth--;
			} else if (token.isText(",") && depth == 0) {
				addExpression(sql, tokens, expressionStart, i, out);
				expressionStart = i + 1;
			}
		}
		addExpression(sql, tokens, expressionStart, fromIndex, out);
		return List.copyOf(out);
	}

	private static void addExpression(String sql, List<Token> tokens, int start, int end, List<String> out) {
		int first = start;
		while (first < end && tokens.get(first).isText("DISTINCT")) {
			first++;
		}
		int last = stripAlias(tokens, first, end);
		if (first >= last || isStarExpression(tokens, first, last) || containsAggregate(tokens, first, last)) {
			return;
		}
		String expression = normalize(sql.substring(tokens.get(first).start(), tokens.get(last - 1).end()));
		if (!expression.isEmpty()) {
			out.add(expression);
		}
	}

	private static int stripAlias(List<Token> tokens, int start, int end) {
		int depth = 0;
		for (int i = start; i < end; i++) {
			Token token = tokens.get(i);
			if (token.isText("(")) {
				depth++;
			} else if (token.isText(")") && depth > 0) {
				depth--;
			} else if (depth == 0 && isKeyword(token, "AS")) {
				return i;
			}
		}
		if (end - start > 1 && isAliasToken(tokens.get(end - 1)) && !tokens.get(end - 2).isText(".")) {
			return end - 1;
		}
		return end;
	}

	private static boolean isStarExpression(List<Token> tokens, int start, int end) {
		return (end - start == 1 && tokens.get(start).isText("*"))
				|| (end - start == 3 && tokens.get(start + 1).isText(".")
						&& tokens.get(start + 2).isText("*"));
	}

	private static boolean containsAggregate(List<Token> tokens, int start, int end) {
		for (int i = start; i + 1 < end; i++) {
			if (isAggregate(tokens.get(i)) && tokens.get(i + 1).isText("(")) {
				return true;
			}
		}
		return false;
	}

	private static int firstTopLevelKeyword(List<Token> tokens, String keyword, int start) {
		int depth = 0;
		for (int i = start; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isText("(")) {
				depth++;
			} else if (token.isText(")") && depth > 0) {
				depth--;
			} else if (depth == 0 && isKeyword(token, keyword)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isAggregate(Token token) {
		return token != null && (token.isText("COUNT") || token.isText("SUM") || token.isText("AVG")
				|| token.isText("MIN") || token.isText("MAX"));
	}

	private static boolean isKeyword(Token token, String keyword) {
		return token != null && token.kind() == TokenKind.KEYWORD && token.isText(keyword);
	}

	private static boolean isAliasToken(Token token) {
		return token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.QUOTED_IDENTIFIER;
	}

	private static String normalize(String value) {
		return (value == null ? "" : value).trim().replaceAll("\\s+", " ");
	}
}
