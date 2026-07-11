package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
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
import java.util.stream.Collectors;

import static io.github.wesleym.augur.gen.GeneratorSupport.effectivePlanner;
import static io.github.wesleym.augur.gen.GeneratorSupport.key;
import static io.github.wesleym.augur.gen.GeneratorSupport.table;

/** Generates star expansion candidates. */
public final class ExpansionGenerator {
	private ExpansionGenerator() { }

	public static List<Candidate> generate(ResolvedScope scope, Context context) {
		return generate(null, scope, context, null);
	}

	public static List<Candidate> generate(Catalog catalog, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		if (!expansionContext(context)) {
			return List.of();
		}
		InsertionPlanner planner = effectivePlanner(insertion);
		if (context instanceof Context.ColumnRef columnRef && !columnRef.qualifier().isEmpty()) {
			return qualifiedExpansions(catalog, scope, columnRef.qualifier(), planner);
		}
		if (scope == null || scope.visibleSources().isEmpty()) {
			return List.of(expansion("*", "*", "all columns", ""));
		}
		List<Candidate> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		if (seen.add("*")) {
			out.add(expansion("*", "*", "all visible columns", ""));
		}
		for (ScopeSource source : scope.visibleSources()) {
			String qualifier = source.qualifier();
			if (!qualifier.isEmpty()) {
				String text = qualifier + ".*";
				if (seen.add(key(text))) {
					out.add(expansion(text, text, "all columns from " + source.name(), source.name()));
				}
				columns(catalog, scope, source).ifPresent(columns -> {
					String list = qualifiedColumnList(qualifier, columns, planner);
					if (!list.isEmpty() && seen.add(key(list))) {
						out.add(expansion(list, list, "column list from " + source.name(), source.name(), "list"));
					}
				});
			}
		}
		return List.copyOf(out);
	}

	private static List<Candidate> qualifiedExpansions(Catalog catalog, ResolvedScope scope, String qualifier,
			InsertionPlanner planner) {
		List<Candidate> out = new ArrayList<>();
		out.add(expansion(qualifier + ".*", "*", "all columns from " + qualifier, qualifier));
		source(scope, qualifier).flatMap(source -> columns(catalog, scope, source)).ifPresent(columns -> {
			String display = qualifiedColumnList(qualifier, columns, planner);
			String insertText = qualifiedColumnListAfterQualifier(qualifier, columns, planner);
			if (!display.isEmpty()) {
				out.add(expansion(display, insertText, "column list from " + qualifier, qualifier, "list"));
			}
		});
		return List.copyOf(out);
	}

	private static Candidate expansion(String display, String insertText, String detail, String qualifiedName) {
		return expansion(display, insertText, detail, qualifiedName, "star");
	}

	private static Candidate expansion(String display, String insertText, String detail, String qualifiedName,
			String badge) {
		return new Candidate(new CandidateKind.Expansion(), display, detail, insertText, insertText.length(), null,
				new CandidateDoc(qualifiedName, "expansion", List.of(badge), "", null, null, List.of()));
	}

	private static Optional<ScopeSource> source(ResolvedScope scope, String qualifier) {
		return scope == null ? Optional.empty() : scope.source(qualifier);
	}

	private static Optional<List<String>> columns(Catalog catalog, ResolvedScope scope, ScopeSource source) {
		// A CTE binding shadows a same-named catalog table, so it must win here.
		if (source.kind() == SourceKind.CTE && scope != null) {
			Optional<List<String>> cteColumns = scope.cte(source.name()).map(CteBinding::columns);
			if (cteColumns.isPresent()) {
				return cteColumns;
			}
		}
		return table(catalog, source.name()).map(table -> table.columns().stream()
				.map(Catalog.Column::name)
				.toList());
	}

	private static String qualifiedColumnList(String qualifier, List<String> columns, InsertionPlanner planner) {
		return columns.stream()
				.map(column -> qualifier + "." + planner.identifier(column))
				.collect(Collectors.joining(", "));
	}

	private static String qualifiedColumnListAfterQualifier(String qualifier, List<String> columns,
			InsertionPlanner planner) {
		if (columns.isEmpty()) {
			return "";
		}
		List<String> out = new ArrayList<>(columns.size());
		out.add(planner.identifier(columns.get(0)));
		for (int i = 1; i < columns.size(); i++) {
			out.add(qualifier + "." + planner.identifier(columns.get(i)));
		}
		return String.join(", ", out);
	}

	private static boolean expansionContext(Context context) {
		return context instanceof Context.ColumnRef || context instanceof Context.OnCondition
				|| context instanceof Context.GroupByRef || context instanceof Context.OrderByRef
				|| context instanceof Context.ExpressionRef;
	}
}
