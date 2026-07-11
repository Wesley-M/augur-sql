package io.github.wesleym.augur.rank;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Provenance;
import io.github.wesleym.augur.Usage;
import io.github.wesleym.augur.ValueShare;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.match.HumpMatcher;
import io.github.wesleym.augur.match.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankerTest {
	@Test
	void filtersNoMatchesAndInjectsHighlightPositions() {
		List<RankedCandidate> ranked = Ranker.rank(List.of(
				candidate(new CandidateKind.Table(), "appointment"),
				candidate(new CandidateKind.Table(), "patient")), "app", new Context.TableRef("app"), Usage.none());

		assertEquals(1, ranked.size());
		assertEquals("appointment", ranked.get(0).candidate().display());
		assertArrayEquals(new int[] {0, 1, 2}, ranked.get(0).candidate().matchedChars());
	}

	@Test
	void contextFitBeatsOtherwiseGoodWrongKindCandidates() {
		List<RankedCandidate> tableContext = Ranker.rank(List.of(
				candidate(new CandidateKind.Column(), "appointment"),
				candidate(new CandidateKind.Table(), "appointment_status")), "app", new Context.TableRef("app"),
				Usage.none());

		assertEquals(CandidateKind.Table.class, tableContext.get(0).candidate().kind().getClass());
	}

	@Test
	void matchTierScoreUsageRoleAndLengthAffectStableOrder() {
		Usage usage = identifier -> identifier.equals("appointment_status") ? 50 : 0;
		Candidate primary = candidate(new CandidateKind.Column(), "appointment_id",
				new CandidateDoc("", "", List.of("pk"), "", null, null, List.of()));
		Candidate used = candidate(new CandidateKind.Column(), "appointment_status");
		Candidate longColumn = candidate(new CandidateKind.Column(), "appointment_status_history");

		List<RankedCandidate> ranked = Ranker.rank(List.of(longColumn, used, primary), "app",
				new Context.ColumnRef("app"), usage);

		assertEquals("appointment_id", ranked.get(0).candidate().display());
		assertEquals("appointment_status", ranked.get(1).candidate().display());
		assertEquals("appointment_status_history", ranked.get(2).candidate().display());
		assertTrue(ranked.get(1).usageScore() > ranked.get(2).usageScore());
	}

	@Test
	void valuesKeywordsAndQuietContextsHaveSpecificFit() {
		assertEquals("open", Ranker.rank(List.of(
				candidate(new CandidateKind.Keyword(), "or"),
				candidate(new CandidateKind.Value(), "open")), "op", new Context.ValueLiteral("op"),
				Usage.none()).get(0).candidate().display());
		assertEquals("closed", Ranker.rank(List.of(
				candidate(new CandidateKind.Value(), "open",
						new CandidateDoc("", "", List.of("value"), "", null, null,
								List.of(new ValueShare("open", 0.20, false)))),
				candidate(new CandidateKind.Value(), "closed",
						new CandidateDoc("", "", List.of("value"), "", null, null,
								List.of(new ValueShare("closed", 0.70, false))))), "",
				new Context.ValueLiteral(""), Usage.none()).get(0).candidate().display());
		assertEquals("select", Ranker.rank(List.of(
				candidate(new CandidateKind.Keyword(), "select"),
				candidate(new CandidateKind.Table(), "selection")), "sel", new Context.StatementHead("sel"),
				Usage.none()).get(0).candidate().display());
		assertEquals("select *", Ranker.rank(List.of(
				candidate(new CandidateKind.Scaffold(), "select *"),
				candidate(new CandidateKind.Table(), "selectable")), "sel", new Context.StatementHead("sel"),
				Usage.none()).get(0).candidate().display());
		assertEquals(3, Ranker.key(candidate(new CandidateKind.Keyword(), "open"),
				HumpMatcher.match("op", "open"), 0, new Context.ValueLiteral("op"))[0]);
		assertEquals(9, Ranker.key(candidate(new CandidateKind.Keyword(), "select"),
				HumpMatcher.match("sel", "select"), 0, new Context.Quiet("x"))[0]);
		assertEquals(1, Ranker.key(candidate(new CandidateKind.Keyword(), "select"),
				HumpMatcher.match("sel", "select"), 0, new Context.ExpressionRef("sel"))[0]);
	}

	@Test
	void joinsAndAliasesGetUsefulBuckets() {
		List<RankedCandidate> joinRanked = Ranker.rank(List.of(
				candidate(new CandidateKind.JoinClause(null), "join patient p on p.id = a.patient_id"),
				candidate(new CandidateKind.Table(), "patient")), "pat", new Context.JoinTarget("pat"), Usage.none());
		List<RankedCandidate> columnRanked = Ranker.rank(List.of(
				candidate(new CandidateKind.Column(), "patient_id"),
				candidate(new CandidateKind.Alias(), "p")), "p", new Context.ColumnRef("p"), Usage.none());

		assertEquals("join patient p on p.id = a.patient_id", joinRanked.get(0).candidate().display());
		assertEquals("p", columnRanked.get(0).candidate().display());
		assertEquals(0, Ranker.key(candidate(new CandidateKind.View(), "patient_view"),
				HumpMatcher.match("pat", "patient_view"), 0, new Context.TableRef("pat"))[0]);
		assertEquals(0, Ranker.key(candidate(new CandidateKind.JoinPath("via_patient"), "patient via recall"),
				HumpMatcher.match("pat", "patient via recall"), 0, new Context.JoinTarget("pat"))[0]);
		assertEquals(1, Ranker.key(candidate(new CandidateKind.Table(), "patient"),
				HumpMatcher.match("pat", "patient"), 0, new Context.JoinTarget("pat"))[0]);
		assertEquals(4, Ranker.key(candidate(new CandidateKind.Column(), "patient_id"),
				HumpMatcher.match("pat", "patient_id"), 0, new Context.JoinTarget("pat"))[0]);
	}

	@Test
	void columnLikeContextsAndRoleBadgesAreBucketed() {
		Candidate expansion = candidate(new CandidateKind.Expansion(), "patient.*");
		Candidate fk = candidate(new CandidateKind.Column(), "patient_id",
				new CandidateDoc("", "", List.of("fk"), "", null, null, List.of()));
		Candidate reference = candidate(new CandidateKind.Column(), "appointment_id",
				new CandidateDoc("", "", List.of("reference"), "", null, null, List.of()));
		Candidate sensitive = candidate(new CandidateKind.Column(), "email",
				new CandidateDoc("", "", List.of("sensitive"), "", null, null, List.of()));
		Candidate primary = candidate(new CandidateKind.Column(), "id",
				new CandidateDoc("", "", List.of("primary key"), "", null, null, List.of()));
		Candidate key = candidate(new CandidateKind.Column(), "id",
				new CandidateDoc("", "", List.of("key"), "", null, null, List.of()));

		assertEquals(1, Ranker.key(expansion, HumpMatcher.match("p", "patient.*"), 0,
				new Context.GroupByRef("p"))[0]);
		assertEquals(0, Ranker.key(candidate(new CandidateKind.Scaffold(), "patient_id"),
				HumpMatcher.match("p", "patient_id"), 0, new Context.GroupByRef("p"))[0]);
		assertEquals(0, Ranker.key(expansion, HumpMatcher.match("p", "patient.*"), 0,
				new Context.OrderByRef("p"))[0]);
		assertEquals(1, Ranker.key(expansion, HumpMatcher.match("p", "patient.*"), 0,
				new Context.OnCondition("p"))[0]);
		assertEquals(0, Ranker.key(candidate(new CandidateKind.JoinClause(Provenance.DECLARED),
				"p.id = a.patient_id"), HumpMatcher.match("p", "p.id = a.patient_id"), 0,
				new Context.OnCondition("p"))[0]);
		assertEquals(3, Ranker.key(expansion, HumpMatcher.match("p", "patient.*"), 0,
				new Context.InsertColumns("p"))[0]);
		assertEquals(0, Ranker.key(candidate(new CandidateKind.Scaffold(), "(id) values (?)"),
				HumpMatcher.match("", "(id) values (?)"), 0, new Context.InsertColumns(""))[0]);
		assertEquals(1, Ranker.key(fk, HumpMatcher.match("p", "patient_id"), 0,
				new Context.InsertColumns("p"))[0]);
		assertEquals(0, Ranker.key(expansion, HumpMatcher.match("p", "patient.*"), 0,
				new Context.SetAssignment("p"))[0]);
		assertEquals(1, Ranker.key(fk, HumpMatcher.match("p", "patient_id"), 0, new Context.ColumnRef("p"))[5]);
		assertEquals(1, Ranker.key(reference, HumpMatcher.match("a", "appointment_id"), 0,
				new Context.ColumnRef("a"))[5]);
		assertEquals(8, Ranker.key(sensitive, HumpMatcher.match("e", "email"), 0, new Context.ColumnRef("e"))[5]);
		assertEquals(0, Ranker.key(primary, HumpMatcher.match("i", "id"), 0, new Context.ColumnRef("i"))[5]);
		assertEquals(0, Ranker.key(key, HumpMatcher.match("i", "id"), 0, new Context.ColumnRef("i"))[5]);
	}

	@Test
	void defensiveCopiesAndNullInputsAreHandled() {
		RankedCandidate ranked = new RankedCandidate(candidate(new CandidateKind.Table(), "patient"),
				HumpMatcher.match("p", "patient"), 0, new int[] {2, 1});
		int[] key = ranked.key();
		key[0] = 99;

		assertArrayEquals(new int[] {2, 1}, ranked.key());
		assertNotSame(ranked.key(), ranked.key());
		assertEquals("[2, 1]", ranked.debugKey());
		assertEquals("[]", Ranker.debugKey(null));
		assertEquals("[1, 2]", Ranker.debugKey(new int[] {1, 2}));
		assertTrue(Ranker.rank(null, "x", null, null).isEmpty());
		assertTrue(Ranker.rank(List.of(), "x", null, null).isEmpty());
		assertTrue(Ranker.rank(Arrays.asList(null, candidate(new CandidateKind.Table(), "patient")), "z", null, null)
				.isEmpty());
		assertEquals("patient", Ranker.rank(List.of(new Candidate(new CandidateKind.Table(), "", "", "patient", 7,
				null, null)), "pat", null, null).get(0).candidate().insertText());
		assertThrows(NullPointerException.class, () -> new RankedCandidate(null, HumpMatcher.match("p", "p"), 0,
				new int[0]));
		assertThrows(NullPointerException.class, () -> new RankedCandidate(candidate(new CandidateKind.Table(), "p"),
				null, 0, new int[0]));
		assertThrows(NullPointerException.class, () -> Ranker.key(null, HumpMatcher.match("p", "p"), 0, null));
		assertThrows(NullPointerException.class, () -> Ranker.key(candidate(new CandidateKind.Table(), "p"), null, 0,
				null));
	}

	private static Candidate candidate(CandidateKind kind, String display) {
		return candidate(kind, display, CandidateDoc.empty());
	}

	private static Candidate candidate(CandidateKind kind, String display, CandidateDoc doc) {
		return new Candidate(kind, display, "", display, display.length(), null, doc);
	}
}
