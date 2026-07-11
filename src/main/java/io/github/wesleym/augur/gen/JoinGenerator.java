package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.Provenance;
import io.github.wesleym.augur.TypeFamily;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static io.github.wesleym.augur.gen.GeneratorSupport.aliasFor;
import static io.github.wesleym.augur.gen.GeneratorSupport.effectivePlanner;
import static io.github.wesleym.augur.gen.GeneratorSupport.emptyCatalog;
import static io.github.wesleym.augur.gen.GeneratorSupport.key;
import static io.github.wesleym.augur.gen.GeneratorSupport.qualified;
import static io.github.wesleym.augur.gen.GeneratorSupport.references;
import static io.github.wesleym.augur.gen.GeneratorSupport.table;
import static io.github.wesleym.augur.gen.GeneratorSupport.usedAliases;
import static io.github.wesleym.augur.gen.GeneratorSupport.visibleTable;

/** Generates FK-backed JOIN targets and ON predicates from visible scope sources. */
public final class JoinGenerator {
	private JoinGenerator() { }

	public static List<Candidate> generate(Catalog catalog, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		if (context instanceof Context.JoinTarget) {
			return generateJoinTargets(catalog, scope, insertion);
		}
		if (context instanceof Context.OnCondition) {
			return generateOnConditions(catalog, scope, insertion);
		}
		if (context instanceof Context.ExpressionRef expression) {
			// After a table (before the JOIN keyword is even typed), offer the whole FK join up front: the user
			// typing `jo` gets `join Accounts a ON a.id = b.account_id` in one go, not just the bare keyword.
			return generateInlineJoins(catalog, scope, insertion, expression.prefix());
		}
		return List.of();
	}

	private static List<Candidate> generateInlineJoins(Catalog catalog, ResolvedScope scope,
			InsertionPlanner insertion, String prefix) {
		List<Candidate> targets = generateJoinTargets(catalog, scope, insertion);
		if (targets.isEmpty()) {
			return List.of();
		}
		String join = effectivePlanner(insertion).keyword(prefix, "join");   // "join" or "JOIN", mirroring case
		List<Candidate> out = new ArrayList<>(targets.size());
		for (Candidate target : targets) {
			String insertText = join + " " + target.insertText();
			String display = join + " " + target.display();
			out.add(new Candidate(target.kind(), display, target.detail(), insertText, insertText.length(), null,
					target.doc()));
		}
		return List.copyOf(out);
	}

	public static List<Candidate> generateJoinTargets(Catalog catalog, ResolvedScope scope,
			InsertionPlanner insertion) {
		if (emptyCatalog(catalog) || scope == null || scope.visibleSources().isEmpty()) {
			return List.of();
		}
		InsertionPlanner planner = effectivePlanner(insertion);
		Set<String> usedAliases = usedAliases(scope);
		List<Candidate> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (ScopeSource source : scope.visibleSources()) {
			Optional<Catalog.Table> sourceTableMatch = table(catalog, source.name());
			if (sourceTableMatch.isEmpty()) {
				continue;
			}
			Catalog.Table sourceTable = sourceTableMatch.orElseThrow();
			addJunctionPaths(catalog, scope, source, sourceTable, usedAliases, planner, out, seen);
			addOutgoingTargets(catalog, scope, source, sourceTable, usedAliases, planner, out, seen);
			addIncomingTargets(catalog, scope, source, sourceTable, usedAliases, planner, out, seen);
		}
		return List.copyOf(out);
	}

	public static List<Candidate> generateOnConditions(Catalog catalog, ResolvedScope scope,
			InsertionPlanner insertion) {
		if (emptyCatalog(catalog) || scope == null || scope.visibleSources().size() < 2) {
			return List.of();
		}
		InsertionPlanner planner = effectivePlanner(insertion);
		List<ScopedTable> tables = scopedTables(catalog, scope);
		List<Candidate> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (int i = 0; i < tables.size(); i++) {
			for (int j = i + 1; j < tables.size(); j++) {
				addOnConditions(tables.get(i), tables.get(j), planner, out, seen);
				addOnConditions(tables.get(j), tables.get(i), planner, out, seen);
			}
		}
		return List.copyOf(out);
	}

	private static void addOutgoingTargets(Catalog catalog, ResolvedScope scope, ScopeSource source,
			Catalog.Table sourceTable, Set<String> usedAliases, InsertionPlanner planner, List<Candidate> out,
			Set<String> seen) {
		for (Catalog.Column column : sourceTable.columns()) {
			if (!column.foreignKey()) {
				continue;
			}
			Catalog.Reference reference = column.reference();
			Optional<Catalog.Table> targetMatch = table(catalog, reference.table());
			if (targetMatch.isEmpty()) {
				continue;
			}
			Catalog.Table target = targetMatch.orElseThrow();
			if (visibleTable(scope, target.name())) {
				continue;
			}
			String targetAlias = aliasFor(target.name(), usedAliases);
			String predicate = qualified(targetAlias, reference.column(), planner) + " = "
					+ qualified(source.qualifier(), column.name(), planner);
			addJoinTarget(target, targetAlias, sourceTable, column, reference, predicate, planner, out, seen);
		}
	}

	private static void addIncomingTargets(Catalog catalog, ResolvedScope scope, ScopeSource source,
			Catalog.Table sourceTable, Set<String> usedAliases, InsertionPlanner planner, List<Candidate> out,
			Set<String> seen) {
		for (Catalog.Table candidateTable : catalog.tables()) {
			if (visibleTable(scope, candidateTable.name())) {
				continue;
			}
			for (Catalog.Column column : candidateTable.columns()) {
				if (!column.foreignKey() || !references(column.reference(), sourceTable)) {
					continue;
				}
				String targetAlias = aliasFor(candidateTable.name(), usedAliases);
				String predicate = qualified(targetAlias, column.name(), planner) + " = "
						+ qualified(source.qualifier(), column.reference().column(), planner);
				addJoinTarget(candidateTable, targetAlias, candidateTable, column, column.reference(), predicate,
						planner, out, seen);
			}
		}
	}

	private static void addJunctionPaths(Catalog catalog, ResolvedScope scope, ScopeSource source,
			Catalog.Table sourceTable, Set<String> usedAliases, InsertionPlanner planner, List<Candidate> out,
			Set<String> seen) {
		for (Catalog.Table junction : catalog.tables()) {
			if (visibleTable(scope, junction.name()) || !isJunction(junction)) {
				continue;
			}
			List<Catalog.Column> fks = foreignKeys(junction);
			for (Catalog.Column sourceFk : fks) {
				if (!references(sourceFk.reference(), sourceTable)) {
					continue;
				}
				for (Catalog.Column targetFk : fks) {
					if (targetFk == sourceFk) {
						continue;
					}
					Optional<Catalog.Table> targetMatch = table(catalog, targetFk.reference().table());
					if (targetMatch.isEmpty()) {
						continue;
					}
					Catalog.Table target = targetMatch.orElseThrow();
					if (visibleTable(scope, target.name())) {
						continue;
					}
					Set<String> aliases = new LinkedHashSet<>(usedAliases);
					String junctionAlias = aliasFor(junction.name(), aliases);
					aliases.add(key(junctionAlias));
					String targetAlias = aliasFor(target.name(), aliases);
					String firstPredicate = qualified(junctionAlias, sourceFk.name(), planner) + " = "
							+ qualified(source.qualifier(), sourceFk.reference().column(), planner);
					String secondPredicate = qualified(targetAlias, targetFk.reference().column(), planner) + " = "
							+ qualified(junctionAlias, targetFk.name(), planner);
					String insertText = planner.identifier(junction.name()) + " " + junctionAlias + " ON "
							+ firstPredicate + " JOIN " + planner.identifier(target.name()) + " "
							+ targetAlias + " ON " + secondPredicate;
					String display = target.name() + " " + targetAlias + " via " + junction.name();
					String seenKey = key(insertText);
					if (seen.add(seenKey)) {
						out.add(new Candidate(new CandidateKind.JoinPath(junction.name()), display,
								"junction path via " + junction.name(), insertText, insertText.length(), null,
								new CandidateDoc(junction.name(), "join path", List.of("join path", "junction"),
										target.name(), null, null, List.of())));
					}
				}
			}
		}
	}

	private static boolean isJunction(Catalog.Table table) {
		if (Boolean.FALSE.equals(table.junctionOverride())) {
			return false;
		}
		List<Catalog.Column> fks = foreignKeys(table);
		if (Boolean.TRUE.equals(table.junctionOverride())) {
			return fks.size() == 2;
		}
		if (fks.size() != 2) {
			return false;
		}
		Set<String> fkNames = new LinkedHashSet<>();
		Set<String> primaryKeys = new LinkedHashSet<>();
		for (Catalog.Column column : table.columns()) {
			if (column.primaryKey()) {
				primaryKeys.add(key(column.name()));
			}
			if (column.foreignKey()) {
				fkNames.add(key(column.name()));
			}
		}
		// The classic shape: the primary key IS the FK pair.
		if (!primaryKeys.isEmpty() && fkNames.containsAll(primaryKeys)) {
			return true;
		}
		// The ORM shape: a surrogate id plus the two FKs, with nothing but audit timestamps besides. Any
		// payload column means the table is an entity in its own right, not a junction.
		int surrogateKeys = 0;
		for (Catalog.Column column : table.columns()) {
			if (column.foreignKey()) {
				continue;
			}
			if (column.primaryKey()) {
				if (++surrogateKeys > 1) {
					return false;
				}
				continue;
			}
			if (!temporal(column)) {
				return false;
			}
		}
		return true;
	}

	private static boolean temporal(Catalog.Column column) {
		if (column.typeFamily() == TypeFamily.TEMPORAL) {
			return true;
		}
		String type = column.typeName().toLowerCase(Locale.ROOT);
		return type.contains("date") || type.contains("time");
	}

	private static List<Catalog.Column> foreignKeys(Catalog.Table table) {
		List<Catalog.Column> out = new ArrayList<>();
		for (Catalog.Column column : table.columns()) {
			if (column.foreignKey()) {
				out.add(column);
			}
		}
		return List.copyOf(out);
	}

	private static void addJoinTarget(Catalog.Table targetTable, String targetAlias, Catalog.Table fkTable,
			Catalog.Column fkColumn, Catalog.Reference reference, String predicate, InsertionPlanner planner,
			List<Candidate> out, Set<String> seen) {
		String display = targetTable.name() + " " + targetAlias + " ON " + predicate;
		String insertText = planner.identifier(targetTable.name()) + " " + targetAlias + " ON " + predicate;
		String seenKey = key(insertText);
		if (seen.add(seenKey)) {
			out.add(new Candidate(new CandidateKind.JoinClause(reference.provenance()), display,
					detail(reference.provenance(), fkTable.name(), fkColumn.name(), reference), insertText,
					insertText.length(), null, doc(fkTable.name(), fkColumn.name(), reference)));
		}
	}

	private static void addOnConditions(ScopedTable child, ScopedTable parent, InsertionPlanner planner,
			List<Candidate> out, Set<String> seen) {
		for (Catalog.Column column : child.table().columns()) {
			if (!column.foreignKey() || !references(column.reference(), parent.table())) {
				continue;
			}
			Catalog.Reference reference = column.reference();
			String predicate = qualified(parent.source().qualifier(), reference.column(), planner) + " = "
					+ qualified(child.source().qualifier(), column.name(), planner);
			String seenKey = key(predicate);
			if (seen.add(seenKey)) {
				out.add(new Candidate(new CandidateKind.JoinClause(reference.provenance()), predicate,
						detail(reference.provenance(), child.table().name(), column.name(), reference), predicate,
						predicate.length(), null, doc(child.table().name(), column.name(), reference)));
			}
		}
	}

	private static List<ScopedTable> scopedTables(Catalog catalog, ResolvedScope scope) {
		List<ScopedTable> out = new ArrayList<>();
		for (ScopeSource source : scope.visibleSources()) {
			table(catalog, source.name()).ifPresent(table -> out.add(new ScopedTable(source, table)));
		}
		return List.copyOf(out);
	}

	private static String detail(Provenance provenance, String table, String column, Catalog.Reference reference) {
		return key(provenance.name()) + " join via " + table + "." + column + " -> "
				+ reference.table() + "." + reference.column();
	}

	private static CandidateDoc doc(String table, String column, Catalog.Reference reference) {
		return new CandidateDoc(table + "." + column, "join", List.of("join",
				key(reference.provenance().name())), reference.table(), null, null, List.of());
	}

	private record ScopedTable(ScopeSource source, Catalog.Table table) { }
}
