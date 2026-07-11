package io.github.wesleym.augur.lex;

import java.util.ArrayList;
import java.util.List;

/** Dialect-specific lexical behavior used by {@link SqlLexer}. */
public record LexProfile(List<IdentifierQuote> identifierQuotes, List<String> lineComments,
		List<BlockComment> blockComments, boolean dollarInIdentifiers) {
	private static final LexProfile ANSI = new LexProfile(
			List.of(new IdentifierQuote('"', '"')),
			List.of("--"),
			List.of(new BlockComment("/*", "*/")),
			false);

	public LexProfile {
		identifierQuotes = copyQuotes(identifierQuotes);
		lineComments = copyMarkers(lineComments);
		blockComments = copyBlocks(blockComments);
	}

	public static LexProfile ansi() {
		return ANSI;
	}

	public LexProfile withIdentifierQuote(char open, char close) {
		List<IdentifierQuote> quotes = new ArrayList<>(identifierQuotes.size() + 1);
		quotes.add(new IdentifierQuote(open, close));
		quotes.addAll(identifierQuotes);
		return new LexProfile(quotes, lineComments, blockComments, dollarInIdentifiers);
	}

	public LexProfile withAdditionalIdentifierQuote(char open, char close) {
		List<IdentifierQuote> quotes = new ArrayList<>(identifierQuotes.size() + 1);
		quotes.addAll(identifierQuotes);
		quotes.add(new IdentifierQuote(open, close));
		return new LexProfile(quotes, lineComments, blockComments, dollarInIdentifiers);
	}

	public LexProfile withLineComment(String marker) {
		List<String> markers = new ArrayList<>(lineComments.size() + 1);
		markers.add(marker);
		markers.addAll(lineComments);
		return new LexProfile(identifierQuotes, markers, blockComments, dollarInIdentifiers);
	}

	public LexProfile withBlockComment(String opener, String closer) {
		List<BlockComment> comments = new ArrayList<>(blockComments.size() + 1);
		comments.add(new BlockComment(opener, closer));
		comments.addAll(blockComments);
		return new LexProfile(identifierQuotes, lineComments, comments, dollarInIdentifiers);
	}

	public LexProfile withDollarInIdentifiers(boolean enabled) {
		return new LexProfile(identifierQuotes, lineComments, blockComments, enabled);
	}

	public boolean isIdentifierStart(char c) {
		return Character.isLetter(c) || c == '_';
	}

	public boolean isIdentifierPart(char c) {
		return isIdentifierStart(c) || Character.isDigit(c) || (dollarInIdentifiers && c == '$');
	}

	public IdentifierQuote quoteStartingWith(char c) {
		for (IdentifierQuote quote : identifierQuotes) {
			if (quote.open() == c) {
				return quote;
			}
		}
		return null;
	}

	public record BlockComment(String opener, String closer) {
		public BlockComment {
			if (opener == null || opener.isEmpty()) {
				throw new IllegalArgumentException("block comment opener is required");
			}
			if (closer == null || closer.isEmpty()) {
				throw new IllegalArgumentException("block comment closer is required");
			}
		}
	}

	private static List<IdentifierQuote> copyQuotes(List<IdentifierQuote> quotes) {
		if (quotes == null || quotes.isEmpty()) {
			return List.of();
		}
		List<IdentifierQuote> out = new ArrayList<>(quotes.size());
		for (IdentifierQuote quote : quotes) {
			if (quote != null) {
				out.add(quote);
			}
		}
		return List.copyOf(out);
	}

	private static List<String> copyMarkers(List<String> markers) {
		if (markers == null || markers.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(markers.size());
		for (String marker : markers) {
			if (marker != null && !marker.isEmpty()) {
				out.add(marker);
			}
		}
		return List.copyOf(out);
	}

	private static List<BlockComment> copyBlocks(List<BlockComment> comments) {
		if (comments == null || comments.isEmpty()) {
			return List.of();
		}
		List<BlockComment> out = new ArrayList<>(comments.size());
		for (BlockComment comment : comments) {
			if (comment != null) {
				out.add(comment);
			}
		}
		return List.copyOf(out);
	}
}
