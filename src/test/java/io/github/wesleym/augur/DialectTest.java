package io.github.wesleym.augur;

import io.github.wesleym.augur.lex.LexProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialectTest {
	@ParameterizedTest
	@MethodSource("builtIns")
	void builtInsExposeCompleteSpecs(Dialect dialect) {
		assertTrue(dialect.isKeyword("select"));
		assertTrue(dialect.needsQuoting("order"));
		assertFalse(dialect.needsQuoting("patient_id"));
		assertFalse(dialect.lex().identifierQuotes().isEmpty());
		assertNotNull(dialect.identifiers());
		assertNotNull(dialect.qualification());
		assertNotNull(dialect.pagination());
		assertNotNull(dialect.types());
		assertEquals(TypeFamily.TEXT, dialect.typeFamily("varchar(20)"));
		assertEquals(TypeFamily.INTEGER, dialect.typeFamily("bigint"));
		assertEquals(TypeFamily.DECIMAL, dialect.typeFamily("decimal(10, 2)"));
		assertEquals(TypeFamily.BOOLEAN, dialect.typeFamily("boolean"));
		assertEquals(TypeFamily.TEMPORAL, dialect.typeFamily("timestamp"));
		assertEquals(TypeFamily.BINARY, dialect.typeFamily("blob"));
	}

	@Test
	void builtInLexProfilesCaptureDialectDifferences() {
		assertEquals('`', Dialects.MYSQL.lex().identifierQuotes().get(0).open());
		assertEquals('[', Dialects.SQLSERVER.lex().identifierQuotes().get(0).open());
		assertEquals('"', Dialects.SQLITE.lex().identifierQuotes().get(0).open());
		assertNotNull(Dialects.SQLITE.lex().quoteStartingWith('`'));
		assertNotNull(Dialects.SQLITE.lex().quoteStartingWith('['));
		assertTrue(Dialects.MYSQL.lex().lineComments().contains("#"));
		assertEquals(IdentifierCase.LOWER, Dialects.POSTGRES.identifiers().unquotedCase());
		assertEquals(Pagination.Style.TOP, Dialects.SQLSERVER.pagination().style());
		assertEquals(TypeFamily.BOOLEAN, Dialects.MYSQL.typeFamily("tinyint(1)"));
	}

	@Test
	void builderAuthorsCustomDialectSpecs() {
		TypeMap types = TypeMap.ansi().with(".*\\bmoney2\\b.*", TypeFamily.DECIMAL);
		Dialect dialect = Dialect.builder("custom")
				.lex(LexProfile.ansi().withIdentifierQuote('`', '`').withDollarInIdentifiers(true))
				.keywords("upsert")
				.identifierCase(IdentifierCase.UPPER)
				.qualification(new Qualification("app", true))
				.pagination(Pagination.top())
				.types(types)
				.build();

		assertTrue(dialect.isKeyword("UPSERT"));
		assertFalse(dialect.isKeyword("select"));
		assertTrue(dialect.needsQuoting("upsert"));
		// '$' is a valid identifier part here (withDollarInIdentifiers), so an already-folded name is bare...
		assertFalse(dialect.needsQuoting("TOTAL$"));
		// ...but a name whose case differs from the dialect's folding must be quoted to resolve to it.
		assertTrue(dialect.needsQuoting("total$"));
		assertEquals("PATIENT", dialect.foldIdentifier("patient"));
		assertTrue(dialect.qualification().hasOwnerPrefix());
		assertEquals(TypeFamily.DECIMAL, dialect.typeFamily("money2"));

		Dialect copy = dialect.toBuilder().name("copy").reservedWords(Set.of("only")).build();
		assertEquals("copy", copy.name());
		assertTrue(copy.needsQuoting("only"));
		// No longer reserved (reservedWords was reassigned), and already in the dialect's UPPER folding, so bare.
		assertFalse(copy.needsQuoting("UPSERT"));
		assertEquals("PATIENT", copy.foldIdentifier("patient"));

		Dialect orderInsensitive = Dialect.builder("order-insensitive")
				.identifierCase(IdentifierCase.LOWER)
				.keywords("merge")
				.build();
		assertTrue(orderInsensitive.needsQuoting("merge"));
		assertEquals("patient", orderInsensitive.foldIdentifier("PATIENT"));
	}

	@Test
	void valueObjectsNormalizeSparseInputsAndDefaults() {
		KeywordSet keywords = KeywordSet.of(Arrays.asList("one", null, "", "Two"));
		IdentifierRules identifiers = IdentifierRules.ansi(List.of("order")).withUnquotedCase(IdentifierCase.LOWER);
		LexProfile lex = LexProfile.ansi()
				.withAdditionalIdentifierQuote('`', '`')
				.withLineComment("#")
				.withBlockComment("{-", "-}");

		assertTrue(KeywordSet.of(null).contains("select"));
		assertTrue(keywords.contains("TWO"));
		assertFalse(KeywordSet.of(Arrays.asList(null, "")).contains("select"));
		assertTrue(KeywordSet.ansi().with("extra", null, "").contains("extra"));
		assertTrue(identifiers.isReserved("ORDER"));
		assertEquals("patient", identifiers.fold("PATIENT"));
		assertEquals("PATIENT", IdentifierCase.UPPER.apply("patient"));
		assertEquals("patient", IdentifierCase.PRESERVE.apply("patient"));
		assertEquals('`', lex.quoteStartingWith('`').open());
		assertTrue(lex.lineComments().contains("#"));
		assertEquals("{-", lex.blockComments().get(0).opener());
		assertFalse(Qualification.none().hasOwnerPrefix());
		assertEquals(Pagination.Style.NONE, new Pagination(null).style());
	}

	@Test
	void typeMapsAreOrderedAndValidateRules() {
		TypeMap sparse = new TypeMap(Arrays.asList(null, TypeMap.TypeRule.of(".*", TypeFamily.TEXT)));
		TypeMap overridden = TypeMap.ansi().with(".*\\bint\\b.*", TypeFamily.TEXT);

		assertEquals(1, sparse.rules().size());
		assertEquals(TypeFamily.UNKNOWN, TypeMap.ansi().family(null));
		assertEquals(TypeFamily.TEXT, overridden.family("int"));
		assertEquals(TypeFamily.UNKNOWN, new TypeMap(null).family("varchar"));
		assertEquals(TypeFamily.UNKNOWN, new TypeMap(List.of(new TypeMap.TypeRule(Pattern.compile(".*"), null)))
				.family("anything"));
		assertThrows(IllegalArgumentException.class, () -> TypeMap.TypeRule.of("", TypeFamily.TEXT));
		assertThrows(IllegalArgumentException.class, () -> TypeMap.TypeRule.of(null, TypeFamily.TEXT));
		assertThrows(NullPointerException.class, () -> new TypeMap.TypeRule(null, TypeFamily.TEXT));
	}

	private static Stream<Dialect> builtIns() {
		return Dialects.builtIns().stream();
	}
}
