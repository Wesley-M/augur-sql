package io.github.wesleym.augur;

/**
 * Dialect spellings for the literal suggestions the engine offers at value positions: the two boolean
 * values and the current date/timestamp expressions. An empty string means the dialect has no portable
 * spelling and the suggestion is simply not offered (e.g. SQL Server has no {@code current_date}).
 */
public record Literals(String booleanTrue, String booleanFalse, String currentDate, String currentTimestamp) {

	public Literals {
		booleanTrue = Catalog.text(booleanTrue);
		booleanFalse = Catalog.text(booleanFalse);
		currentDate = Catalog.text(currentDate);
		currentTimestamp = Catalog.text(currentTimestamp);
	}

	/** The ANSI spellings: {@code true}/{@code false} and {@code current_date}/{@code current_timestamp}. */
	public static Literals ansi() {
		return new Literals("true", "false", "current_date", "current_timestamp");
	}
}
