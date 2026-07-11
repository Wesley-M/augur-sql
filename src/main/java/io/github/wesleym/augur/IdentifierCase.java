package io.github.wesleym.augur;

import java.util.Locale;

/** Case folding applied by a dialect to unquoted identifiers. */
public enum IdentifierCase {
	PRESERVE,
	LOWER,
	UPPER;

	public String apply(String identifier) {
		String value = Catalog.text(identifier);
		return switch (this) {
			case LOWER -> value.toLowerCase(Locale.ROOT);
			case UPPER -> value.toUpperCase(Locale.ROOT);
			case PRESERVE -> value;
		};
	}
}
