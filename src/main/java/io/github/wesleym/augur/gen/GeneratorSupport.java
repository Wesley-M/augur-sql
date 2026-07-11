package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeSource;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Shared package-local helpers for generator implementations. */
final class GeneratorSupport {
	private GeneratorSupport() { }

	static boolean emptyCatalog(Catalog catalog) {
		return catalog == null || catalog.isEmpty();
	}

	static InsertionPlanner effectivePlanner(InsertionPlanner insertion) {
		return insertion == null ? new InsertionPlanner(null) : insertion;
	}

	static Optional<Catalog.Table> table(Catalog catalog, String name) {
		if (emptyCatalog(catalog)) {
			return Optional.empty();
		}
		Optional<Catalog.Table> exact = catalog.table(name);
		if (exact.isPresent()) {
			return exact;
		}
		Optional<Catalog.Table> unqualified = catalog.table(lastPart(name));
		if (unqualified.isPresent()) {
			return unqualified;
		}
		String wanted = key(lastPart(name));
		for (Catalog.Table table : catalog.tables()) {
			if (key(lastPart(table.name())).equals(wanted)) {
				return Optional.of(table);
			}
		}
		return Optional.empty();
	}

	static boolean references(Catalog.Reference reference, Catalog.Table targetTable) {
		if (reference == null || targetTable == null) {
			return false;
		}
		String referenced = key(reference.table());
		String target = key(targetTable.name());
		String targetLastPart = key(lastPart(targetTable.name()));
		return referenced.equals(target) || referenced.equals(targetLastPart)
				|| key(lastPart(reference.table())).equals(targetLastPart);
	}

	static boolean visibleTable(ResolvedScope scope, String tableName) {
		if (scope == null) {
			return false;
		}
		String wanted = key(tableName);
		String wantedLastPart = key(lastPart(tableName));
		for (ScopeSource source : scope.visibleSources()) {
			if (key(source.name()).equals(wanted) || key(lastPart(source.name())).equals(wantedLastPart)) {
				return true;
			}
		}
		return false;
	}

	static Set<String> usedAliases(ResolvedScope scope) {
		Set<String> out = new LinkedHashSet<>();
		if (scope != null) {
			for (ScopeSource source : scope.visibleSources()) {
				out.add(key(source.qualifier()));
			}
		}
		return Set.copyOf(out);
	}

	static String aliasFor(String tableName, Set<String> usedAliases) {
		Set<String> aliases = usedAliases == null ? Set.of() : usedAliases;
		String seed = aliasSeed(tableName);
		String alias = seed;
		int suffix = 2;
		while (aliases.contains(key(alias))) {
			alias = seed + suffix;
			suffix++;
		}
		return alias;
	}

	static String qualified(String qualifier, String column, InsertionPlanner planner) {
		return qualifier + "." + planner.identifier(column);
	}

	static String lastPart(String name) {
		String value = text(name);
		int dot = value.lastIndexOf('.');
		return dot < 0 ? value : value.substring(dot + 1);
	}

	static String key(String value) {
		return text(value).toLowerCase(Locale.ROOT);
	}

	private static String aliasSeed(String tableName) {
		String value = text(tableName).trim();
		if (value.isEmpty()) {
			return "t";
		}
		StringBuilder initials = new StringBuilder();
		boolean nextInitial = true;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '_' || c == '-' || c == ' ' || c == '.') {
				nextInitial = true;
				continue;
			}
			if (nextInitial && Character.isLetterOrDigit(c)) {
				initials.append(Character.toLowerCase(c));
			}
			nextInitial = false;
		}
		String seed = initials.isEmpty() ? "t" : initials.toString();
		return Character.isDigit(seed.charAt(0)) ? "t" + seed : seed;
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
