package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugurTest {
	@Test
	void facadeBuildsWithRequiredSnapshots() {
		Catalog catalog = Catalog.builder().build();

		Augur augur = Augur.create(catalog);

		assertSame(catalog, augur.catalog());
		assertSame(Dialects.ANSI, augur.dialect());
		assertEquals(0, augur.usage().score("appointment"));
		assertTrue(augur.profiles().values("appointment", "status").isEmpty());
	}

	@Test
	void builderRequiresCatalogAndDefaultsDialect() {
		NullPointerException missingCatalog = assertThrows(NullPointerException.class,
				() -> Augur.builder().build());
		Catalog catalog = Catalog.builder().build();
		Augur custom = Augur.create(catalog, Dialects.MYSQL);

		assertEquals("catalog", missingCatalog.getMessage());
		assertSame(Dialects.MYSQL, custom.dialect());
	}

	@Test
	void completesTablesThroughTheFacade() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);

		Completion completion = augur.complete("select * from app", 17);
		Candidate candidate = completion.first().orElseThrow();

		assertEquals(TextSpan.of(14, 17), completion.replaceSpan());
		assertEquals("appointment", candidate.display());
		assertEquals("appointment", candidate.insertText());
		assertEquals(11, candidate.caretAfter());
		assertInstanceOf(CandidateKind.Table.class, candidate.kind());
		assertEquals(List.of("table", "48210 rows"), candidate.doc().badges());
		assertEquals(1, completion.candidates().size());
		assertEquals("select * from appointment", completion.applyFirst("select * from app"));
	}

	@Test
	void completesViewsAndDialectQuotedTables() {
		Catalog catalog = Catalog.builder()
				.view("active patient", t -> t.column("id", "integer"))
				.table("order detail", t -> t.column("id", "integer"))
				.build();
		Augur augur = augur(catalog, Dialects.MYSQL);

		Completion viewCompletion = augur.complete("select * from active", 20);
		Completion tableCompletion = augur.complete("select * from ord", 17);

		assertInstanceOf(CandidateKind.View.class, viewCompletion.candidates().get(0).kind());
		assertEquals("`active patient`", viewCompletion.candidates().get(0).insertText());
		assertEquals("`order detail`", tableCompletion.candidates().get(0).insertText());
	}

	@Test
	void completesKeywordsThroughTheFacade() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);

		Completion lower = augur.complete("sel");
		Completion upper = augur.complete("SEL", 3);

		assertEquals(TextSpan.of(0, 3), lower.replaceSpan());
		assertEquals("select", lower.candidates().get(0).insertText());
		assertEquals("SELECT", upper.candidates().get(0).insertText());
		assertInstanceOf(CandidateKind.Keyword.class, lower.candidates().get(0).kind());
	}

	@Test
	void quietContextsSuppressCandidates() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);

		Completion completion = augur.complete("select 'app", 11);

		assertTrue(completion.isEmpty());
		assertEquals("select 'app", completion.applyFirst("select 'app"));
	}

	@Test
	void ranksGeneratedCandidatesWithUsage() {
		Catalog catalog = Catalog.builder()
				.table("patient_a", t -> t.column("id", "integer"))
				.table("patient_b", t -> t.column("id", "integer"))
				.build();
		Usage usage = identifierLower -> identifierLower.equals("patient_b") ? 200 : 0;
		Augur augur = Augur.builder()
				.catalog(catalog)
				.dialect(Dialects.ANSI)
				.usage(usage)
				.build();

		Completion completion = augur.complete("select * from pat", 17);

		assertEquals("patient_b", completion.candidates().get(0).display());
	}

	@Test
	void completesColumnsForVisibleSources() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);

		Completion completion = augur.complete("select fir from patient p", 10);

		assertEquals(TextSpan.of(7, 10), completion.replaceSpan());
		assertEquals("first_name", completion.candidates().get(0).display());
		assertEquals("first_name", completion.candidates().get(0).insertText());
		assertInstanceOf(CandidateKind.Column.class, completion.candidates().get(0).kind());
	}

	@Test
	void completesQualifiedColumnsForAliases() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "select p.fi from patient p";

		Completion completion = augur.complete(sql, "select p.fi".length());

		assertEquals(TextSpan.of(9, 11), completion.replaceSpan());
		assertEquals("first_name", completion.candidates().get(0).display());
		assertEquals("first_name", completion.candidates().get(0).insertText());
	}

	@Test
	void completesAliasesForVisibleSources() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "select p from patient p join appointment a on a.patient_id = p.id";

		Completion completion = augur.complete(sql, "select p".length());

		assertEquals("p", completion.candidates().get(0).display());
		assertInstanceOf(CandidateKind.Alias.class, completion.candidates().get(0).kind());
	}

	@Test
	void completesStarExpansions() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String unqualified = "select * from patient p join appointment a on true";
		String qualified = "select p. from patient p";

		Completion all = augur.complete(unqualified, "select *".length());
		Completion fromAlias = augur.complete(qualified, "select p.".length());

		assertTrue(all.candidates().stream().anyMatch(candidate -> candidate.display().equals("p.*")));
		Candidate expansion = fromAlias.candidates().stream()
				.filter(candidate -> candidate.kind() instanceof CandidateKind.Expansion)
				.findFirst()
				.orElseThrow();
		assertEquals("p.*", expansion.display());
		assertEquals("*", expansion.insertText());
		assertTrue(all.candidates().stream().anyMatch(candidate ->
				candidate.display().equals("p.id, p.first_name, p.email")));
	}

	@Test
	void completesFkBackedJoinTargets() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "select * from appointment a join pat";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals(TextSpan.of("select * from appointment a join ".length(), sql.length()),
				completion.replaceSpan());
		assertEquals("patient p ON p.id = a.patient_id", candidate.insertText());
		assertInstanceOf(CandidateKind.JoinClause.class, candidate.kind());
	}

	@Test
	void offersWholeJoinWhileTypingTheJoinKeyword() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "select * from appointment a jo";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().stream()
				.filter(c -> c.kind() instanceof CandidateKind.JoinClause)
				.findFirst()
				.orElseThrow();

		// The whole join is offered before the JOIN keyword is finished: it leads with `join ` and carries the
		// FK-backed target and ON predicate.
		assertTrue(candidate.insertText().startsWith("join "), candidate.insertText());
		assertTrue(candidate.insertText().contains(" ON "), candidate.insertText());
	}

	@Test
	void completesJunctionJoinPaths() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("provider", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("patient_provider", t -> t
						.column("patient_id", "integer", c -> c.primaryKey()
								.referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer", c -> c.primaryKey()
								.referencing("provider", "id", Provenance.DECLARED)))
				.build();
		Augur augur = augur(catalog, Dialects.ANSI);
		String sql = "select * from patient p join pro";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals("provider p2 via patient_provider", candidate.display());
		assertEquals("patient_provider pp ON pp.patient_id = p.id JOIN provider p2 ON p2.id = pp.provider_id",
				candidate.insertText());
		assertInstanceOf(CandidateKind.JoinPath.class, candidate.kind());
	}

	@Test
	void completesFkBackedOnPredicates() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "select * from appointment a join patient p on ";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals("p.id = a.patient_id", candidate.insertText());
		assertInstanceOf(CandidateKind.JoinClause.class, candidate.kind());
	}

	@Test
	void completesInsertScaffoldsAfterTarget() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "insert into patient ";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals(TextSpan.of(sql.length(), sql.length()), completion.replaceSpan());
		assertEquals("(id, first_name, email) values (?, ?, ?)", candidate.insertText());
		assertEquals(candidate.insertText().indexOf('?'), candidate.caretAfter());
		assertInstanceOf(CandidateKind.Scaffold.class, candidate.kind());
	}

	@Test
	void completesInsertScaffoldsAfterOpenParen() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "insert into patient (";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals("id, first_name, email) values (?, ?, ?)", candidate.insertText());
		assertEquals(candidate.insertText().indexOf('?'), candidate.caretAfter());
		assertInstanceOf(CandidateKind.Scaffold.class, candidate.kind());
	}

	@Test
	void completesGroupByBackfill() {
		Augur augur = augur(demoCatalog(), Dialects.ANSI);
		String sql = "select p.first_name, p.email, count(*) from patient p group by ";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals("p.first_name, p.email", candidate.insertText());
		assertInstanceOf(CandidateKind.Scaffold.class, candidate.kind());
	}

	@Test
	void completesProfiledValuesForComparisonColumns() {
		Profiles profiles = (table, column) -> table.equals("appointment") && column.equals("status")
				? List.of(new ValueShare("open", 0.20, false), new ValueShare("closed", 0.70, false))
				: List.of();
		Augur augur = Augur.builder()
				.catalog(demoCatalog())
				.dialect(Dialects.ANSI)
				.profiles(profiles)
				.build();
		String sql = "select * from appointment a where status = ";

		Completion completion = augur.complete(sql, sql.length());
		Candidate candidate = completion.candidates().get(0);

		assertEquals("'closed'", candidate.insertText());
		assertEquals("closed", candidate.display());
		assertInstanceOf(CandidateKind.Value.class, candidate.kind());
	}

	@Test
	void completeValidatesCaretAgainstNormalizedSqlText() {
		Augur augur = Augur.builder()
				.catalog(Catalog.builder().build())
				.dialect(Dialects.ANSI)
				.build();

		assertEquals(TextSpan.of(0, 0), augur.complete(null, 0).replaceSpan());
		assertThrows(IllegalArgumentException.class, () -> augur.complete("select", -1));
		assertThrows(IllegalArgumentException.class, () -> augur.complete("select", 7));
	}

	private static Augur augur(Catalog catalog, Dialect dialect) {
		return Augur.builder()
				.catalog(catalog)
				.dialect(dialect)
				.build();
	}

	private static Catalog demoCatalog() {
		return Catalog.builder()
				.table("appointment", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("patient_id", "integer",
								c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("status", "varchar")
						.rowCount(48_210))
				.table("patient", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("first_name", "varchar")
						.column("email", "varchar", c -> c.role(ColumnRole.SENSITIVE)))
				.build();
	}
}
