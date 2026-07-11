package io.github.wesleym.augur.rank;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.match.MatchResult;

import java.util.Arrays;
import java.util.Objects;

/** Candidate plus deterministic rank metadata. */
public record RankedCandidate(Candidate candidate, MatchResult match, int usageScore, int[] key) {
	public RankedCandidate {
		candidate = Objects.requireNonNull(candidate, "candidate");
		match = Objects.requireNonNull(match, "match");
		key = key == null ? new int[0] : key.clone();
	}

	@Override
	public int[] key() {
		return key.clone();
	}

	public String debugKey() {
		return Arrays.toString(key);
	}
}
