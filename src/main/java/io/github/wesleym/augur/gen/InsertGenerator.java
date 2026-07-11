package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeSource;
import io.github.wesleym.augur.scope.SourceKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.wesleym.augur.gen.GeneratorSupport.effectivePlanner;
import static io.github.wesleym.augur.gen.GeneratorSupport.emptyCatalog;
import static io.github.wesleym.augur.gen.GeneratorSupport.table;

/** Generates INSERT column-list and VALUES scaffolds for the target table. */
public final class InsertGenerator {
	private InsertGenerator() { }

	public static List<Candidate> generate(Catalog catalog, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		if (!(context instanceof Context.InsertColumns insertColumns) || emptyCatalog(catalog) || scope == null) {
			return List.of();
		}
		Optional<ScopeSource> target = insertTarget(scope);
		if (target.isEmpty()) {
			return List.of();
		}
		ScopeSource targetSource = target.orElseThrow();
		Optional<Catalog.Table> targetTable = table(catalog, targetSource.name());
		if (targetTable.isEmpty()) {
			return List.of();
		}
		Catalog.Table table = targetTable.orElseThrow();
		InsertionPlanner planner = effectivePlanner(insertion);
		List<Catalog.Column> columns = writableColumns(table);
		if (columns.isEmpty()) {
			return List.of();
		}
		String display = scaffoldText(columns, insertColumns.openParenTyped(), planner, false);
		String insertText = scaffoldText(columns, insertColumns.openParenTyped(), planner, true);
		int caretAfter = firstPlaceholder(insertText);
		return List.of(new Candidate(new CandidateKind.Scaffold(), display,
				"insert scaffold for " + table.name(), insertText, caretAfter, null, doc(table, columns)));
	}

	private static Optional<ScopeSource> insertTarget(ResolvedScope scope) {
		for (ScopeSource source : scope.sources()) {
			if (source.kind() == SourceKind.INSERT_TARGET) {
				return Optional.of(source);
			}
		}
		return Optional.empty();
	}

	private static List<Catalog.Column> writableColumns(Catalog.Table table) {
		List<Catalog.Column> out = new ArrayList<>();
		for (Catalog.Column column : table.columns()) {
			if (column.role() != ColumnRole.SYSTEM) {
				out.add(column);
			}
		}
		return List.copyOf(out);
	}

	private static String scaffoldText(List<Catalog.Column> columns, boolean openParenTyped, InsertionPlanner planner,
			boolean insertionText) {
		List<String> columnNames = new ArrayList<>(columns.size());
		List<String> placeholders = new ArrayList<>(columns.size());
		for (Catalog.Column column : columns) {
			columnNames.add(insertionText ? planner.identifier(column.name()) : column.name());
			placeholders.add("?");
		}
		String prefix = openParenTyped ? "" : "(";
		return prefix + String.join(", ", columnNames) + ") values (" + String.join(", ", placeholders) + ")";
	}

	private static int firstPlaceholder(String insertText) {
		int placeholder = insertText.indexOf('?');
		return placeholder < 0 ? insertText.length() : placeholder;
	}

	private static CandidateDoc doc(Catalog.Table table, List<Catalog.Column> columns) {
		String columnBadge = columns.size() == 1 ? "1 column" : columns.size() + " columns";
		return new CandidateDoc(table.name(), "insert scaffold", List.of("insert", columnBadge), "", null, null,
				List.of());
	}

}
