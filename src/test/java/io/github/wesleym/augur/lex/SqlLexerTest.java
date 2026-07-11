package io.github.wesleym.augur.lex;

import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlLexerTest {
	@Test
	void tokenizesAnsiSqlWithOffsetsAndKeywords() {
		List<Token> tokens = SqlLexer.lex("select a.id, 'open' from appointment a", Dialects.ANSI);

		assertKinds(tokens, TokenKind.KEYWORD, TokenKind.IDENTIFIER, TokenKind.SYMBOL, TokenKind.IDENTIFIER,
				TokenKind.SYMBOL, TokenKind.STRING, TokenKind.KEYWORD, TokenKind.IDENTIFIER, TokenKind.IDENTIFIER);
		assertEquals("select", tokens.get(0).text());
		assertEquals(0, tokens.get(0).start());
		assertEquals(6, tokens.get(0).end());
		assertEquals("'open'", tokens.get(5).text());
		assertEquals(13, tokens.get(5).start());
	}

	@Test
	void acceptsNullDialectAsAnsi() {
		assertEquals(TokenKind.KEYWORD, new SqlLexer(null).scan("select").tokens().get(0).kind());
	}

	@Test
	void dropsCommentsButKeepsQuietSpans() {
		LexResult result = SqlLexer.scan("select -- ignore from nope\nstatus /* hide ; */ from appointment",
				Dialects.ANSI);

		assertEquals(List.of("select", "status", "from", "appointment"),
				result.tokens().stream().map(Token::text).toList());
		assertTrue(result.quietAt(10));
		assertTrue(result.quietAt(45));
		assertFalse(result.quietAt(result.sql().length()));
	}

	@Test
	void lineCommentsStopAtCarriageReturns() {
		LexResult result = SqlLexer.scan("select -- hide\r status", Dialects.ANSI);

		assertTrue(result.quietAt(12));
		assertEquals(List.of("select", "status"), result.tokens().stream().map(Token::text).toList());
	}

	@Test
	void stringQuietSpanIncludesUnterminatedEndOnly() {
		LexResult closed = SqlLexer.scan("select 'a''b'", Dialects.ANSI);
		LexResult open = SqlLexer.scan("select 'abc", Dialects.ANSI);

		assertEquals("'a''b'", closed.tokens().get(1).text());
		assertTrue(closed.quietAt(9));
		assertFalse(closed.quietAt(13));
		assertTrue(open.quietAt(open.sql().length()));
	}

	@Test
	void supportsDialectIdentifierQuotesAndDollarIdentifiers() {
		Dialect dialect = new Dialect("custom",
				LexProfile.ansi().withIdentifierQuote('`', '`').withDollarInIdentifiers(true),
				Dialect.ansiKeywords());

		List<Token> tokens = SqlLexer.lex("select `weird``name`, total$ from t", dialect);

		assertEquals(TokenKind.QUOTED_IDENTIFIER, tokens.get(1).kind());
		assertEquals("`weird``name`", tokens.get(1).text());
		assertEquals("total$", tokens.get(3).text());
	}

	@Test
	void quotedIdentifiersMayBeUnterminated() {
		Token token = SqlLexer.lex("select \"still_open", Dialects.ANSI).get(1);

		assertEquals(TokenKind.QUOTED_IDENTIFIER, token.kind());
		assertEquals("\"still_open", token.text());
	}

	@Test
	void tokenizesDecimalsAndOperators() {
		List<Token> tokens = SqlLexer.lex("1.25 2. a <> b != c <= d >= e || f && g :: h := i => j -> k ->> l !",
				Dialects.ANSI);

		assertEquals("1.25", tokens.get(0).text());
		assertEquals("2", tokens.get(1).text());
		assertEquals(".", tokens.get(2).text());
		assertEquals(List.of("<>", "!=", "<=", ">=", "||", "&&", "::", ":=", "=>", "->", "->>", "!"),
				tokens.stream()
						.filter(token -> token.kind() == TokenKind.OPERATOR)
						.map(Token::text)
						.toList());
	}

	@Test
	void normalizesDialectKeywords() {
		Dialect dialect = new Dialect("tiny", LexProfile.ansi(), Set.of("foo"));

		assertTrue(dialect.isKeyword("FOO"));
		assertFalse(dialect.isKeyword("select"));
		assertEquals(TokenKind.KEYWORD, SqlLexer.lex("foo", dialect).get(0).kind());
	}

	@Test
	void dialectDefaultsAndFluentCopiesWork() {
		Dialect dialect = new Dialect(null, null, null);

		assertEquals("", dialect.name());
		assertTrue(dialect.isKeyword("select"));
		assertTrue(dialect.withLex(LexProfile.ansi().withDollarInIdentifiers(true)).lex().dollarInIdentifiers());
		assertTrue(dialect.withKeywords(Arrays.asList("one", null, "", "two")).isKeyword("TWO"));
		assertFalse(dialect.withKeywords(Arrays.asList("one", null, "", "two")).isKeyword("select"));
	}

	@Test
	void lexProfileCopiesSparseInputs() {
		LexProfile.BlockComment block = new LexProfile.BlockComment("/*", "*/");
		LexProfile profile = new LexProfile(Arrays.asList(null, new IdentifierQuote('[', ']')),
				Arrays.asList(null, "", "--"), Arrays.asList(null, block), false);
		LexProfile empty = new LexProfile(null, null, null, false);

		assertEquals(1, profile.identifierQuotes().size());
		assertEquals(1, profile.lineComments().size());
		assertEquals(1, profile.blockComments().size());
		assertTrue(empty.identifierQuotes().isEmpty());
		assertTrue(empty.lineComments().isEmpty());
		assertTrue(empty.blockComments().isEmpty());
		assertTrue(profile.isIdentifierPart('7'));
		assertFalse(profile.isIdentifierPart('$'));
		assertEquals(null, profile.quoteStartingWith('`'));
		assertEquals('[', profile.quoteStartingWith('[').open());
	}

	@Test
	void recordsValidateRangesAndMarkers() {
		assertThrows(NullPointerException.class, () -> new Token(null, "", 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Token(TokenKind.IDENTIFIER, "", 2, 1));
		assertThrows(IllegalArgumentException.class, () -> new QuietSpan(2, 1, false));
		assertThrows(IllegalArgumentException.class, () -> new LexProfile.BlockComment(null, "*/"));
		assertThrows(IllegalArgumentException.class, () -> new LexProfile.BlockComment("", "*/"));
		assertThrows(IllegalArgumentException.class, () -> new LexProfile.BlockComment("/*", null));
		assertThrows(IllegalArgumentException.class, () -> new LexProfile.BlockComment("/*", ""));
	}

	@Test
	void resultCopiesSparseInputs() {
		Token token = new Token(TokenKind.IDENTIFIER, "x", 0, 1);
		QuietSpan quiet = new QuietSpan(0, 1, true);
		LexResult result = new LexResult(null, Arrays.asList(null, token), Arrays.asList(null, quiet));

		assertEquals("", result.sql());
		assertEquals(List.of(token), result.tokens());
		assertEquals(List.of(quiet), result.quietSpans());
		assertTrue(result.quietAt(1));
	}

	private static void assertKinds(List<Token> tokens, TokenKind... kinds) {
		assertEquals(kinds.length, tokens.size());
		for (int i = 0; i < kinds.length; i++) {
			assertEquals(kinds[i], tokens.get(i).kind(), "token " + i);
		}
	}
}
