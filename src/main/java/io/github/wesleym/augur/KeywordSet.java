package io.github.wesleym.augur;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Immutable keyword vocabulary for a dialect. */
public record KeywordSet(Set<String> words) {
	private static final Set<String> ANSI_WORDS = Set.of(
			"ALL", "ALTER", "AND", "AS", "ASC", "BETWEEN", "BY", "CASE", "CREATE", "CROSS", "DELETE",
			"DESC", "DISTINCT", "DROP", "ELSE", "END", "EXISTS", "FETCH", "FIRST", "FROM", "FULL",
			"GROUP", "HAVING", "IN", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "LEFT",
			"LIKE", "LIMIT", "NOT", "NULL", "ON", "OR", "ORDER", "OUTER", "RIGHT", "SELECT", "SET",
			"TABLE", "THEN", "TOP", "UNION", "UPDATE", "VALUES", "WHEN", "WHERE", "WITH");

	public KeywordSet {
		words = words == null ? ANSI_WORDS : normalize(words);
	}

	public static KeywordSet ansi() {
		return new KeywordSet(ANSI_WORDS);
	}

	public static KeywordSet of(Collection<String> words) {
		if (words == null || words.isEmpty()) {
			return ansi();
		}
		return new KeywordSet(normalize(words));
	}

	public KeywordSet with(String... extras) {
		LinkedHashSet<String> out = new LinkedHashSet<>(words);
		if (extras != null) {
			for (String extra : extras) {
				if (extra != null && !extra.isBlank()) {
					out.add(extra);
				}
			}
		}
		return new KeywordSet(out);
	}

	public boolean contains(String word) {
		return words.contains(Catalog.text(word).toUpperCase(Locale.ROOT));
	}

	private static Set<String> normalize(Collection<String> words) {
		TreeSet<String> normalized = new TreeSet<>();
		for (String word : words) {
			if (word != null && !word.isBlank()) {
				normalized.add(word.toUpperCase(Locale.ROOT));
			}
		}
		return Set.copyOf(normalized);
	}
}
