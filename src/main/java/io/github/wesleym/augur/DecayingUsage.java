package io.github.wesleym.augur;

import io.github.wesleym.augur.lex.IdentifierQuote;
import io.github.wesleym.augur.lex.SqlLexer;
import io.github.wesleym.augur.lex.Token;
import io.github.wesleym.augur.lex.TokenKind;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Mutable frecency tracker with exponential decay and zero-dependency persistence. */
public final class DecayingUsage implements Usage {
	private static final String HEADER = "augur-usage-v1";
	private static final Duration DEFAULT_HALF_LIFE = Duration.ofDays(30);
	private static final double OBSERVED_IDENTIFIER_WEIGHT = 1.0;
	private static final double ACCEPTED_CANDIDATE_WEIGHT = 5.0;
	private static final double SCORE_SCALE = 1_000.0;

	private final Clock clock;
	private final long halfLifeMillis;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public DecayingUsage() {
		this(Clock.systemUTC(), DEFAULT_HALF_LIFE);
	}

	public DecayingUsage(Clock clock, Duration halfLife) {
		this.clock = Objects.requireNonNull(clock, "clock");
		if (halfLife == null || halfLife.isZero() || halfLife.isNegative()) {
			throw new IllegalArgumentException("halfLife must be positive");
		}
		this.halfLifeMillis = halfLife.toMillis();
	}

	public static DecayingUsage create() {
		return new DecayingUsage();
	}

	public static DecayingUsage load(String text) {
		return load(text, Clock.systemUTC());
	}

	public static DecayingUsage load(String text, Clock clock) {
		List<String> lines = lines(text);
		if (lines.isEmpty()) {
			return new DecayingUsage(clock, DEFAULT_HALF_LIFE);
		}
		Header header = Header.parse(lines.get(0));
		DecayingUsage usage = new DecayingUsage(clock, Duration.ofMillis(header.halfLifeMillis()));
		for (int i = 1; i < lines.size(); i++) {
			EntryLine entry = EntryLine.parse(lines.get(i));
			if (entry != null) {
				usage.entries.put(entry.identifier(), new Entry(entry.weight(), entry.observedAtMillis()));
			}
		}
		return usage;
	}

	@Override
	public synchronized int score(String identifierLower) {
		Entry entry = entries.get(key(identifierLower));
		if (entry == null) {
			return 0;
		}
		return (int) Math.round(decayed(entry, nowMillis()) * SCORE_SCALE);
	}

	@Override
	public void observeStatement(String sql) {
		observeStatement(sql, Dialects.ANSI);
	}

	public synchronized void observeStatement(String sql, Dialect dialect) {
		Dialect effectiveDialect = dialect == null ? Dialects.ANSI : dialect;
		for (Token token : SqlLexer.lex(sql, effectiveDialect)) {
			if (token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.QUOTED_IDENTIFIER) {
				observeIdentifier(identifierText(token, effectiveDialect), OBSERVED_IDENTIFIER_WEIGHT);
			}
		}
	}

	@Override
	public synchronized void observeAcceptance(Candidate candidate) {
		if (candidate != null) {
			String text = candidate.display().isEmpty() ? candidate.insertText() : candidate.display();
			observeIdentifier(text, ACCEPTED_CANDIDATE_WEIGHT);
		}
	}

	public synchronized void observeIdentifier(String identifier) {
		observeIdentifier(identifier, OBSERVED_IDENTIFIER_WEIGHT);
	}

	public synchronized void pruneBelow(int minimumScore) {
		long now = nowMillis();
		entries.entrySet().removeIf(entry ->
				(int) Math.round(decayed(entry.getValue(), now) * SCORE_SCALE) < minimumScore);
	}

	public synchronized String save() {
		StringBuilder out = new StringBuilder();
		out.append(HEADER).append('\t').append(halfLifeMillis).append('\n');
		for (Map.Entry<String, Entry> entry : entries.entrySet()) {
			out.append(encode(entry.getKey()))
					.append('\t')
					.append(entry.getValue().observedAtMillis())
					.append('\t')
					.append(entry.getValue().weight())
					.append('\n');
		}
		return out.toString();
	}

	private void observeIdentifier(String identifier, double weight) {
		String normalized = key(identifier);
		if (normalized.isEmpty()) {
			return;
		}
		long now = nowMillis();
		Entry current = entries.get(normalized);
		double next = (current == null ? 0.0 : decayed(current, now)) + weight;
		entries.put(normalized, new Entry(next, now));
	}

	private double decayed(Entry entry, long nowMillis) {
		long elapsed = Math.max(0L, nowMillis - entry.observedAtMillis());
		return entry.weight() * Math.pow(0.5, (double) elapsed / halfLifeMillis);
	}

	private long nowMillis() {
		return clock.millis();
	}

	private static String identifierText(Token token, Dialect dialect) {
		if (token.kind() != TokenKind.QUOTED_IDENTIFIER || token.text().isEmpty()) {
			return token.text();
		}
		IdentifierQuote quote = dialect.lex().quoteStartingWith(token.text().charAt(0));
		String value = token.text();
		int start = 1;
		int end = value.endsWith(String.valueOf(quote.close())) ? value.length() - 1 : value.length();
		String close = String.valueOf(quote.close());
		return value.substring(start, end).replace(close + close, close);
	}

	private static String key(String value) {
		return Catalog.text(value).toLowerCase(Locale.ROOT);
	}

	private static String encode(String value) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String value) {
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	private static List<String> lines(String text) {
		String value = Catalog.text(text);
		if (value.isEmpty()) {
			return List.of();
		}
		String[] split = value.split("\\R");
		List<String> out = new ArrayList<>(split.length);
		for (String line : split) {
			if (!line.isBlank()) {
				out.add(line);
			}
		}
		return List.copyOf(out);
	}

	private record Header(long halfLifeMillis) {
		private static Header parse(String line) {
			String[] fields = line.split("\\t");
			if (fields.length != 2 || !fields[0].equals(HEADER)) {
				throw new IllegalArgumentException("unsupported usage format");
			}
			long halfLife = Long.parseLong(fields[1]);
			if (halfLife <= 0) {
				throw new IllegalArgumentException("halfLife must be positive");
			}
			return new Header(halfLife);
		}
	}

	private record Entry(double weight, long observedAtMillis) { }

	private record EntryLine(String identifier, long observedAtMillis, double weight) {
		private static EntryLine parse(String line) {
			String[] fields = line.split("\\t");
			if (fields.length != 3) {
				return null;
			}
			try {
				double weight = Double.parseDouble(fields[2]);
				if (Double.isNaN(weight) || weight <= 0.0) {
					return null;
				}
				return new EntryLine(decode(fields[0]), Long.parseLong(fields[1]), weight);
			} catch (IllegalArgumentException ex) {
				return null;
			}
		}
	}
}
