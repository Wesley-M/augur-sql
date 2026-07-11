package io.github.wesleym.augur;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Ordered SQL type-name rules mapped into coarse completion type families. */
public record TypeMap(List<TypeRule> rules) {
	private static final TypeMap ANSI = new TypeMap(List.of(
			TypeRule.of(".*\\b(boolean|bool|bit)\\b.*", TypeFamily.BOOLEAN),
			TypeRule.of(".*\\b(bigint|integer|int|smallint|tinyint|mediumint|serial|bigserial|smallserial|identity)\\b.*",
					TypeFamily.INTEGER),
			TypeRule.of(".*\\b(decimal|numeric|number|real|double|float|money|smallmoney)\\b.*",
					TypeFamily.DECIMAL),
			TypeRule.of(".*\\b(char|character|varchar|nvarchar|nchar|text|clob|string|uuid|json|jsonb|xml)\\b.*",
					TypeFamily.TEXT),
			TypeRule.of(".*\\b(date|time|timestamp|datetime|datetime2|smalldatetime|interval)\\b.*",
					TypeFamily.TEMPORAL),
			TypeRule.of(".*\\b(binary|varbinary|blob|bytea|image)\\b.*", TypeFamily.BINARY)));

	public TypeMap {
		rules = copyRules(rules);
	}

	public static TypeMap ansi() {
		return ANSI;
	}

	public TypeFamily family(String typeName) {
		String value = Catalog.text(typeName);
		for (TypeRule rule : rules) {
			if (rule.matches(value)) {
				return rule.family();
			}
		}
		return TypeFamily.UNKNOWN;
	}

	public TypeMap with(String regex, TypeFamily family) {
		List<TypeRule> out = new ArrayList<>(rules.size() + 1);
		out.add(TypeRule.of(regex, family));
		out.addAll(rules);
		return new TypeMap(out);
	}

	public record TypeRule(Pattern pattern, TypeFamily family) {
		public TypeRule {
			pattern = Objects.requireNonNull(pattern, "pattern");
			family = Objects.requireNonNullElse(family, TypeFamily.UNKNOWN);
		}

		public static TypeRule of(String regex, TypeFamily family) {
			if (regex == null || regex.isBlank()) {
				throw new IllegalArgumentException("type rule regex is required");
			}
			return new TypeRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), family);
		}

		public boolean matches(String typeName) {
			return pattern.matcher(Catalog.text(typeName)).matches();
		}
	}

	private static List<TypeRule> copyRules(List<TypeRule> rules) {
		if (rules == null || rules.isEmpty()) {
			return List.of();
		}
		List<TypeRule> out = new ArrayList<>(rules.size());
		for (TypeRule rule : rules) {
			if (rule != null) {
				out.add(rule);
			}
		}
		return List.copyOf(out);
	}
}
