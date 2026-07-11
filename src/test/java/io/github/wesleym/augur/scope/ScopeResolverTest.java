package io.github.wesleym.augur.scope;

import io.github.wesleym.augur.Dialects;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeResolverTest {
	@Test
	void resolvesFromAndJoinAliasesAcrossWholeStatement() {
		ResolvedScope scope = ScopeResolver.resolveStatement(
				"select p.name, a.status from appointment a left join patient as p on p.id = a.patient_id",
				Dialects.ANSI);

		assertEquals(2, scope.sources().size());
		assertSource(scope.source("a").orElseThrow(), "appointment", "a", SourceKind.TABLE);
		assertSource(scope.source("p").orElseThrow(), "patient", "p", SourceKind.TABLE);
		assertEquals("a", scope.source("appointment").orElseThrow().qualifier());
		assertFalse(scope.truncated());
	}

	@Test
	void resolvesQualifiedAndQuotedSourceNames() {
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from admin.\"order\" o", Dialects.ANSI);

		assertSource(scope.source("o").orElseThrow(), "admin.order", "o", SourceKind.TABLE);
	}

	@Test
	void resolvesStatementTargets() {
		assertSource(ScopeResolver.resolveStatement("update patient p set first_name = 'A'", Dialects.ANSI)
				.source("p").orElseThrow(), "patient", "p", SourceKind.UPDATE_TARGET);
		assertSource(ScopeResolver.resolveStatement("delete from appointment a where a.id = 1", Dialects.ANSI)
				.source("a").orElseThrow(), "appointment", "a", SourceKind.DELETE_TARGET);
		assertSource(ScopeResolver.resolveStatement("insert into patient (id, first_name) values (1, 'A')",
				Dialects.ANSI).source("patient").orElseThrow(), "patient", "patient", SourceKind.INSERT_TARGET);
	}

	@Test
	void resolvesCteBindingsAndReferences() {
		ResolvedScope scope = ScopeResolver.resolveStatement("""
				with recent(id, patient_id) as (
				    select id, patient_id from appointment a
				), named as (
				    select * from recent r
				)
				select * from named n join patient p on p.id = n.patient_id
				""", Dialects.ANSI);

		CteBinding recent = scope.cte("RECENT").orElseThrow();
		CteBinding named = scope.cte("named").orElseThrow();

		assertEquals(List.of("id", "patient_id"), recent.columns());
		assertSource(recent.queryScope().source("a").orElseThrow(), "appointment", "a", SourceKind.TABLE);
		assertSource(named.queryScope().source("r").orElseThrow(), "recent", "r", SourceKind.CTE);
		assertSource(scope.source("n").orElseThrow(), "named", "n", SourceKind.CTE);
		assertSource(scope.source("p").orElseThrow(), "patient", "p", SourceKind.TABLE);
		assertEquals(2, scope.ctes().size());
		assertEquals(2, scope.children().size());
	}

	@Test
	void resolvesDerivedTablesAndTheirChildScopes() {
		ResolvedScope scope = ScopeResolver.resolveStatement("""
				select * from (select id from patient p) q
				join appointment a on a.patient_id = q.id
				""", Dialects.ANSI);

		assertSource(scope.source("q").orElseThrow(), "q", "q", SourceKind.DERIVED_TABLE);
		assertSource(scope.source("a").orElseThrow(), "appointment", "a", SourceKind.TABLE);
		assertEquals(1, scope.children().size());

		ResolvedScope child = scope.children().get(0);
		assertSource(child.source("p").orElseThrow(), "patient", "p", SourceKind.TABLE);
		assertSource(child.source("a").orElseThrow(), "appointment", "a", SourceKind.TABLE);
		assertEquals(2, child.inheritedSources().size());
	}

	@Test
	void childScopeLocalsShadowParentSources() {
		ResolvedScope scope = ScopeResolver.resolveStatement("""
				select * from patient p
				where exists (
				    select 1 from appointment p where p.patient_id = patient.id
				)
				""", Dialects.ANSI);

		ResolvedScope child = scope.children().get(0);

		assertSource(scope.source("p").orElseThrow(), "patient", "p", SourceKind.TABLE);
		assertSource(child.source("p").orElseThrow(), "appointment", "p", SourceKind.TABLE);
		assertSource(child.inheritedSources().get(0), "patient", "p", SourceKind.TABLE);
	}

	@Test
	void resolvesOnlyTheStatementAtTheCaret() {
		String sql = "select * from first f; select * from second s";

		ResolvedScope scope = ScopeResolver.resolve(sql, sql.length(), Dialects.ANSI);

		assertTrue(scope.source("f").isEmpty());
		assertSource(scope.source("s").orElseThrow(), "second", "s", SourceKind.TABLE);
	}

	@Test
	void appliesTheRecursionDepthCap() {
		ResolvedScope scope = new ScopeResolver(Dialects.ANSI, 0).resolveStatement(
				"select * from patient p where exists (select 1 from appointment a)");

		assertTrue(scope.truncated());
		assertTrue(scope.children().get(0).truncated());
		assertEquals(1, scope.children().get(0).depth());
	}

	@Test
	void toleratesBrokenSourcesWithoutHardFailure() {
		ResolvedScope empty = ScopeResolver.resolveStatement("select * from", Dialects.ANSI);
		ResolvedScope brokenCte = ScopeResolver.resolveStatement("with x as (select * from patient", Dialects.ANSI);
		ResolvedScope brokenDerived = ScopeResolver.resolveStatement("select * from (select * from patient p",
				Dialects.ANSI);

		assertTrue(empty.sources().isEmpty());
		assertEquals(1, brokenCte.ctes().size());
		assertSource(brokenCte.ctes().get(0).queryScope().source("patient").orElseThrow(), "patient", "patient",
				SourceKind.TABLE);
		assertEquals(SourceKind.DERIVED_TABLE, brokenDerived.sources().get(0).kind());
		assertEquals(1, brokenDerived.children().size());
	}

	@Test
	void validatesInputsAndNormalizesModels() {
		ScopeResolver resolver = new ScopeResolver(null, -1);
		ScopeSource source = new ScopeSource(null, null, null, 0, 0, -2);
		ResolvedScope scope = new ResolvedScope(-1, false, Arrays.asList(null, source), Arrays.asList(null, source),
				Arrays.asList(null, new CteBinding(null, Arrays.asList("id", null, ""), null, 0, 0)),
				Arrays.asList(null, ResolvedScope.empty()));

		assertEquals(0, resolver.resolveStatement("").depth());
		assertEquals("", source.name());
		assertSame(SourceKind.TABLE, source.kind());
		assertEquals(0, source.depth());
		assertTrue(source.matches(""));
		assertEquals(1, scope.sources().size());
		assertEquals(2, scope.visibleSources().size());
		assertEquals("", scope.ctes().get(0).name());
		assertEquals(List.of("id"), scope.ctes().get(0).columns());
		assertEquals(1, scope.children().size());
		assertThrows(IllegalArgumentException.class, () -> ScopeResolver.resolve("select", -1, Dialects.ANSI));
		assertThrows(IllegalArgumentException.class, () -> ScopeResolver.resolve("select", 7, Dialects.ANSI));
		assertThrows(IllegalArgumentException.class, () -> new ScopeSource("", "", SourceKind.TABLE, 2, 1, 0));
		assertThrows(IllegalArgumentException.class, () -> new CteBinding("", List.of(), ResolvedScope.empty(), 2, 1));
	}

	private static void assertSource(ScopeSource source, String name, String alias, SourceKind kind) {
		assertEquals(name, source.name());
		assertEquals(alias, source.alias());
		assertEquals(kind, source.kind());
	}
}
