package io.github.wesleym.augur.samples;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.Completion;

import java.util.List;

/**
 * A narrated, self-documenting tour of Augur's completion families.
 *
 * <p>Each scenario prints the SQL with the caret marked by {@code |}, the top
 * ranked candidates, and the text that results from accepting the first one.
 * Read top to bottom to see every generator the engine currently ships.
 */
public final class Quickstart {
	private Quickstart() { }

	private record Scenario(String title, String sqlWithCaret) { }

	private static final List<Scenario> SCENARIOS = List.of(
			new Scenario("Table reference",
					"select * from bat|"),
			new Scenario("Qualified column (alias-aware)",
					"select l.cog| from legionary l"),
			new Scenario("Column-list expansion for a source",
					"select l.| from legionary l"),
			new Scenario("FK-backed JOIN target",
					"select * from battle b join leg|"),
			new Scenario("Two-hop join path via a junction table",
					"select * from legionary l join gen|"),
			new Scenario("ON predicate from a foreign key",
					"select * from battle b join legionary l on |"),
			new Scenario("GROUP BY backfill from the select list",
					"select l.name, l.century, count(*) from legionary l group by |"),
			new Scenario("Profiled value literal",
					"select * from battle where outcome = |"),
			new Scenario("INSERT column/value scaffold",
					"insert into legionary |"));

	public static void main(String[] args) {
		Augur augur = DemoCatalog.augur();
		System.out.println("Augur SQL — completion tour (dialect: " + DemoCatalog.DIALECT.name() + ")");
		System.out.println("The caret is marked with |.  Showing the top candidates and the applied edit.\n");

		for (Scenario scenario : SCENARIOS) {
			run(augur, scenario);
		}
	}

	private static void run(Augur augur, Scenario scenario) {
		CaretText input = CaretText.parse(scenario.sqlWithCaret());
		Completion completion = augur.complete(input.sql(), input.caret());

		System.out.println("● " + scenario.title());
		System.out.println("    " + scenario.sqlWithCaret());

		if (completion.isEmpty()) {
			System.out.println("    (no candidates)\n");
			return;
		}

		int shown = 0;
		for (Candidate candidate : completion.candidates()) {
			if (shown++ == 3) {
				break;
			}
			String marker = shown == 1 ? "  → " : "    ";
			String detail = candidate.detail().isEmpty() ? "" : "   " + dim(candidate.detail());
			System.out.println("    " + marker + pad(candidate.display(), 22) + "↦ " + candidate.insertText() + detail);
		}

		String applied = completion.applyFirst(input.sql());
		System.out.println("    " + dim("accept #1 ⇒ ") + applied + "\n");
	}

	private static String pad(String value, int width) {
		if (value.length() >= width) {
			return value + " ";
		}
		return value + " ".repeat(width - value.length());
	}

	private static String dim(String value) {
		return "[2m" + value + "[0m";
	}
}
