package io.github.wesleym.augur.match;

/** Match quality tiers in rank order. */
public enum MatchTier {
	EXACT,
	PREFIX_CS,
	PREFIX_CI,
	HUMP,
	SUBSTRING,
	SUBSEQUENCE,
	NO_MATCH
}
