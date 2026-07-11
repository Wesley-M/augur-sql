package io.github.wesleym.augur.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic matcher for identifiers and multi-word keywords. */
public final class HumpMatcher {
	private HumpMatcher() { }

	public static MatchResult match(String pattern, String candidate) {
		String query = text(pattern);
		String value = text(candidate);
		if (query.isEmpty()) {
			return new MatchResult(MatchTier.EXACT, score(value, new int[0], true), new int[0]);
		}
		if (value.isEmpty()) {
			return MatchResult.noMatch();
		}
		if (value.equals(query)) {
			return new MatchResult(MatchTier.EXACT, score(value, range(query.length()), true), range(query.length()));
		}
		if (value.startsWith(query)) {
			return new MatchResult(MatchTier.PREFIX_CS, score(value, range(query.length()), true),
					range(query.length()));
		}
		String queryLower = lower(query);
		String valueLower = lower(value);
		if (valueLower.startsWith(queryLower)) {
			return new MatchResult(MatchTier.PREFIX_CI, score(value, range(query.length()), false),
					range(query.length()));
		}
		int[] hump = matchHump(query, value);
		if (hump.length == query.length()) {
			return new MatchResult(MatchTier.HUMP, score(value, hump, caseExact(query, value, hump)), hump);
		}
		int substring = valueLower.indexOf(queryLower);
		if (substring >= 0) {
			int[] positions = range(substring, substring + query.length());
			return new MatchResult(MatchTier.SUBSTRING, score(value, positions, caseExact(query, value, positions)),
					positions);
		}
		int[] subsequence = matchSubsequence(query, value);
		if (subsequence.length == query.length()) {
			return new MatchResult(MatchTier.SUBSEQUENCE, score(value, subsequence,
					caseExact(query, value, subsequence)), subsequence);
		}
		return MatchResult.noMatch();
	}

	private static int[] matchHump(String query, String value) {
		String queryLower = lower(query);
		String valueLower = lower(value);
		boolean[] starts = wordStarts(value);
		int[] positions = new int[query.length()];
		int from = 0;
		for (int i = 0; i < queryLower.length(); i++) {
			int found = -1;
			for (int j = from; j < valueLower.length(); j++) {
				if (starts[j] && valueLower.charAt(j) == queryLower.charAt(i)) {
					found = j;
					break;
				}
			}
			if (found < 0) {
				return new int[0];
			}
			positions[i] = found;
			from = found + 1;
		}
		return positions;
	}

	private static int[] matchSubsequence(String query, String value) {
		String queryLower = lower(query);
		String valueLower = lower(value);
		List<Integer> positions = new ArrayList<>(query.length());
		int from = 0;
		for (int i = 0; i < queryLower.length(); i++) {
			int found = valueLower.indexOf(queryLower.charAt(i), from);
			if (found < 0) {
				return new int[0];
			}
			positions.add(found);
			from = found + 1;
		}
		return toArray(positions);
	}

	private static boolean[] wordStarts(String value) {
		boolean[] starts = new boolean[value.length()];
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (i == 0) {
				starts[i] = true;
			} else {
				char previous = value.charAt(i - 1);
				starts[i] = isSeparator(previous)
						|| Character.isLowerCase(previous) && Character.isUpperCase(c)
						|| Character.isDigit(c) && !Character.isDigit(previous);
			}
		}
		return starts;
	}

	private static int score(String value, int[] positions, boolean caseExact) {
		if (positions.length == 0) {
			return 0;
		}
		boolean[] starts = wordStarts(value);
		int score = caseExact ? 20 : 0;
		score += Math.max(0, 80 - positions[0] * 2);
		for (int i = 0; i < positions.length; i++) {
			int position = positions[i];
			score += starts[position] ? 30 : 5;
			if (i > 0 && position == positions[i - 1] + 1) {
				score += 15;
			}
		}
		score -= Math.max(0, value.length() - positions.length);
		return score;
	}

	private static boolean caseExact(String query, String value, int[] positions) {
		if (query.length() != positions.length) {
			return false;
		}
		for (int i = 0; i < query.length(); i++) {
			if (positions[i] >= value.length() || query.charAt(i) != value.charAt(positions[i])) {
				return false;
			}
		}
		return true;
	}

	private static int[] range(int end) {
		return range(0, end);
	}

	private static int[] range(int start, int end) {
		int[] out = new int[Math.max(0, end - start)];
		for (int i = 0; i < out.length; i++) {
			out[i] = start + i;
		}
		return out;
	}

	private static int[] toArray(List<Integer> values) {
		int[] out = new int[values.size()];
		for (int i = 0; i < values.size(); i++) {
			out[i] = values.get(i);
		}
		return out;
	}

	private static boolean isSeparator(char c) {
		return c == '_' || c == '-' || c == ' ' || c == '.' || c == '/';
	}

	private static String lower(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
