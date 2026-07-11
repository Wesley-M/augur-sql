package io.github.wesleym.augur.insert;

import io.github.wesleym.augur.Dialects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression tests for keyword case mirroring, including empty-keyword edge cases. */
class InsertionKeywordTest {
	private final InsertionPlanner planner = new InsertionPlanner(Dialects.ANSI);

	@Test
	void titleCasePrefixWithEmptyKeywordDoesNotThrow() {
		// "Se" reaches the title-case branch, which used to call charAt(0) on an empty keyword.
		assertDoesNotThrow(() -> planner.keyword("Se", ""));
		assertEquals("", planner.keyword("Se", ""));
		assertEquals("", planner.keyword("Se", null));
	}

	@Test
	void titleCasePrefixMirrorsOntoKeyword() {
		assertEquals("Select", planner.keyword("Se", "select"));
		assertEquals("SELECT", planner.keyword("SE", "select"));
		assertEquals("select", planner.keyword("se", "select"));
	}
}
