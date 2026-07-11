package io.github.wesleym.augur.match;

import java.util.Arrays;

/** One deterministic fuzzy-match result. */
public record MatchResult(MatchTier tier, int score, int[] positions) {
	private static final MatchResult NO_MATCH = new MatchResult(MatchTier.NO_MATCH, Integer.MIN_VALUE, new int[0]);

	public MatchResult {
		if (tier == null) {
			throw new NullPointerException("tier");
		}
		positions = positions == null ? new int[0] : positions.clone();
		Arrays.sort(positions);
	}

	public static MatchResult noMatch() {
		return NO_MATCH;
	}

	public boolean matched() {
		return tier != MatchTier.NO_MATCH;
	}

	@Override
	public int[] positions() {
		return positions.clone();
	}
}
