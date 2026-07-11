package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.CteBinding;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeSource;
import io.github.wesleym.augur.scope.SourceKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.wesleym.augur.gen.GeneratorSupport.effectivePlanner;
import static io.github.wesleym.augur.gen.GeneratorSupport.emptyCatalog;
import static io.github.wesleym.augur.gen.GeneratorSupport.key;
import static io.github.wesleym.augur.gen.GeneratorSupport.table;

/** Generates column candidates from catalog tables visible in the resolved scope. */
public final class ColumnGenerator {
	private ColumnGenerator() { }

	public static List<Candidate> generate(Catalog catalog, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		if (!columnContext(context)) {
			return List.of();
		}
		InsertionPlanner planner = effectivePlanner(insertion);
		String qualifier = qualifier(context);
		List<Candidate> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		if (!qualifier.isEmpty()) {
			addQualified(catalog, scope, qualifier, planner, out, seen);
		} else {
			addVisible(catalog, scope, planner, out, seen);
		}
		return List.copyOf(out);
	}

	private static void addQualified(Catalog catalog, ResolvedScope scope, String qualifier, InsertionPlanner planner,
			List<Candidate> out, Set<String> seen) {
		if (scope != null) {
			Optional<ScopeSource> sourceMatch = scope.source(qualifier);
			if (sourceMatch.isPresent()) {
				addSource(catalog, scope, sourceMatch.orElseThrow(), false, planner, out, seen);
				return;
			}
		}
		table(catalog, qualifier).ifPresent(table -> addTable(table, qualifier, false, planner, out, seen));
	}

	private static void addVisible(Catalog catalog, ResolvedScope scope, InsertionPlanner planner,
			List<Candidate> out, Set<String> seen) {
		if (scope == null || scope.visibleSources().isEmpty()) {
			if (emptyCatalog(catalog)) {
				return;
			}
			for (Catalog.Table table : catalog.tables()) {
				addTable(table, "", false, planner, out, seen);
			}
			return;
		}
		boolean qualifyDisplay = scope.visibleSources().size() > 1;
		for (ScopeSource source : scope.visibleSources()) {
			addSource(catalog, scope, source, qualifyDisplay, planner, out, seen);
		}
	}

	private static void addSource(Catalog catalog, ResolvedScope scope, ScopeSource source, boolean includeQualifier,
			InsertionPlanner planner, List<Candidate> out, Set<String> seen) {
		// A CTE binding shadows a same-named catalog table, so resolve it first.
		if (source.kind() == SourceKind.CTE && scope != null) {
			Optional<CteBinding> cte = scope.cte(source.name());
			if (cte.isPresent()) {
				addCte(cte.orElseThrow(), includeQualifier ? source.qualifier() : "", planner, out, seen);
				return;
			}
		}
		table(catalog, source.name()).ifPresent(table ->
				addTable(table, includeQualifier ? source.qualifier() : "", includeQualifier, planner, out, seen));
	}

	private static void addTable(Catalog.Table table, String qualifier, boolean qualifyDisplay,
			InsertionPlanner planner, List<Candidate> out, Set<String> seen) {
		for (Catalog.Column column : table.columns()) {
			String display = qualifyDisplay && !qualifier.isEmpty() ? qualifier + "." + column.name() : column.name();
			String insertText = planner.identifier(column.name());
			String seenKey = key(display) + "|" + key(insertText);
			if (seen.add(seenKey)) {
				out.add(new Candidate(new CandidateKind.Column(), display, detail(table, column), insertText,
						insertText.length(), null, doc(table, column)));
			}
		}
	}

	private static void addCte(CteBinding cte, String qualifier, InsertionPlanner planner, List<Candidate> out,
			Set<String> seen) {
		for (String column : cte.columns()) {
			String display = qualifier.isEmpty() ? column : qualifier + "." + column;
			String insertText = planner.identifier(column);
			String seenKey = key(display) + "|" + key(insertText);
			if (seen.add(seenKey)) {
				out.add(new Candidate(new CandidateKind.Column(), display, "cte column", insertText,
						insertText.length(), null,
						new CandidateDoc(cte.name() + "." + column, "", List.of("cte"), "", null, null, List.of())));
			}
		}
	}

	private static String detail(Catalog.Table table, Catalog.Column column) {
		String type = column.typeName().isEmpty() ? "column" : column.typeName();
		return table.name() + " - " + type;
	}

	private static CandidateDoc doc(Catalog.Table table, Catalog.Column column) {
		List<String> badges = new ArrayList<>();
		if (column.primaryKey()) {
			badges.add("pk");
		}
		if (column.foreignKey()) {
			badges.add("fk");
		}
		if (column.role() == ColumnRole.SENSITIVE) {
			badges.add("sensitive");
		} else if (column.role() == ColumnRole.SYSTEM) {
			badges.add("system");
		}
		String referencedTable = column.reference() == null ? "" : column.reference().table();
		return new CandidateDoc(table.name() + "." + column.name(), column.typeName(), badges, referencedTable, null,
				null, List.of());
	}

	private static boolean columnContext(Context context) {
		return context instanceof Context.ColumnRef || context instanceof Context.OnCondition
				|| context instanceof Context.GroupByRef || context instanceof Context.OrderByRef
				|| context instanceof Context.InsertColumns || context instanceof Context.SetAssignment;
	}

	private static String qualifier(Context context) {
		if (context instanceof Context.ColumnRef columnRef) {
			return columnRef.qualifier();
		}
		return "";
	}

}
