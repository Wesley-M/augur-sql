package io.github.wesleym.augur.stmt;

import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementSplitterTest {
	@Test
	void splitsOnSemicolonTokensOnly() {
		String sql = "select ';'; -- ; ignored\n select 2";
		List<StatementSpan> statements = StatementSplitter.split(sql, Dialects.ANSI);

		assertEquals(2, statements.size());
		assertEquals("select ';'", statements.get(0).text());
		assertEquals("select 2", statements.get(1).text());
		assertEquals(List.of("select", "';'"),
				statements.get(0).tokens().stream().map(Token::text).toList());
	}

	@Test
	void returnsStatementContainingCaret() {
		String sql = "select 1; select 2";
		StatementSpan statement = StatementSplitter.statementAt(sql, 12, Dialects.ANSI);

		assertEquals("select 2", statement.text());
		assertTrue(statement.containsCaret(17));
	}

	@Test
	void emptyInputsAndCaretValidation() {
		assertTrue(StatementSplitter.split(null, Dialects.ANSI).isEmpty());
		assertEquals("", StatementSplitter.statementAt("", 0, Dialects.ANSI).text());
		assertThrows(IllegalArgumentException.class, () -> StatementSplitter.statementAt("select", -1, Dialects.ANSI));
		assertThrows(IllegalArgumentException.class, () -> StatementSplitter.statementAt("select", 7, Dialects.ANSI));
	}

	@Test
	void statementSpanCopiesSparseTokensAndValidatesRange() {
		Token token = new Token(TokenKind.KEYWORD, "select", 0, 6);
		StatementSpan span = new StatementSpan("select", 0, 6, Arrays.asList(null, token));

		assertEquals(List.of(token), span.tokens());
		assertThrows(IllegalArgumentException.class, () -> new StatementSpan("select", -1, 0, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new StatementSpan("select", 5, 2, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new StatementSpan("select", 0, 7, List.of()));
	}
}
