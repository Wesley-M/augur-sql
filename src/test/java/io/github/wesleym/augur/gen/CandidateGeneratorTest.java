package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;
import io.github.wesleym.augur.ProfileSnapshot;
import io.github.wesleym.augur.Profiles;
import io.github.wesleym.augur.Provenance;
import io.github.wesleym.augur.TypeFamily;
import io.github.wesleym.augur.ValueShare;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeResolver;
import io.github.wesleym.augur.scope.ScopeSource;
import io.github.wesleym.augur.scope.SourceKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateGeneratorTest {
	@Test
	void tableGeneratorCreatesTablesAndViews() {
		Catalog catalog = Catalog.builder()
				.table("appointment", t -> t.rowCount(10))
				.view("active_patient", t -> { })
				.build();

		List<Candidate> candidates = TableGenerator.generate(catalog, new InsertionPlanner(null));

		assertEquals(2, candidates.size());
		assertInstanceOf(CandidateKind.Table.class, candidates.get(0).kind());
		assertEquals("table - 10 rows", candidates.get(0).detail());
		assertEquals(List.of("table", "10 rows"), candidates.get(0).doc().badges());
		assertInstanceOf(CandidateKind.View.class, candidates.get(1).kind());
		assertEquals("view", candidates.get(1).detail());
	}

	@Test
	void tableGeneratorHandlesEmptyInputsAndQuoting() {
		Catalog catalog = Catalog.builder()
				.table("order detail", t -> { })
				.build();

		assertTrue(TableGenerator.generate(null, null).isEmpty());
		assertTrue(TableGenerator.generate(Catalog.builder().build(), null).isEmpty());
		assertEquals("\"order detail\"", TableGenerator.generate(catalog, null).get(0).insertText());
	}

	@Test
	void keywordGeneratorUsesContextSpecificFollowSets() {
		assertEquals(List.of("select", "with", "insert", "update", "delete"),
				KeywordGenerator.generate(new Context.StatementHead(""), null).stream()
						.map(Candidate::display)
						.toList());
		assertEquals(List.of("from", "where", "join", "inner join", "left join", "right join", "full join",
						"cross join", "group by", "order by", "having", "limit", "union", "on"),
				KeywordGenerator.generate(new Context.ExpressionRef(""), null).stream()
						.map(Candidate::display)
						.toList());
		assertEquals(List.of("and", "or", "not", "null", "case"),
				KeywordGenerator.generate(new Context.ColumnRef(""), null).stream()
						.map(Candidate::display)
						.toList());
		assertEquals(List.of("null", "true", "false"),
				KeywordGenerator.generate(new Context.ValueLiteral(""), null).stream()
						.map(Candidate::display)
						.toList());
		assertEquals(List.of("select"),
				KeywordGenerator.generate(new Context.TableRef(""), null).stream()
						.map(Candidate::display)
						.toList());
		assertEquals(List.of("select"),
				KeywordGenerator.generate(new Context.JoinTarget(""), null).stream()
						.map(Candidate::display)
						.toList());
		assertTrue(KeywordGenerator.generate(new Context.Quiet("x"), null).isEmpty());
	}

	@Test
	void keywordGeneratorMirrorsTypedPrefixCase() {
		Candidate candidate = KeywordGenerator.generate(new Context.StatementHead("SEL"), null).get(0);

		assertEquals("select", candidate.display());
		assertEquals("SELECT", candidate.insertText());
		assertInstanceOf(CandidateKind.Keyword.class, candidate.kind());
	}

	@Test
	void candidateGeneratorCombinesOnlyRelevantSources() {
		Catalog catalog = Catalog.builder()
				.table("appointment", t -> { })
				.build();
		ResolvedScope scope = ScopeResolver.resolveStatement("select | from appointment a", null);

		assertEquals(2, CandidateGenerator.generate(catalog, new Context.TableRef(""), null).size());
		assertEquals(2, CandidateGenerator.generate(catalog, new Context.JoinTarget(""), null).size());
		assertEquals(5, CandidateGenerator.generate(catalog, new Context.StatementHead(""), null).size());
		assertTrue(CandidateGenerator.generate(catalog, scope, new Context.ColumnRef(""), null).stream()
				.anyMatch(candidate -> candidate.kind() instanceof CandidateKind.Alias));
		assertTrue(CandidateGenerator.generate(catalog, scope, new Context.ColumnRef(""), null).stream()
				.anyMatch(candidate -> candidate.kind() instanceof CandidateKind.Expansion));
		assertTrue(CandidateGenerator.generate(catalog, new Context.Quiet("x"), null).isEmpty());
	}

	@Test
	void columnGeneratorUsesVisibleScopeSources() {
		Catalog catalog = dentalCatalog();
		ResolvedScope scope = ScopeResolver.resolveStatement("select | from patient p", null);

		List<Candidate> candidates = ColumnGenerator.generate(catalog, scope, new Context.ColumnRef(""), null);

		assertEquals(List.of("id", "first_name", "email"),
				candidates.stream().map(Candidate::display).toList());
		assertEquals("patient - varchar", candidates.get(1).detail());
		assertEquals("patient.first_name", candidates.get(1).doc().qualifiedName());
		assertEquals(List.of("sensitive"), candidates.get(2).doc().badges());
	}

	@Test
	void columnGeneratorFiltersQualifiedAliasSources() {
		Catalog catalog = dentalCatalog();
		ResolvedScope scope = ScopeResolver.resolveStatement("select p.| from patient p join appointment a on true",
				null);

		List<Candidate> candidates = ColumnGenerator.generate(catalog, scope, new Context.ColumnRef("", "p"),
				new InsertionPlanner(null));

		assertEquals(List.of("id", "first_name", "email"),
				candidates.stream().map(Candidate::display).toList());
		assertEquals("first_name", candidates.get(1).insertText());
	}

	@Test
	void columnGeneratorQualifiesDisplayWhenMultipleSourcesAreVisible() {
		Catalog catalog = dentalCatalog();
		ResolvedScope scope = ScopeResolver.resolveStatement("select | from patient p join appointment a on true",
				null);

		List<Candidate> candidates = ColumnGenerator.generate(catalog, scope, new Context.ColumnRef(""), null);

		assertTrue(candidates.stream().anyMatch(candidate -> candidate.display().equals("p.first_name")));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.display().equals("a.status")));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.doc().badges().contains("fk")));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.doc().referencedTable().equals("patient")));
	}

	@Test
	void columnGeneratorFallsBackToCatalogWithoutVisibleSources() {
		List<Candidate> candidates = ColumnGenerator.generate(dentalCatalog(), ResolvedScope.empty(),
				new Context.ColumnRef(""), null);

		assertTrue(candidates.stream().anyMatch(candidate -> candidate.display().equals("first_name")));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.display().equals("status")));
	}

	@Test
	void columnGeneratorSupportsQualifiedTableNamesAndCteColumns() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer"))
				.build();
		ResolvedScope qualifiedScope = ScopeResolver.resolveStatement("select admin.patient.| from admin.patient",
				null);
		ResolvedScope cteScope = ScopeResolver.resolveStatement("""
				with recent(id, patient_id) as (select id, patient_id from appointment)
				select r.| from recent r
				""", null);

		assertEquals("id", ColumnGenerator.generate(catalog, qualifiedScope, new Context.ColumnRef("", "admin.patient"),
				null).get(0).display());
		assertEquals(List.of("id", "patient_id"), ColumnGenerator.generate(null, cteScope,
				new Context.ColumnRef("", "r"), null).stream().map(Candidate::display).toList());
	}

	@Test
	void aliasGeneratorUsesVisibleSourceQualifiers() {
		ResolvedScope scope = ScopeResolver.resolveStatement("select | from patient p join appointment a on true",
				null);

		List<Candidate> candidates = AliasGenerator.generate(scope, new Context.ColumnRef(""));

		assertEquals(List.of("p", "a"), candidates.stream().map(Candidate::display).toList());
		assertEquals("alias for patient", candidates.get(0).detail());
		assertEquals(List.of("alias"), candidates.get(0).doc().badges());
		assertEquals("patient", candidates.get(0).doc().referencedTable());
		assertTrue(AliasGenerator.generate(scope, new Context.ColumnRef("", "p")).isEmpty());
		assertTrue(AliasGenerator.generate(ResolvedScope.empty(), new Context.ColumnRef("")).isEmpty());
		assertTrue(AliasGenerator.generate(null, new Context.ColumnRef("")).isEmpty());
		assertTrue(AliasGenerator.generate(scope, new Context.TableRef("")).isEmpty());
	}

	@Test
	void expansionGeneratorCreatesGlobalAndQualifiedStars() {
		ResolvedScope scope = ScopeResolver.resolveStatement("select | from patient p join appointment a on true",
				null);

		List<Candidate> candidates = ExpansionGenerator.generate(scope, new Context.ColumnRef(""));

		assertEquals(List.of("*", "p.*", "a.*"), candidates.stream().map(Candidate::display).toList());
		assertEquals("*", candidates.get(0).insertText());
		assertEquals("p.*", candidates.get(1).insertText());
		assertEquals(List.of("star"), candidates.get(1).doc().badges());
		List<Candidate> qualified = ExpansionGenerator.generate(scope, new Context.ColumnRef("", "p"));
		assertEquals(List.of("p.*"), qualified.stream().map(Candidate::display).toList());
		assertEquals(List.of("*"), qualified.stream().map(Candidate::insertText).toList());
		assertEquals(List.of("*"), ExpansionGenerator.generate(ResolvedScope.empty(), new Context.ColumnRef("")).stream()
				.map(Candidate::insertText).toList());
		assertEquals(List.of("*"), ExpansionGenerator.generate(null, new Context.ExpressionRef("")).stream()
				.map(Candidate::insertText).toList());
		assertTrue(ExpansionGenerator.generate(scope, new Context.TableRef("")).isEmpty());
	}

	@Test
	void expansionGeneratorCreatesExplicitColumnListExpansions() {
		Catalog catalog = dentalCatalog();
		ResolvedScope scope = ScopeResolver.resolveStatement("select | from patient p join appointment a on true",
				null);
		ResolvedScope cteScope = ScopeResolver.resolveStatement("""
				with recent(id, patient_id) as (select id, patient_id from appointment)
				select r.| from recent r
				""", null);

		List<Candidate> candidates = ExpansionGenerator.generate(catalog, scope, new Context.ColumnRef(""),
				new InsertionPlanner(null));
		List<Candidate> qualified = ExpansionGenerator.generate(catalog, scope, new Context.ColumnRef("", "p"),
				new InsertionPlanner(null));
		List<Candidate> cteQualified = ExpansionGenerator.generate(null, cteScope, new Context.ColumnRef("", "r"),
				new InsertionPlanner(null));

		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.display().equals("p.id, p.first_name, p.email")
						&& candidate.insertText().equals("p.id, p.first_name, p.email")
						&& candidate.doc().badges().equals(List.of("list"))));
		assertEquals(List.of("p.*", "p.id, p.first_name, p.email"),
				qualified.stream().map(Candidate::display).toList());
		assertEquals(List.of("*", "id, p.first_name, p.email"),
				qualified.stream().map(Candidate::insertText).toList());
		assertEquals(List.of("r.*", "r.id, r.patient_id"),
				cteQualified.stream().map(Candidate::display).toList());
		assertEquals(List.of("*", "id, r.patient_id"),
				cteQualified.stream().map(Candidate::insertText).toList());
	}

	@Test
	void expansionGeneratorHandlesQualifiedSourceFallbacksAndEmptyColumns() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer"))
				.table("core.invoice", t -> t.column("id", "integer"))
				.table("empty_table", t -> { })
				.build();
		ResolvedScope unqualifiedCatalogScope = ScopeResolver.resolveStatement("select | from admin.patient p",
				null);
		ResolvedScope lastPartCatalogScope = ScopeResolver.resolveStatement("select | from billing.invoice i",
				null);
		ResolvedScope emptyScope = ScopeResolver.resolveStatement("select e.| from empty_table e", null);

		assertTrue(ExpansionGenerator.generate(catalog, unqualifiedCatalogScope, new Context.ColumnRef(""),
				null).stream().anyMatch(candidate -> candidate.display().equals("p.id")));
		assertTrue(ExpansionGenerator.generate(catalog, lastPartCatalogScope, new Context.ColumnRef(""),
				null).stream().anyMatch(candidate -> candidate.display().equals("i.id")));
		assertEquals(List.of("x.*"), ExpansionGenerator.generate(catalog, null, new Context.ColumnRef("", "x"),
				null).stream().map(Candidate::display).toList());
		assertEquals(List.of("e.*"), ExpansionGenerator.generate(catalog, emptyScope,
				new Context.ColumnRef("", "e"), null).stream().map(Candidate::display).toList());
		assertTrue(ExpansionGenerator.generate(catalog, unqualifiedCatalogScope, new Context.ValueLiteral(""),
				null).isEmpty());
		assertEquals(List.of("*", "m.*"), ExpansionGenerator.generate(Catalog.builder().build(),
				new ResolvedScope(0, false, List.of(new ScopeSource("missing", "m", SourceKind.TABLE, 0, 7, 0)),
						List.of(), List.of(), List.of()),
				new Context.GroupByRef(""), null).stream().map(Candidate::display).toList());
		assertEquals(List.of("*"), ExpansionGenerator.generate(catalog,
				new ResolvedScope(0, false, List.of(new ScopeSource("", "", SourceKind.TABLE, 0, 0, 0)),
						List.of(), List.of(), List.of()),
				new Context.OrderByRef(""), null).stream().map(Candidate::display).toList());
	}

	@Test
	void groupByGeneratorBackfillsSelectExpressions() {
		List<Candidate> candidates = GroupByGenerator.generate(new Context.GroupByRef("",
				List.of("p.first_name", "p.email", "p.first_name", "")));

		assertEquals(1, candidates.size());
		assertEquals("p.first_name, p.email", candidates.get(0).insertText());
		assertEquals("group by select expressions", candidates.get(0).detail());
		assertEquals(List.of("group by", "2 expressions"), candidates.get(0).doc().badges());
		assertInstanceOf(CandidateKind.Scaffold.class, candidates.get(0).kind());
		assertEquals(List.of("group by", "1 expression"), GroupByGenerator.generate(
				new Context.GroupByRef("", List.of("p.id"))).get(0).doc().badges());
		assertTrue(GroupByGenerator.generate(new Context.GroupByRef("", List.of("", "   "))).isEmpty());
		assertTrue(GroupByGenerator.generate(new Context.GroupByRef("")).isEmpty());
		assertTrue(GroupByGenerator.generate(new Context.ColumnRef("")).isEmpty());
	}

	@Test
	void joinGeneratorCreatesChildToParentJoinTargets() {
		Catalog catalog = dentalCatalog();
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from appointment a join |", null);

		List<Candidate> candidates = JoinGenerator.generateJoinTargets(catalog, scope, new InsertionPlanner(null));

		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.insertText().equals("patient p ON p.id = a.patient_id")
						&& candidate.kind() instanceof CandidateKind.JoinClause clause
						&& clause.provenance() == Provenance.DECLARED
						&& candidate.doc().badges().equals(List.of("join", "declared"))));
	}

	@Test
	void joinGeneratorCreatesParentToChildJoinTargetsAndAvoidsAliasCollisions() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("appointment", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("patient_id", "integer",
								c -> c.referencing("patient", "id", Provenance.DECLARED)))
				.table("payment", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("patient_id", "integer",
								c -> c.referencing("patient", "id", Provenance.INFERRED)))
				.build();
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from patient p join |", null);

		List<Candidate> candidates = JoinGenerator.generateJoinTargets(catalog, scope, new InsertionPlanner(null));

		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.insertText().equals("appointment a ON a.patient_id = p.id")));
		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.insertText().equals("payment p2 ON p2.patient_id = p.id")
						&& candidate.detail().startsWith("inferred join")));
	}

	@Test
	void joinGeneratorCreatesJunctionPathCandidates() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("provider", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("patient_provider", t -> t
						.column("patient_id", "integer", c -> c.primaryKey()
								.referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer", c -> c.primaryKey()
								.referencing("provider", "id", Provenance.DECLARED)))
				.build();
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from patient p join pro", null);

		List<Candidate> candidates = JoinGenerator.generateJoinTargets(catalog, scope, new InsertionPlanner(null));

		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("patient_provider")
						&& candidate.display().equals("provider p2 via patient_provider")
						&& candidate.insertText().equals("patient_provider pp ON pp.patient_id = p.id "
								+ "JOIN provider p2 ON p2.id = pp.provider_id")
						&& candidate.doc().badges().equals(List.of("join path", "junction"))));
	}

	@Test
	void joinGeneratorHonorsJunctionOverridesAndVisibleTargets() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("provider", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("location", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("forced_bridge", t -> t
						.junction(true)
						.column("patient_id", "integer", c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer",
								c -> c.referencing("provider", "id", Provenance.DECLARED)))
				.table("forced_one_fk", t -> t
						.junction(true)
						.column("patient_id", "integer", c -> c.referencing("patient", "id", Provenance.DECLARED)))
				.table("missing_bridge", t -> t
						.junction(true)
						.column("patient_id", "integer", c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("missing_id", "integer",
								c -> c.referencing("missing_provider", "id", Provenance.DECLARED)))
				.table("disabled_bridge", t -> t
						.junction(false)
						.column("patient_id", "integer", c -> c.primaryKey()
								.referencing("patient", "id", Provenance.DECLARED))
						.column("location_id", "integer", c -> c.primaryKey()
								.referencing("location", "id", Provenance.DECLARED)))
				.table("patient_provider_note", t -> t
						.column("patient_id", "integer", c -> c.primaryKey()
								.referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer", c -> c.primaryKey()
								.referencing("provider", "id", Provenance.DECLARED))
						.column("note", "varchar"))
				.table("non_covering_bridge", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("patient_id", "integer", c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer",
								c -> c.referencing("provider", "id", Provenance.DECLARED)))
				.build();
		ResolvedScope patientScope = ScopeResolver.resolveStatement("select * from patient p join |", null);
		ResolvedScope patientProviderScope = ScopeResolver.resolveStatement(
				"select * from patient p join provider pr on true join |", null);

		List<Candidate> candidates = JoinGenerator.generateJoinTargets(catalog, patientScope, null);
		List<Candidate> visibleTargetCandidates = JoinGenerator.generateJoinTargets(catalog, patientProviderScope,
				null);

		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("forced_bridge")
						&& candidate.insertText().equals("forced_bridge fb ON fb.patient_id = p.id "
								+ "JOIN provider p2 ON p2.id = fb.provider_id")));
		assertTrue(candidates.stream()
				.noneMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("disabled_bridge")));
		assertTrue(candidates.stream()
				.noneMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("forced_one_fk")));
		assertTrue(candidates.stream()
				.noneMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("missing_bridge")));
		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("patient_provider_note")));
		assertTrue(candidates.stream()
				.noneMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("non_covering_bridge")));
		assertTrue(visibleTargetCandidates.stream()
				.noneMatch(candidate -> candidate.kind() instanceof CandidateKind.JoinPath path
						&& path.via().equals("forced_bridge")));
	}

	@Test
	void joinGeneratorCreatesOnPredicatesBetweenVisibleSources() {
		Catalog catalog = dentalCatalog();
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from appointment a join patient p on |",
				null);

		List<Candidate> candidates = JoinGenerator.generateOnConditions(catalog, scope, new InsertionPlanner(null));

		assertEquals(List.of("p.id = a.patient_id"), candidates.stream().map(Candidate::insertText).toList());
		assertInstanceOf(CandidateKind.JoinClause.class, candidates.get(0).kind());
	}

	@Test
	void joinGeneratorHandlesEmptyInputsMissingReferencesAndDefaultPlanners() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("order detail", t -> t.column("patient_id", "integer",
						c -> c.referencing("patient", "id", Provenance.DECLARED)))
				.table("3rd_party", t -> t.column("patient_id", "integer",
						c -> c.referencing("patient", "id", Provenance.DECLARED)))
				.table("", t -> t.column("patient_id", "integer",
						c -> c.referencing("patient", "id", Provenance.DECLARED)))
				.table("invoice", t -> t.column("patient_id", "integer",
						c -> c.referencing("missing_patient", "id", Provenance.INFERRED)))
				.build();
		ResolvedScope patientScope = ScopeResolver.resolveStatement("select * from patient p join |", null);
		ResolvedScope invoiceScope = ScopeResolver.resolveStatement("select * from invoice i join |", null);

		assertTrue(JoinGenerator.generateJoinTargets(null, patientScope, null).isEmpty());
		assertTrue(JoinGenerator.generateJoinTargets(Catalog.builder().build(), patientScope, null).isEmpty());
		assertTrue(JoinGenerator.generateJoinTargets(catalog, null, null).isEmpty());
		assertTrue(JoinGenerator.generateJoinTargets(catalog, ResolvedScope.empty(), null).isEmpty());
		assertTrue(JoinGenerator.generateJoinTargets(catalog, invoiceScope, null).isEmpty());

		List<Candidate> candidates = JoinGenerator.generateJoinTargets(catalog, patientScope, null);

		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.insertText().equals("\"order detail\" od ON od.patient_id = p.id")));
		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.insertText().equals("\"3rd_party\" t3p ON t3p.patient_id = p.id")));
		assertTrue(candidates.stream()
				.anyMatch(candidate -> candidate.insertText().equals("\"\" t ON t.patient_id = p.id")));
	}

	@Test
	void joinGeneratorHandlesOnConditionEmptyInputsAndQualifiedNameFallbacks() {
		Catalog catalog = Catalog.builder()
				.table("crm.patient", t -> t.column("id", "integer", c -> c.primaryKey()))
				.table("appointment", t -> t.column("patient_id", "integer",
						c -> c.referencing("public.patient", "id", Provenance.INFERRED)))
				.build();
		ResolvedScope singleSource = ScopeResolver.resolveStatement("select * from crm.patient p where |", null);
		ResolvedScope joined = ScopeResolver.resolveStatement("select * from appointment a join crm.patient p on |",
				null);

		assertTrue(JoinGenerator.generateOnConditions(null, joined, null).isEmpty());
		assertTrue(JoinGenerator.generateOnConditions(Catalog.builder().build(), joined, null).isEmpty());
		assertTrue(JoinGenerator.generateOnConditions(catalog, null, null).isEmpty());
		assertTrue(JoinGenerator.generateOnConditions(catalog, singleSource, null).isEmpty());

		assertEquals(List.of("p.id = a.patient_id"), JoinGenerator.generateOnConditions(catalog, joined, null)
				.stream().map(Candidate::insertText).toList());
		assertEquals(List.of("\"crm.patient\" cp ON cp.id = a.patient_id"),
				JoinGenerator.generateJoinTargets(catalog,
						ScopeResolver.resolveStatement("select * from appointment a join |", null),
						null)
						.stream().map(Candidate::insertText).toList());
		assertTrue(JoinGenerator.generateJoinTargets(dentalCatalog(),
				ScopeResolver.resolveStatement("select * from crm.patient p join appointment a on true join |",
						null),
				null).isEmpty());
	}

	@Test
	void insertGeneratorCreatesScaffoldAfterTargetAndOpenParen() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("first_name", "varchar")
						.column("updated_at", "timestamp", c -> c.role(ColumnRole.SYSTEM)))
				.build();
		ResolvedScope afterTarget = ScopeResolver.resolveStatement("insert into patient ", null);
		ResolvedScope afterOpenParen = ScopeResolver.resolveStatement("insert into patient (", null);

		Candidate targetCandidate = InsertGenerator.generate(catalog, afterTarget,
				new Context.InsertColumns("", false), new InsertionPlanner(null)).get(0);
		Candidate parenCandidate = InsertGenerator.generate(catalog, afterOpenParen,
				new Context.InsertColumns("", true), new InsertionPlanner(null)).get(0);

		assertEquals("(id, first_name) values (?, ?)", targetCandidate.insertText());
		assertEquals(targetCandidate.insertText().indexOf('?'), targetCandidate.caretAfter());
		assertEquals("id, first_name) values (?, ?)", parenCandidate.insertText());
		assertInstanceOf(CandidateKind.Scaffold.class, targetCandidate.kind());
		assertEquals(List.of("insert", "2 columns"), targetCandidate.doc().badges());
	}

	@Test
	void insertGeneratorQuotesIdentifiersAndHandlesDefensivePaths() {
		Catalog catalog = Catalog.builder()
				.table("crm.patient", t -> t.column("full name", "varchar"))
				.table("audit", t -> t.column("created_at", "timestamp", c -> c.role(ColumnRole.SYSTEM)))
				.build();
		ResolvedScope patientScope = ScopeResolver.resolveStatement("insert into patient ", null);
		ResolvedScope auditScope = ScopeResolver.resolveStatement("insert into audit ", null);

		assertTrue(InsertGenerator.generate(null, patientScope, new Context.InsertColumns(""), null).isEmpty());
		assertTrue(InsertGenerator.generate(Catalog.builder().build(), patientScope, new Context.InsertColumns(""),
				null).isEmpty());
		assertTrue(InsertGenerator.generate(catalog, null, new Context.InsertColumns(""), null).isEmpty());
		assertTrue(InsertGenerator.generate(catalog, patientScope, new Context.ColumnRef(""), null).isEmpty());
		assertTrue(InsertGenerator.generate(catalog, auditScope, new Context.InsertColumns(""), null).isEmpty());
		assertTrue(InsertGenerator.generate(dentalCatalog(),
				ScopeResolver.resolveStatement("insert into missing_table ", null),
				new Context.InsertColumns(""), null).isEmpty());

		Candidate candidate = InsertGenerator.generate(catalog, patientScope, new Context.InsertColumns(""), null)
				.get(0);

		assertEquals("(full name) values (?)", candidate.display());
		assertEquals("(\"full name\") values (?)", candidate.insertText());
		assertEquals(List.of("insert", "1 column"), candidate.doc().badges());
	}

	@Test
	void valueGeneratorCreatesProfiledTextValuesForComparisonColumn() {
		Catalog catalog = dentalCatalog();
		Profiles profiles = profile("appointment", "status",
				List.of(new ValueShare("open", 0.62, false), new ValueShare("can't", 0.05, true)));
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from appointment a where status = |", null);

		List<Candidate> candidates = ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "", "status"));

		assertEquals(List.of("open", "can't"), candidates.stream().map(Candidate::display).toList());
		assertEquals(List.of("'open'", "'can''t'"), candidates.stream().map(Candidate::insertText).toList());
		assertInstanceOf(CandidateKind.Value.class, candidates.get(0).kind());
		assertEquals("appointment.status - 62%", candidates.get(0).detail());
		assertEquals(List.of("value", "62%"), candidates.get(0).doc().badges());
		assertEquals(2L, candidates.get(0).doc().distinctCount());
		assertEquals(0.10, candidates.get(0).doc().nullFraction());
		assertEquals(List.of("value", "5%", "approx"), candidates.get(1).doc().badges());
	}

	@Test
	void valueGeneratorHandlesQualifiedNumericAndDefensivePaths() {
		Catalog catalog = Catalog.builder()
				.table("crm.appointment", t -> t
						.column("status", "varchar")
						.column("score", "integer", c -> c.typeFamily(TypeFamily.INTEGER))
						.column("active", "varchar", c -> c.typeFamily(TypeFamily.BOOLEAN)))
				.table("patient", t -> t.column("status", "varchar"))
				.build();
		Profiles profiles = new Profiles() {
			@Override
			public List<ValueShare> values(String tableName, String columnName) {
				if (tableName.equalsIgnoreCase("appointment") && columnName.equalsIgnoreCase("score")) {
					return List.of(new ValueShare("10", 0.75, false));
				}
				if (tableName.equalsIgnoreCase("appointment") && columnName.equalsIgnoreCase("active")) {
					return List.of(new ValueShare("true", 0.85, false));
				}
				return List.of();
			}

			@Override
			public OptionalLong distinctCount(String tableName, String columnName) {
				return tableName.equalsIgnoreCase("appointment") && columnName.equalsIgnoreCase("score")
						? OptionalLong.of(3) : OptionalLong.empty();
			}

			@Override
			public OptionalDouble nullFraction(String tableName, String columnName) {
				return tableName.equalsIgnoreCase("appointment") && columnName.equalsIgnoreCase("score")
						? OptionalDouble.of(0.25) : OptionalDouble.empty();
			}
		};
		ResolvedScope scope = ScopeResolver.resolveStatement(
				"select * from crm.appointment a join patient p on true where a.score = |", null);

		assertTrue(ValueGenerator.generate(null, profiles, scope, new Context.ValueLiteral("", "a", "score"))
				.isEmpty());
		assertTrue(ValueGenerator.generate(Catalog.builder().build(), profiles, scope,
				new Context.ValueLiteral("", "a", "score")).isEmpty());
		assertTrue(ValueGenerator.generate(catalog, null, scope, new Context.ValueLiteral("", "a", "score"))
				.isEmpty());
		assertTrue(ValueGenerator.generate(catalog, profiles, scope, new Context.ColumnRef("")).isEmpty());
		assertTrue(ValueGenerator.generate(catalog, profiles, scope, new Context.ValueLiteral("", "", "")).isEmpty());
		assertTrue(ValueGenerator.generate(catalog, Profiles.empty(), scope,
				new Context.ValueLiteral("", "a", "score")).isEmpty());
		assertTrue(ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "missing", "score")).isEmpty());

		List<Candidate> candidates = ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "a", "score"));

		assertEquals(List.of("10"), candidates.stream().map(Candidate::insertText).toList());
		assertEquals("crm.appointment.score", candidates.get(0).doc().qualifiedName());
		assertEquals(3L, candidates.get(0).doc().distinctCount());
		assertEquals(0.25, candidates.get(0).doc().nullFraction());
		assertEquals(List.of("true"), ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "a", "active")).stream().map(Candidate::insertText).toList());
	}

	@Test
	void valueGeneratorHandlesFallbacksDeduplicationAndRawLiteralInference() {
		Catalog catalog = Catalog.builder()
				.table("crm.appointment", t -> t
						.column("status", "varchar")
						.column("score", "numeric"))
				.table("patient", t -> t.column("status", "varchar"))
				.table("feature_flag", t -> t.column("enabled", "bool"))
				.table("invoice", t -> t.column("amount", "decimal", c -> c.typeFamily(TypeFamily.DECIMAL)))
				.build();
		Profiles profiles = new Profiles() {
			@Override
			public List<ValueShare> values(String tableName, String columnName) {
				if (tableName.equalsIgnoreCase("appointment") && columnName.equalsIgnoreCase("status")) {
					return List.of(new ValueShare("open", 0.50, false));
				}
				if (tableName.equalsIgnoreCase("patient") && columnName.equalsIgnoreCase("status")) {
					return List.of(new ValueShare("open", 0.25, false));
				}
				if (columnName.equalsIgnoreCase("score")) {
					return List.of(new ValueShare("12.5", 0.40, false));
				}
				if (columnName.equalsIgnoreCase("enabled")) {
					return List.of(new ValueShare("true", 0.90, false));
				}
				if (columnName.equalsIgnoreCase("amount")) {
					return List.of(new ValueShare("19.99", 0.30, false));
				}
				return List.of();
			}
		};
		ResolvedScope appointmentScope = ScopeResolver.resolveStatement("select * from appointment a where status = |",
				null);
		ResolvedScope qualifiedScope = ScopeResolver.resolveStatement(
				"select * from crm.appointment a where score = |", null);

		assertEquals(List.of("'open'"), ValueGenerator.generate(catalog, profiles, null,
				new Context.ValueLiteral("", "", "status")).stream().map(Candidate::insertText).toList());
		assertEquals(List.of("'open'"), ValueGenerator.generate(catalog, profiles, ResolvedScope.empty(),
				new Context.ValueLiteral("", "", "status")).stream().map(Candidate::insertText).toList());
		assertEquals(List.of("'open'"), ValueGenerator.generate(catalog, profiles, null,
				new Context.ValueLiteral("", "crm.appointment", "status")).stream()
				.map(Candidate::insertText).toList());
		assertEquals(List.of("'open'"), ValueGenerator.generate(catalog, profiles, appointmentScope,
				new Context.ValueLiteral("", "", "status")).stream().map(Candidate::insertText).toList());
		assertEquals(List.of("12.5"), ValueGenerator.generate(catalog, profiles, qualifiedScope,
				new Context.ValueLiteral("", "a", "score")).stream().map(Candidate::insertText).toList());
		assertEquals(List.of("true"), ValueGenerator.generate(catalog, profiles,
				ScopeResolver.resolveStatement("select * from feature_flag f where enabled = |", null),
				new Context.ValueLiteral("", "", "enabled")).stream().map(Candidate::insertText).toList());
		assertEquals(List.of("19.99"), ValueGenerator.generate(catalog, profiles,
				ScopeResolver.resolveStatement("select * from invoice i where amount = |", null),
				new Context.ValueLiteral("", "", "amount")).stream().map(Candidate::insertText).toList());
		assertTrue(ValueGenerator.generate(dentalCatalog(), Profiles.empty(),
				ScopeResolver.resolveStatement("select * from appointment a where status = |", null),
				new Context.ValueLiteral("", "", "status")).isEmpty());
		assertEquals(List.of("'kept'"), ValueGenerator.generate(Catalog.builder()
						.table("appointment", t -> t.column("status", "varchar"))
						.build(), profile("appointment", "status", List.of(new ValueShare("kept", 0.10, false))),
				ScopeResolver.resolveStatement("select * from crm.appointment a where status = |", null),
				new Context.ValueLiteral("", "", "status")).stream().map(Candidate::insertText).toList());
	}

	@Test
	void valueGeneratorAppliesProfileSafetyGates() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t
						.column("status", "varchar")
						.column("email", "varchar", c -> c.role(ColumnRole.SENSITIVE))
						.column("country", "varchar"))
				.build();
		List<ValueShare> manyValues = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			manyValues.add(new ValueShare("v" + i, 0.01, false));
		}
		ProfileSnapshot profiles = Profiles.builder()
				.values("patient", "status", List.of(new ValueShare("open", 0.40, false)))
				.distinctCount("patient", "status", 51)
				.values("patient", "email", List.of(new ValueShare("a@example.test", 0.20, false)))
				.values("patient", "country", manyValues)
				.distinctCount("patient", "country", 20)
				.build();
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from patient p", null);

		assertTrue(ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "", "status")).isEmpty());
		assertTrue(ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "", "email")).isEmpty());
		assertEquals(15, ValueGenerator.generate(catalog, profiles, scope,
				new Context.ValueLiteral("", "", "country")).size());
	}

	private static Catalog dentalCatalog() {
		return Catalog.builder()
				.table("patient", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("first_name", "varchar")
						.column("email", "varchar", c -> c.role(ColumnRole.SENSITIVE)))
				.table("appointment", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("patient_id", "integer",
								c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("status", "varchar"))
				.build();
	}

	private static Profiles profile(String table, String column, List<ValueShare> values) {
		return new Profiles() {
			@Override
			public List<ValueShare> values(String tableName, String columnName) {
				return tableName.equalsIgnoreCase(table) && columnName.equalsIgnoreCase(column)
						? values : List.of();
			}

			@Override
			public OptionalLong distinctCount(String tableName, String columnName) {
				return tableName.equalsIgnoreCase(table) && columnName.equalsIgnoreCase(column)
						? OptionalLong.of(values.size()) : OptionalLong.empty();
			}

			@Override
			public OptionalDouble nullFraction(String tableName, String columnName) {
				return tableName.equalsIgnoreCase(table) && columnName.equalsIgnoreCase(column)
						? OptionalDouble.of(0.10) : OptionalDouble.empty();
			}
		};
	}
}
