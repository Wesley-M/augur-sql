package io.github.wesleym.augur.scope;

import io.github.wesleym.augur.Dialects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression tests for comma-separated (implicit cross join) FROM lists. */
class CommaSourcesTest {

	@Test
	void resolvesTwoTableCommaListWithAlias() {
		ResolvedScope scope = ScopeResolver.resolveStatement("select * from patient, appointment a", Dialects.ANSI);

		assertEquals(2, scope.sources().size());
		assertSource(scope.source("patient").orElseThrow(), "patient", "patient");
		assertSource(scope.source("a").orElseThrow(), "appointment", "a");
	}

	@Test
	void resolvesThreeTableCommaList() {
		ResolvedScope scope = ScopeResolver.resolveStatement(
				"select * from patient p, provider v, room r where p.id = 1", Dialects.ANSI);

		assertEquals(3, scope.sources().size());
		assertSource(scope.source("p").orElseThrow(), "patient", "p");
		assertSource(scope.source("v").orElseThrow(), "provider", "v");
		assertSource(scope.source("r").orElseThrow(), "room", "r");
	}

	@Test
	void commaListStillSupportsTrailingJoin() {
		ResolvedScope scope = ScopeResolver.resolveStatement(
				"select * from patient p, provider v join room r on r.id = 1", Dialects.ANSI);

		assertEquals(3, scope.sources().size());
		assertSource(scope.source("p").orElseThrow(), "patient", "p");
		assertSource(scope.source("v").orElseThrow(), "provider", "v");
		assertSource(scope.source("r").orElseThrow(), "room", "r");
	}

	private static void assertSource(ScopeSource source, String name, String alias) {
		assertEquals(name, source.name());
		assertEquals(alias, source.qualifier());
		assertEquals(SourceKind.TABLE, source.kind());
	}
}
