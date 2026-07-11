package io.github.wesleym.augur.context;

import io.github.wesleym.augur.Dialects;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Regression tests for caret-classification fixes found during code review. */
class ContextFixesTest {

	@Test
	void groupByBackfillKeepsAsInsideParentheses() {
		String sql = "select cast(active as boolean), p.email from t p group by ";
		Context context = CaretClassifier.classify(sql, sql.length(), Dialects.ANSI);

		Context.GroupByRef groupBy = assertInstanceOf(Context.GroupByRef.class, context);
		assertEquals(List.of("cast(active as boolean)", "p.email"), groupBy.expressions());
	}

	@Test
	void quotedIdentifierQualifierUnescapesOnlyItsOwnQuote() {
		// ]] is not an escape for an ANSI double-quoted identifier; it must survive.
		String head = "select \"a]]b\".";
		String sql = head + " from t";
		Context context = CaretClassifier.classify(sql, head.length(), Dialects.ANSI);

		Context.ColumnRef columnRef = assertInstanceOf(Context.ColumnRef.class, context);
		assertEquals("a]]b", columnRef.qualifier());
	}

	@Test
	void bracketQuotedIdentifierUnescapesItsBracket() {
		// SQL Server uses [ ] delimiters with ]] as the escaped bracket.
		String head = "select [a]]b].";
		String sql = head + " from t";
		Context context = CaretClassifier.classify(sql, head.length(), Dialects.SQLSERVER);

		Context.ColumnRef columnRef = assertInstanceOf(Context.ColumnRef.class, context);
		assertEquals("a]b", columnRef.qualifier());
	}
}
