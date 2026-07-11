package io.github.wesleym.augur.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumpMatcherTest {
	@Test
	void ranksMatchTiersInExpectedOrder() {
		assertMatch("", "appointment", MatchTier.EXACT);
		assertMatch("appointment", "appointment", MatchTier.EXACT);
		assertMatch("app", "appointment", MatchTier.PREFIX_CS);
		assertMatch("App", "appointment", MatchTier.PREFIX_CI);
		assertMatch("pi", "patient_id", MatchTier.HUMP);
		assertMatch("stat", "appointment_status", MatchTier.SUBSTRING);
		assertMatch("apt", "appointment", MatchTier.SUBSEQUENCE);
		assertFalse(HumpMatcher.match("zzz", "appointment").matched());
		assertFalse(HumpMatcher.match("x", null).matched());
	}

	@Test
	void returnsHighlightPositions() {
		assertArrayEquals(new int[] {0, 1, 2}, HumpMatcher.match("app", "appointment").positions());
		assertArrayEquals(new int[] {0, 12}, HumpMatcher.match("ai", "appointment_id").positions());
		assertArrayEquals(new int[] {0, 3, 5}, HumpMatcher.match("pin", "patient_name").positions());
		assertArrayEquals(new int[] {8, 9}, HumpMatcher.match("id", "patient_id").positions());
	}

	@Test
	void treatsSpacesAndCamelCaseAsWordStarts() {
		assertEquals(MatchTier.HUMP, HumpMatcher.match("gb", "GROUP BY").tier());
		assertEquals(MatchTier.HUMP, HumpMatcher.match("fn", "firstName").tier());
		assertEquals(MatchTier.HUMP, HumpMatcher.match("a2", "address2_line").tier());
		assertEquals(MatchTier.HUMP, HumpMatcher.match("pl", "patient-list").tier());
		assertEquals(MatchTier.HUMP, HumpMatcher.match("ps", "patient/status").tier());
		assertEquals(MatchTier.HUMP, HumpMatcher.match("a2l", "address22_line").tier());
	}

	@Test
	void scorePrefersEarlierWordStartAndCaseExactMatchesInsideATier() {
		MatchResult earlier = HumpMatcher.match("id", "id_number");
		MatchResult later = HumpMatcher.match("id", "patient_id");
		MatchResult exactCase = HumpMatcher.match("pa", "patient");
		MatchResult foldedCase = HumpMatcher.match("PA", "patient");

		assertTrue(earlier.score() > later.score());
		assertTrue(exactCase.score() > foldedCase.score());
	}

	@Test
	void defensivelyCopiesPositionsAndNormalizesNoMatch() {
		int[] positions = {2, 0};
		MatchResult result = new MatchResult(MatchTier.SUBSEQUENCE, 10, positions);
		positions[0] = 99;

		assertArrayEquals(new int[] {0, 2}, result.positions());
		assertNotSame(result.positions(), result.positions());
		assertEquals(MatchTier.NO_MATCH, MatchResult.noMatch().tier());
		assertThrows(NullPointerException.class, () -> new MatchResult(null, 0, null));
	}

	private static void assertMatch(String query, String candidate, MatchTier tier) {
		MatchResult result = HumpMatcher.match(query, candidate);

		assertTrue(result.matched(), query + " should match " + candidate);
		assertEquals(tier, result.tier());
	}
}
