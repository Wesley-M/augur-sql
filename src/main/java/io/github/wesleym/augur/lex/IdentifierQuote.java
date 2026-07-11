package io.github.wesleym.augur.lex;

/**
 * Quoted identifier delimiter pair for a dialect. The closing delimiter is
 * escaped by doubling it inside the identifier (for example {@code "a""b"}).
 */
public record IdentifierQuote(char open, char close) { }
