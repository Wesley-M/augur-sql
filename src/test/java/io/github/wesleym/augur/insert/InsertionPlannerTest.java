package io.github.wesleym.augur.insert;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.TextSpan;
import io.github.wesleym.augur.lex.LexProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsertionPlannerTest {
	@Test
	void replacementSpanCoversAlreadyEnteredIdentifierPrefix() {
		assertEquals(TextSpan.of(14, 17), InsertionPlanner.replacementSpan("select * from app", 17, Dialects.ANSI));
		assertEquals(TextSpan.of(9, 9), InsertionPlanner.replacementSpan("select p. from p", 9, Dialects.ANSI));
		assertEquals(TextSpan.of(8, 8), InsertionPlanner.replacementSpan("select .", 8, Dialects.ANSI));
		assertEquals(TextSpan.of(0, 0), InsertionPlanner.replacementSpan("select", 0, Dialects.ANSI));
		assertEquals(TextSpan.of(0, 0), InsertionPlanner.replacementSpan("", 0, null));
	}

	@Test
	void replacementSpanHandlesQuotedAndNumericTokens() {
		assertEquals(TextSpan.of(14, 18), InsertionPlanner.replacementSpan("select * from \"pat", 18, Dialects.ANSI));
		assertEquals(TextSpan.of(14, 19), InsertionPlanner.replacementSpan("select * from id_12", 19, Dialects.ANSI));
		assertEquals(TextSpan.of(7, 9), InsertionPlanner.replacementSpan("select 12", 9, Dialects.ANSI));
	}

	@Test
	void editAppliesReplacementAndReportsAbsoluteCaret() {
		InsertionEdit edit = InsertionPlanner.plan("select * from app", 17, "appointment", 11, Dialects.ANSI);

		assertEquals("select * from appointment", edit.applyTo("select * from app"));
		assertEquals(25, edit.absoluteCaret());
		assertThrows(IllegalArgumentException.class, () -> edit.applyTo("short"));
	}

	@Test
	void mirrorsKeywordCaseFromTypedPrefix() {
		InsertionPlanner planner = new InsertionPlanner(Dialects.ANSI);

		assertEquals("select", planner.keyword("", "select"));
		assertEquals("SELECT", planner.keyword("SE", "select"));
		assertEquals("select", planner.keyword("se", "SELECT"));
		assertEquals("Select", planner.keyword("Se", "select"));
		assertEquals("select", planner.keyword("sE", "select"));
		assertEquals("select", planner.keyword(null, "select"));
		assertEquals("", planner.keyword("S", null));
	}

	@Test
	void quotesIdentifiersWhenRequiredByDialect() {
		InsertionPlanner ansi = new InsertionPlanner(Dialects.ANSI);
		InsertionPlanner mysql = new InsertionPlanner(Dialects.MYSQL);
		Dialect bracket = new Dialect("bracket", LexProfile.ansi().withIdentifierQuote('[', ']'), Set.of("order"));

		assertEquals("patient", ansi.identifier("patient"));
		assertEquals("\"order\"", ansi.identifier("order"));
		assertEquals("\"patient id\"", ansi.identifier("patient id"));
		assertEquals("\"9lives\"", ansi.identifier("9lives"));
		assertEquals("`order`", mysql.identifier("order"));
		assertEquals("[a]]b]", new InsertionPlanner(bracket).identifier("a]b"));
		assertEquals("\"\"", new InsertionPlanner(new Dialect("none", new LexProfile(null, null, null, false),
				Set.of())).identifier(""));
	}

	@Test
	void validatesCaretsAndEditCaretAfter() {
		assertThrows(IllegalArgumentException.class, () -> InsertionPlanner.replacementSpan("select", -1, Dialects.ANSI));
		assertThrows(IllegalArgumentException.class, () -> InsertionPlanner.replacementSpan("select", 7, Dialects.ANSI));
		assertThrows(IllegalArgumentException.class, () -> new InsertionEdit(TextSpan.of(0, 0), "x", -1));
		assertThrows(IllegalArgumentException.class, () -> new InsertionEdit(TextSpan.of(0, 0), "x", 2));
		assertThrows(NullPointerException.class, () -> new InsertionEdit(null, "x", 0));
		assertEquals("", new InsertionEdit(TextSpan.of(0, 0), null, 0).insertText());
	}
}
