package io.github.wesleym.augur;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Identifier behavior that affects quoting, reserved words, and case folding. */
public record IdentifierRules(Set<String> reservedWords, IdentifierCase unquotedCase) {
	public IdentifierRules {
		reservedWords = normalizeReservedWords(reservedWords);
		unquotedCase = Objects.requireNonNullElse(unquotedCase, IdentifierCase.PRESERVE);
	}

	public static IdentifierRules ansi() {
		return new IdentifierRules(KeywordSet.ansi().words(), IdentifierCase.PRESERVE);
	}

	public static IdentifierRules ansi(Collection<String> reservedWords) {
		return new IdentifierRules(normalizeReservedWords(reservedWords), IdentifierCase.PRESERVE);
	}

	public boolean isReserved(String identifier) {
		return reservedWords.contains(Catalog.text(identifier).toUpperCase(Locale.ROOT));
	}

	public String fold(String identifier) {
		return unquotedCase.apply(identifier);
	}

	public IdentifierRules withReservedWords(Collection<String> words) {
		return new IdentifierRules(normalizeReservedWords(words), unquotedCase);
	}

	public IdentifierRules withUnquotedCase(IdentifierCase identifierCase) {
		return new IdentifierRules(reservedWords, identifierCase);
	}

	private static Set<String> normalizeReservedWords(Collection<String> words) {
		if (words == null) {
			return KeywordSet.ansi().words();
		}
		TreeSet<String> normalized = new TreeSet<>();
		for (String word : words) {
			if (word != null && !word.isBlank()) {
				normalized.add(word.toUpperCase(Locale.ROOT));
			}
		}
		return Set.copyOf(normalized);
	}
}
