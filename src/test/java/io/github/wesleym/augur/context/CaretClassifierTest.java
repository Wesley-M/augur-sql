package io.github.wesleym.augur.context;

import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.testkit.CaretCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaretClassifierTest {
	@Test
	void classifiesTableReferenceContexts() {
		assertContext("select * from |", Context.TableRef.class, "");
		assertContext("select * from app|", Context.TableRef.class, "app");
		assertContext("update pat| set name = 'x'", Context.TableRef.class, "pat");
		assertContext("insert into pat|", Context.TableRef.class, "pat");
		assertContext("drop table |", Context.TableRef.class, "");
	}

	@Test
	void classifiesJoinAndOnContexts() {
		assertContext("select * from appointment a join |", Context.JoinTarget.class, "");
		assertContext("select * from appointment a left join pat|", Context.JoinTarget.class, "pat");
		assertContext("select * from appointment a join patient p on |", Context.OnCondition.class, "");
	}

	@Test
	void classifiesColumnExpressionAndValueContexts() {
		assertContext("|", Context.StatementHead.class, "");
		assertContext("sel|", Context.StatementHead.class, "sel");
		assertContext("select | from patient", Context.ColumnRef.class, "");
		assertContext("select p.| from patient p", Context.ColumnRef.class, "");
		assertColumnContext("select p.fi| from patient p", "fi", "p");
		assertColumnContext("select \"p\".| from patient \"p\"", "", "p");
		assertContext("select * from patient where |", Context.ColumnRef.class, "");
		assertContext("select * from patient where status = |", Context.ValueLiteral.class, "");
		assertValueContext("select * from patient where status = |", "", "status");
		assertValueContext("select * from patient p where p.status = |", "p", "status");
		assertContext("select * from patient where status like op|", Context.ValueLiteral.class, "op");
		assertValueContext("select * from patient where status like op|", "", "status");
		assertContext("select * from patient where status in |", Context.ValueLiteral.class, "");
		assertValueContext("select * from patient where status in |", "", "status");
		assertContext("select * from patient where status is |", Context.ValueLiteral.class, "");
		assertValueContext("select * from patient where status is |", "", "status");
		assertContext("select * from patient where status = 'A' and |", Context.ColumnRef.class, "");
		assertContext("select * from patient where status = 'A' or |", Context.ColumnRef.class, "");
		assertContext("select status, | from appointment", Context.ColumnRef.class, "");
		assertContext("select status from appointment having |", Context.ColumnRef.class, "");
		assertContext("select case when |", Context.ColumnRef.class, "");
		assertContext("select case when active then |", Context.ColumnRef.class, "");
		assertContext("select first_name from patient |", Context.ExpressionRef.class, "");
		assertContext("with |", Context.StatementHead.class, "");
	}

	@Test
	void classifiesClauseSpecificContexts() {
		assertInsertContext("insert into patient |", "", false);
		assertContext("insert into patient (|", Context.InsertColumns.class, "");
		assertInsertContext("insert into patient (|", "", true);
		assertInsertContext("insert into patient (id, |", "", true);
		assertContext("insert into patient values |", Context.ExpressionRef.class, "");
		assertContext("insert patient (|", Context.ExpressionRef.class, "");
		assertContext("insert into patient select (|", Context.ExpressionRef.class, "");
		assertContext("update patient set |", Context.SetAssignment.class, "");
		assertContext("update patient set first_name = 'A', |", Context.SetAssignment.class, "");
		assertContext("update patient set first_name = 'A' where |", Context.ColumnRef.class, "");
		assertContext("select status from appointment group by |", Context.GroupByRef.class, "");
		assertGroupByContext("select p.first_name, p.email, count(*) from patient p group by |",
				List.of("p.first_name", "p.email"));
		assertGroupByContext("select distinct p.first_name as name, p.email email from patient p group by |",
				List.of("p.first_name", "p.email"));
		assertGroupByContext("select *, p.*, sum(a.total), concat(p.first_name, p.email) full_name "
				+ "from patient p join appointment a on true group by |",
				List.of("concat(p.first_name, p.email)"));
		assertGroupByContext("group by |", List.of());
		assertGroupByContext("select from patient group by |", List.of());
		assertGroupByContext("select p.first_name group by |", List.of());
		assertContext("select status from appointment order by sta|", Context.OrderByRef.class, "sta");
		assertContext("select status from appointment by |", Context.ExpressionRef.class, "");
	}

	@Test
	void quietsInsideStringsAndComments() {
		assertContext("select '|", Context.Quiet.class, "");
		assertContext("select * from patient -- nope |", Context.Quiet.class, "");
		assertContext("select * from patient /* nope | */", Context.Quiet.class, "");
		assertContext("select * from patient /* nope |", Context.Quiet.class, "");
	}

	@Test
	void validatesCaretAndContextDefaults() {
		assertEquals(Context.StatementHead.class, new CaretClassifier(null).classify("", 0).getClass());
		assertThrows(IllegalArgumentException.class, () -> CaretClassifier.classify("select", -1, Dialects.ANSI));
		assertThrows(IllegalArgumentException.class, () -> CaretClassifier.classify("select", 7, Dialects.ANSI));

		List<Context> contexts = List.of(new Context.TableRef(null), new Context.ColumnRef(null),
				new Context.JoinTarget(null), new Context.OnCondition(null), new Context.InsertColumns(null),
				new Context.SetAssignment(null), new Context.GroupByRef(null), new Context.OrderByRef(null),
				new Context.ValueLiteral(null), new Context.StatementHead(null), new Context.ExpressionRef(null));
		for (Context context : contexts) {
			assertEquals("", context.prefix());
		}
		assertEquals(List.of(), new Context.GroupByRef("", null).expressions());
		assertEquals(List.of("", "p.id"),
				new Context.GroupByRef("", java.util.Arrays.asList(null, "p.id")).expressions());
		assertEquals(false, new Context.InsertColumns(null).openParenTyped());
		assertEquals("", new Context.ValueLiteral(null, null, null).qualifier());
		assertEquals("", new Context.ValueLiteral(null, null, null).column());
		assertEquals("", new Context.Quiet(null).reason());
	}

	private static void assertContext(String markedSql, Class<? extends Context> type, String prefix) {
		CaretCase c = CaretCase.parse(markedSql);
		Context context = CaretClassifier.classify(c.sql(), c.caretOffset(), Dialects.ANSI);

		assertInstanceOf(type, context);
		assertEquals(prefix, context.prefix());
	}

	private static void assertColumnContext(String markedSql, String prefix, String qualifier) {
		CaretCase c = CaretCase.parse(markedSql);
		Context context = CaretClassifier.classify(c.sql(), c.caretOffset(), Dialects.ANSI);
		Context.ColumnRef column = assertInstanceOf(Context.ColumnRef.class, context);

		assertEquals(prefix, column.prefix());
		assertEquals(qualifier, column.qualifier());
	}

	private static void assertInsertContext(String markedSql, String prefix, boolean openParenTyped) {
		CaretCase c = CaretCase.parse(markedSql);
		Context context = CaretClassifier.classify(c.sql(), c.caretOffset(), Dialects.ANSI);
		Context.InsertColumns insertColumns = assertInstanceOf(Context.InsertColumns.class, context);

		assertEquals(prefix, insertColumns.prefix());
		assertEquals(openParenTyped, insertColumns.openParenTyped());
	}

	private static void assertValueContext(String markedSql, String qualifier, String column) {
		CaretCase c = CaretCase.parse(markedSql);
		Context context = CaretClassifier.classify(c.sql(), c.caretOffset(), Dialects.ANSI);
		Context.ValueLiteral value = assertInstanceOf(Context.ValueLiteral.class, context);

		assertEquals(qualifier, value.qualifier());
		assertEquals(column, value.column());
	}

	private static void assertGroupByContext(String markedSql, List<String> expressions) {
		CaretCase c = CaretCase.parse(markedSql);
		Context context = CaretClassifier.classify(c.sql(), c.caretOffset(), Dialects.ANSI);
		Context.GroupByRef groupBy = assertInstanceOf(Context.GroupByRef.class, context);

		assertEquals(expressions, groupBy.expressions());
	}
}
