package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for correctness fixes found during code review. */
class ReviewFixesTest {

	@Test
	void candidateEqualityComparesMatchedCharsByValue() {
		Candidate a = new Candidate(new CandidateKind.Column(), "email", "detail", "email", 5, new int[] {0, 1}, null);
		Candidate b = new Candidate(new CandidateKind.Column(), "email", "detail", "email", 5, new int[] {0, 1}, null);
		Candidate different = new Candidate(new CandidateKind.Column(), "email", "detail", "email", 5,
				new int[] {0, 2}, null);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, different);
	}

	@Test
	void candidateEqualityDistinguishesEveryComponent() {
		Candidate base = new Candidate(new CandidateKind.Column(), "d", "det", "i", 1, new int[] {0},
				CandidateDoc.empty());

		assertEquals(base, base);
		assertNotEquals(base, null);
		assertNotEquals("not a candidate", base);
		assertNotEquals(base, new Candidate(new CandidateKind.Table(), "d", "det", "i", 1, new int[] {0},
				CandidateDoc.empty()));
		assertNotEquals(base, new Candidate(new CandidateKind.Column(), "x", "det", "i", 1, new int[] {0},
				CandidateDoc.empty()));
		assertNotEquals(base, new Candidate(new CandidateKind.Column(), "d", "x", "i", 1, new int[] {0},
				CandidateDoc.empty()));
		assertNotEquals(base, new Candidate(new CandidateKind.Column(), "d", "det", "ix", 1, new int[] {0},
				CandidateDoc.empty()));
		assertNotEquals(base, new Candidate(new CandidateKind.Column(), "d", "det", "i", 0, new int[] {0},
				CandidateDoc.empty()));
		assertNotEquals(base, new Candidate(new CandidateKind.Column(), "d", "det", "i", 1, new int[] {0},
				new CandidateDoc("q", "", List.of(), "", null, null, List.of())));
	}

	@Test
	void candidateDocRejectsOutOfRangeNullFraction() {
		assertThrows(IllegalArgumentException.class,
				() -> new CandidateDoc("q", "t", List.of(), "", 1.5, null, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new CandidateDoc("q", "t", List.of(), "", -0.1, null, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new CandidateDoc("q", "t", List.of(), "", Double.NaN, null, List.of()));
		// null (unknown) remains allowed.
		assertEquals(null, new CandidateDoc("q", "t", List.of(), "", null, null, List.of()).nullFraction());
	}

	@Test
	void withKeywordsPreservesIdentifierCaseFolding() {
		Dialect base = Dialects.POSTGRES;
		Dialect extended = base.withKeywords(List.of("UPSERT"));

		assertEquals(base.foldIdentifier("Patient"), extended.foldIdentifier("Patient"));
		assertTrue(extended.isKeyword("upsert"));
	}

	@Test
	void observeAcceptanceUpdatesUsageScore() {
		DecayingUsage usage = new DecayingUsage();
		usage.observeAcceptance(new Candidate(new CandidateKind.Column(), "email", "", "email", 5, null, null));

		assertTrue(usage.score("email") > 0);
	}

	@Test
	void cteShadowsSameNamedCatalogTable() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer").column("name", "varchar"))
				.build();
		Augur augur = Augur.create(catalog, Dialects.ANSI);
		String sql = "with patient(alpha, beta) as (select 1, 2) select p. from patient p";
		int caret = sql.indexOf("p.") + 2;

		List<String> columns = augur.complete(sql, caret).candidates().stream()
				.filter(c -> c.kind() instanceof CandidateKind.Column)
				.map(Candidate::display)
				.toList();

		assertTrue(columns.contains("alpha"), columns.toString());
		assertTrue(columns.contains("beta"), columns.toString());
		assertFalse(columns.contains("id"), columns.toString());
		assertFalse(columns.contains("name"), columns.toString());
	}
}
