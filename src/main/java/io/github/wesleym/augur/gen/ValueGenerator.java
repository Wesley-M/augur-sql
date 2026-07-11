package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;
import io.github.wesleym.augur.Literals;
import io.github.wesleym.augur.Profiles;
import io.github.wesleym.augur.TypeFamily;
import io.github.wesleym.augur.ValueShare;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeSource;
import io.github.wesleym.augur.scope.SourceKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static io.github.wesleym.augur.gen.GeneratorSupport.emptyCatalog;
import static io.github.wesleym.augur.gen.GeneratorSupport.key;
import static io.github.wesleym.augur.gen.GeneratorSupport.lastPart;
import static io.github.wesleym.augur.gen.GeneratorSupport.table;

/** Generates profiled literal values for a comparison column. */
public final class ValueGenerator {
	private static final long MAX_DISTINCT_COUNT = 50;
	private static final int MAX_VALUES = 15;

	private ValueGenerator() { }

	public static List<Candidate> generate(Catalog catalog, Profiles profiles, ResolvedScope scope, Context context) {
		return generate(catalog, profiles, scope, context, null);
	}

	public static List<Candidate> generate(Catalog catalog, Profiles profiles, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		if (!(context instanceof Context.ValueLiteral valueLiteral) || emptyCatalog(catalog)
				|| valueLiteral.column().isEmpty()) {
			return List.of();
		}
		Literals literals = GeneratorSupport.effectivePlanner(insertion).dialect().literals();
		Profiles effectiveProfiles = profiles == null ? Profiles.empty() : profiles;
		List<ColumnTarget> targets = targets(catalog, scope, valueLiteral);
		if (targets.isEmpty()) {
			return List.of();
		}
		List<Candidate> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (ColumnTarget target : targets) {
			for (ValueShare value : values(effectiveProfiles, target)) {
				String insertText = literal(value.value(), target.column());
				if (seen.add(key(insertText))) {
					out.add(new Candidate(new CandidateKind.Value(), value.value(), detail(target, value), insertText,
							insertText.length(), null, doc(effectiveProfiles, target, value)));
				}
			}
			// Type-aware literals when there's no value profile to draw on: a boolean's two values, a temporal
			// column's "now" so the user isn't stopped inventing a date. Deterministic — no host clock needed —
			// and spelled per dialect (SQL Server/SQL Anywhere bit columns take 1/0, not true/false).
			for (String template : typeTemplates(target.column(), literals)) {
				if (seen.add(key(template))) {
					out.add(new Candidate(new CandidateKind.Value(), template, "value", template, template.length(),
							null, CandidateDoc.empty()));
				}
			}
		}
		return List.copyOf(out);
	}

	private static List<String> typeTemplates(Catalog.Column column, Literals literals) {
		TypeFamily family = column.typeFamily();
		String type = column.typeName().toLowerCase(Locale.ROOT);
		if (family == TypeFamily.BOOLEAN || type.equals("bool") || type.equals("boolean")) {
			return nonEmpty(literals.booleanTrue(), literals.booleanFalse());
		}
		if (family == TypeFamily.TEMPORAL || type.contains("date") || type.contains("time")) {
			return nonEmpty(literals.currentDate(), literals.currentTimestamp());
		}
		return List.of();
	}

	private static List<String> nonEmpty(String first, String second) {
		List<String> out = new ArrayList<>(2);
		if (!first.isEmpty()) {
			out.add(first);
		}
		if (!second.isEmpty()) {
			out.add(second);
		}
		return List.copyOf(out);
	}

	private static List<ColumnTarget> targets(Catalog catalog, ResolvedScope scope, Context.ValueLiteral context) {
		List<ColumnTarget> out = new ArrayList<>();
		if (!context.qualifier().isEmpty()) {
			addQualifiedTarget(catalog, scope, context.qualifier(), context.column(), out);
			return List.copyOf(out);
		}
		if (scope != null && !scope.visibleSources().isEmpty()) {
			for (ScopeSource source : scope.visibleSources()) {
				if (source.kind() == SourceKind.CTE) {
					// CTE columns are not catalog columns; they carry no profile to value against.
					continue;
				}
				table(catalog, source.name()).flatMap(table -> table.column(context.column())
						.map(column -> new ColumnTarget(table, column))).ifPresent(out::add);
			}
			return List.copyOf(out);
		}
		for (Catalog.Table table : catalog.tables()) {
			table.column(context.column()).ifPresent(column -> out.add(new ColumnTarget(table, column)));
		}
		return List.copyOf(out);
	}

	private static void addQualifiedTarget(Catalog catalog, ResolvedScope scope, String qualifier, String columnName,
			List<ColumnTarget> out) {
		if (scope != null) {
			Optional<ScopeSource> sourceMatch = scope.source(qualifier);
			if (sourceMatch.isPresent()) {
				ScopeSource source = sourceMatch.orElseThrow();
				table(catalog, source.name()).flatMap(table -> table.column(columnName)
						.map(column -> new ColumnTarget(table, column))).ifPresent(out::add);
				return;
			}
		}
		table(catalog, qualifier).flatMap(table -> table.column(columnName)
				.map(column -> new ColumnTarget(table, column))).ifPresent(out::add);
	}

	private static List<ValueShare> values(Profiles profiles, ColumnTarget target) {
		if (target.column().role() == ColumnRole.SENSITIVE || highCardinality(profiles, target)) {
			return List.of();
		}
		List<ValueShare> values = profiles.values(target.table().name(), target.column().name());
		if (values != null && !values.isEmpty()) {
			return cap(values);
		}
		String lastPart = lastPart(target.table().name());
		if (!lastPart.equals(target.table().name())) {
			return cap(profiles.values(lastPart, target.column().name()));
		}
		return List.of();
	}

	private static boolean highCardinality(Profiles profiles, ColumnTarget target) {
		Long count = distinctCount(profiles, target);
		return count != null && count > MAX_DISTINCT_COUNT;
	}

	private static List<ValueShare> cap(List<ValueShare> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<ValueShare> out = new ArrayList<>(Math.min(values.size(), MAX_VALUES));
		for (ValueShare value : values) {
			if (value != null) {
				out.add(value);
				if (out.size() == MAX_VALUES) {
					break;
				}
			}
		}
		return List.copyOf(out);
	}

	private static String literal(String value, Catalog.Column column) {
		if (rawLiteral(column)) {
			return value;
		}
		return "'" + value.replace("'", "''") + "'";
	}

	private static boolean rawLiteral(Catalog.Column column) {
		TypeFamily family = column.typeFamily();
		if (family == TypeFamily.INTEGER || family == TypeFamily.DECIMAL || family == TypeFamily.BOOLEAN) {
			return true;
		}
		String type = column.typeName().toLowerCase(Locale.ROOT);
		return type.contains("int") || type.contains("decimal") || type.contains("numeric")
				|| type.contains("float") || type.contains("double") || type.equals("boolean")
				|| type.equals("bool");
	}

	private static String detail(ColumnTarget target, ValueShare value) {
		return target.table().name() + "." + target.column().name() + " - " + percent(value.share());
	}

	private static CandidateDoc doc(Profiles profiles, ColumnTarget target, ValueShare value) {
		List<String> badges = new ArrayList<>();
		badges.add("value");
		badges.add(percent(value.share()));
		if (value.approximate()) {
			badges.add("approx");
		}
		return new CandidateDoc(target.table().name() + "." + target.column().name(), target.column().typeName(),
				badges, "", nullFraction(profiles, target), distinctCount(profiles, target), List.of(value));
	}

	private static Double nullFraction(Profiles profiles, ColumnTarget target) {
		OptionalDouble exact = profiles.nullFraction(target.table().name(), target.column().name());
		if (exact.isPresent()) {
			return exact.orElseThrow();
		}
		String lastPart = lastPart(target.table().name());
		if (!lastPart.equals(target.table().name())) {
			OptionalDouble unqualified = profiles.nullFraction(lastPart, target.column().name());
			if (unqualified.isPresent()) {
				return unqualified.orElseThrow();
			}
		}
		return null;
	}

	private static Long distinctCount(Profiles profiles, ColumnTarget target) {
		OptionalLong exact = profiles.distinctCount(target.table().name(), target.column().name());
		if (exact.isPresent()) {
			return exact.orElseThrow();
		}
		String lastPart = lastPart(target.table().name());
		if (!lastPart.equals(target.table().name())) {
			OptionalLong unqualified = profiles.distinctCount(lastPart, target.column().name());
			if (unqualified.isPresent()) {
				return unqualified.orElseThrow();
			}
		}
		return null;
	}

	private static String percent(double share) {
		return Math.round(share * 100.0) + "%";
	}

	private record ColumnTarget(Catalog.Table table, Catalog.Column column) { }
}
