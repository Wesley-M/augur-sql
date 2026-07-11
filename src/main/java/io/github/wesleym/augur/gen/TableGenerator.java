package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.insert.InsertionPlanner;

import java.util.ArrayList;
import java.util.List;

import static io.github.wesleym.augur.gen.GeneratorSupport.effectivePlanner;
import static io.github.wesleym.augur.gen.GeneratorSupport.emptyCatalog;

/** Generates table and view candidates from the catalog snapshot. */
public final class TableGenerator {
	private TableGenerator() { }

	public static List<Candidate> generate(Catalog catalog, InsertionPlanner insertion) {
		if (emptyCatalog(catalog)) {
			return List.of();
		}
		InsertionPlanner planner = effectivePlanner(insertion);
		List<Candidate> out = new ArrayList<>();
		for (Catalog.Table table : catalog.tables()) {
			String insertText = planner.identifier(table.name());
			CandidateKind kind = table.view() ? new CandidateKind.View() : new CandidateKind.Table();
			out.add(new Candidate(kind, table.name(), detail(table), insertText, insertText.length(), null,
					doc(table)));
		}
		return List.copyOf(out);
	}

	private static String detail(Catalog.Table table) {
		if (!table.hasKnownRowCount()) {
			return table.view() ? "view" : "table";
		}
		return (table.view() ? "view" : "table") + " - " + table.rowCount() + " rows";
	}

	private static CandidateDoc doc(Catalog.Table table) {
		List<String> badges = new ArrayList<>();
		badges.add(table.view() ? "view" : "table");
		if (table.hasKnownRowCount()) {
			badges.add(table.rowCount() + " rows");
		}
		return new CandidateDoc(table.name(), table.view() ? "view" : "table", badges, "", null, null, List.of());
	}
}
