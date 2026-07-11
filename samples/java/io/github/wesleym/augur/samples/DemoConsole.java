package io.github.wesleym.augur.samples;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.Completion;
import io.github.wesleym.augur.context.CaretClassifier;
import io.github.wesleym.augur.context.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Interactive terminal demo. Type SQL (mark the caret with {@code |}) and see
 * how Augur classifies the caret and which candidates it ranks. Meta commands
 * starting with {@code :} explore the schema and load examples.
 */
public final class DemoConsole {
	private static final int LIMIT = 10;

	private static final List<String> EXAMPLES = List.of(
			"select * from bat|",
			"select l.| from legionary l",
			"select * from battle b join leg|",
			"select * from legionary l join gen|",
			"select * from battle b join legionary l on |",
			"select l.name, count(*) from legionary l group by |",
			"select * from battle where outcome = |",
			"insert into legionary |");

	private DemoConsole() { }

	public static void main(String[] args) throws IOException {
		Augur augur = DemoCatalog.augur();
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		banner();
		while (true) {
			System.out.print("\nsql> ");
			String line = reader.readLine();
			if (line == null) {
				break;
			}
			line = line.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.equals("exit") || line.equals("quit") || line.equals(":q")) {
				break;
			}
			if (line.startsWith(":")) {
				meta(augur, line);
				continue;
			}
			complete(augur, line);
		}
		System.out.println("bye.");
	}

	private static void complete(Augur augur, String line) {
		CaretText input = CaretText.parse(line);
		Context context = CaretClassifier.classify(input.sql(), input.caret(), DemoCatalog.DIALECT);
		Completion completion = augur.complete(input.sql(), input.caret());

		System.out.println("  context   " + describe(context));
		System.out.println("  replacing " + completion.replaceSpan().start() + ".." + completion.replaceSpan().end());
		if (completion.isEmpty()) {
			System.out.println("  (no candidates)");
			return;
		}

		List<Candidate> shown = completion.candidates().stream().limit(LIMIT).toList();
		int width = shown.stream().mapToInt(c -> c.display().length()).max().orElse(0);
		int rank = 1;
		for (Candidate candidate : shown) {
			String detail = candidate.detail().isEmpty() ? "" : "  · " + candidate.detail();
			System.out.printf("  %2d. %-14s %-" + Math.max(1, width) + "s  ↦ %s%s%n",
					rank++, kindLabel(candidate), candidate.display(), candidate.insertText(), detail);
		}
		System.out.println("  accept #1 ⇒ " + completion.applyFirst(input.sql()));
	}

	private static void meta(Augur augur, String command) {
		switch (command) {
			case ":help" -> banner();
			case ":schema" -> printSchema(augur.catalog());
			case ":examples" -> printExamples();
			case ":dialect" -> System.out.println("  dialect: " + DemoCatalog.DIALECT.name());
			default -> {
				if (command.startsWith(":e")) {
					int index = parseIndex(command);
					if (index >= 0 && index < EXAMPLES.size()) {
						String example = EXAMPLES.get(index);
						System.out.println("  " + example);
						complete(augur, example);
						return;
					}
				}
				System.out.println("  unknown command. try :help");
			}
		}
	}

	private static int parseIndex(String command) {
		try {
			return Integer.parseInt(command.substring(2).strip()) - 1;
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	private static void printSchema(Catalog catalog) {
		System.out.println("  Schema (" + catalog.tables().size() + " relations):");
		for (Catalog.Table table : catalog.tables()) {
			String kind = table.view() ? "view" : "table";
			String rows = table.hasKnownRowCount() ? "  (" + table.rowCount() + " rows)" : "";
			System.out.println("    " + table.name() + "  " + kind + rows);
			for (Catalog.Column column : table.columns()) {
				System.out.println("      " + column.name() + " : " + column.typeName()
						+ badges(column));
			}
		}
	}

	private static String badges(Catalog.Column column) {
		StringBuilder out = new StringBuilder();
		if (column.primaryKey()) {
			out.append(" [pk]");
		}
		if (column.foreignKey()) {
			out.append(" [fk → ").append(column.reference().table()).append(']');
		}
		if (column.role().name().equals("SENSITIVE")) {
			out.append(" [sensitive]");
		}
		return out.toString();
	}

	private static void printExamples() {
		System.out.println("  Examples (run with :e <n>):");
		for (int i = 0; i < EXAMPLES.size(); i++) {
			System.out.printf("    %d. %s%n", i + 1, EXAMPLES.get(i));
		}
	}

	private static void banner() {
		System.out.println("Augur SQL — interactive console  (dialect: " + DemoCatalog.DIALECT.name() + ")");
		System.out.println("  Type SQL and mark the caret with |, e.g.  select * from app|");
		System.out.println("  Commands:  :schema   :examples   :e <n>   :dialect   :help   exit");
	}

	private static String kindLabel(Candidate candidate) {
		return candidate.kind().getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
	}

	private static String describe(Context context) {
		String name = context.getClass().getSimpleName();
		String prefix = context.prefix();
		return prefix.isEmpty() ? name : name + " prefix=\"" + prefix + "\"";
	}
}
