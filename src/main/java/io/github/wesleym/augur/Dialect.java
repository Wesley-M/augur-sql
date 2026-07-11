package io.github.wesleym.augur;

import io.github.wesleym.augur.lex.LexProfile;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/** SQL dialect descriptor used by lexing, insertion planning, and generation. */
public record Dialect(String name, LexProfile lex, Set<String> keywords,
		IdentifierRules identifiers, Qualification qualification, Pagination pagination, TypeMap types) {
	public Dialect(String name) {
		this(name, LexProfile.ansi(), ansiKeywords());
	}

	public Dialect(String name, LexProfile lex, Set<String> keywords) {
		this(name, lex, keywords, null, null, null, null);
	}

	public Dialect {
		name = Catalog.text(name);
		lex = lex == null ? LexProfile.ansi() : lex;
		keywords = normalizeKeywords(keywords);
		identifiers = identifiers == null ? IdentifierRules.ansi(keywords) : identifiers;
		qualification = qualification == null ? Qualification.none() : qualification;
		pagination = pagination == null ? Pagination.none() : pagination;
		types = types == null ? TypeMap.ansi() : types;
	}

	public static Builder builder(String name) {
		return new Builder().name(name);
	}

	public Builder toBuilder() {
		return new Builder()
				.name(name)
				.lex(lex)
				.keywords(keywords)
				.identifierRules(identifiers)
				.qualification(qualification)
				.pagination(pagination)
				.types(types);
	}

	public boolean isKeyword(String text) {
		return keywords.contains(Catalog.text(text).toUpperCase(Locale.ROOT));
	}

	public boolean needsQuoting(String identifier) {
		String value = Catalog.text(identifier);
		if (value.isEmpty() || identifiers.isReserved(value) || !lex.isIdentifierStart(value.charAt(0))) {
			return true;
		}
		for (int i = 1; i < value.length(); i++) {
			if (!lex.isIdentifierPart(value.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	public String foldIdentifier(String identifier) {
		return identifiers.fold(identifier);
	}

	public TypeFamily typeFamily(String typeName) {
		return types.family(typeName);
	}

	public Dialect withLex(LexProfile lex) {
		return toBuilder().lex(lex).build();
	}

	public Dialect withKeywords(Collection<String> keywords) {
		Set<String> next = normalizeKeywords(keywords);
		// Resync reserved words to the new keywords, but keep the configured
		// identifier case folding instead of resetting it to PRESERVE.
		IdentifierRules nextRules = IdentifierRules.ansi(next).withUnquotedCase(identifiers.unquotedCase());
		return new Dialect(name, lex, next, nextRules, qualification, pagination, types);
	}

	public static Set<String> ansiKeywords() {
		return KeywordSet.ansi().words();
	}

	private static Set<String> normalizeKeywords(Collection<String> keywords) {
		return KeywordSet.of(keywords).words();
	}

	public static final class Builder {
		private String name;
		private LexProfile lex;
		private Set<String> keywords;
		private IdentifierRules identifiers;
		private IdentifierCase identifierCase;
		private Qualification qualification;
		private Pagination pagination;
		private TypeMap types;

		private Builder() { }

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder lex(LexProfile lex) {
			this.lex = lex;
			return this;
		}

		public Builder keywords(Collection<String> keywords) {
			this.keywords = normalizeKeywords(keywords);
			return this;
		}

		public Builder keywords(String... keywords) {
			return keywords(keywords == null ? null : Arrays.asList(keywords));
		}

		public Builder keywords(KeywordSet keywords) {
			this.keywords = keywords == null ? null : keywords.words();
			return this;
		}

		public Builder identifierRules(IdentifierRules identifiers) {
			this.identifiers = identifiers;
			this.identifierCase = null;
			return this;
		}

		public Builder reservedWords(Collection<String> reservedWords) {
			IdentifierCase currentCase = identifierCase != null
					? identifierCase
					: identifiers == null ? IdentifierCase.PRESERVE : identifiers.unquotedCase();
			this.identifiers = IdentifierRules.ansi(reservedWords).withUnquotedCase(currentCase);
			return this;
		}

		public Builder identifierCase(IdentifierCase identifierCase) {
			this.identifierCase = identifierCase;
			return this;
		}

		public Builder qualification(Qualification qualification) {
			this.qualification = qualification;
			return this;
		}

		public Builder pagination(Pagination pagination) {
			this.pagination = pagination;
			return this;
		}

		public Builder types(TypeMap types) {
			this.types = types;
			return this;
		}

		public Dialect build() {
			return new Dialect(name, lex, keywords, identifierRules(), qualification, pagination, types);
		}

		private IdentifierRules identifierRules() {
			if (identifierCase == null) {
				return identifiers;
			}
			IdentifierRules rules = identifiers == null ? IdentifierRules.ansi(keywords) : identifiers;
			return rules.withUnquotedCase(identifierCase);
		}
	}
}
