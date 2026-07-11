package io.github.wesleym.augur;

import io.github.wesleym.augur.lex.LexProfile;

import java.util.List;

/** Built-in dialect handles. */
public final class Dialects {
	public static final Dialect ANSI = Dialect.builder("ANSI")
			.pagination(Pagination.fetchFirst())
			.build();

	public static final Dialect POSTGRES = Dialect.builder("PostgreSQL")
			.lex(LexProfile.ansi().withDollarInIdentifiers(true))
			.keywords(keywords("BIGSERIAL", "FALSE", "ILIKE", "RETURNING", "SERIAL", "TRUE"))
			.identifierCase(IdentifierCase.LOWER)
			.pagination(Pagination.limitOffset())
			.types(TypeMap.ansi().with(".*\\b(bytea)\\b.*", TypeFamily.BINARY))
			.build();

	public static final Dialect MYSQL = Dialect.builder("MySQL")
			.lex(LexProfile.ansi()
					.withIdentifierQuote('`', '`')
					.withLineComment("#"))
			.keywords(keywords("AUTO_INCREMENT", "FALSE", "REPLACE", "TRUE", "UNSIGNED"))
			.pagination(Pagination.limitOffset())
			.types(TypeMap.ansi().with(".*\\btinyint\\s*\\(\\s*1\\s*\\).*", TypeFamily.BOOLEAN))
			.build();

	public static final Dialect SQLSERVER = Dialect.builder("SQL Server")
			.lex(LexProfile.ansi().withIdentifierQuote('[', ']'))
			.keywords(keywords("GO", "IDENTITY", "NVARCHAR", "OFFSET", "TOP"))
			.pagination(Pagination.top())
			// bit columns take 1/0, and there is no current_date expression.
			.literals(new Literals("1", "0", "", "CURRENT_TIMESTAMP"))
			.types(TypeMap.ansi().with(".*\\b(uniqueidentifier)\\b.*", TypeFamily.TEXT))
			.build();

	public static final Dialect SQLANYWHERE = Dialect.builder("SQL Anywhere")
			.lex(LexProfile.ansi().withAdditionalIdentifierQuote('[', ']'))
			.keywords(keywords("AT", "AUTOINCREMENT", "START", "TOP"))
			.pagination(Pagination.top())
			// bit columns take 1/0; the special values are spelled with a space (CURRENT DATE).
			.literals(new Literals("1", "0", "CURRENT DATE", "CURRENT TIMESTAMP"))
			.build();

	public static final Dialect SQLITE = Dialect.builder("SQLite")
			.lex(LexProfile.ansi()
					.withAdditionalIdentifierQuote('`', '`')
					.withAdditionalIdentifierQuote('[', ']'))
			.keywords(keywords("FALSE", "GLOB", "PRAGMA", "TRUE"))
			.pagination(Pagination.limitOffset())
			// booleans are stored as integers; 1/0 works on every SQLite version.
			.literals(new Literals("1", "0", "CURRENT_DATE", "CURRENT_TIMESTAMP"))
			.build();

	public static final Dialect H2 = Dialect.builder("H2")
			.lex(LexProfile.ansi().withAdditionalIdentifierQuote('`', '`'))
			.keywords(keywords("AUTO_INCREMENT", "BOOLEAN", "IDENTITY", "LIMIT", "MINUS"))
			.pagination(Pagination.limitOffset())
			.build();

	public static final Dialect GENERIC = Dialect.builder("Generic")
			.lex(LexProfile.ansi()
					.withAdditionalIdentifierQuote('`', '`')
					.withAdditionalIdentifierQuote('[', ']')
					.withDollarInIdentifiers(true))
			.keywords(keywords("FALSE", "LIMIT", "TRUE"))
			.pagination(Pagination.none())
			.build();

	private static final List<Dialect> BUILT_INS = List.of(
			ANSI, POSTGRES, MYSQL, SQLSERVER, SQLANYWHERE, SQLITE, H2, GENERIC);

	private Dialects() { }

	public static List<Dialect> builtIns() {
		return BUILT_INS;
	}

	private static KeywordSet keywords(String... extras) {
		return KeywordSet.ansi().with(extras);
	}
}
