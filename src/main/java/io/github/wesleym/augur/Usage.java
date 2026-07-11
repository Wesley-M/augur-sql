package io.github.wesleym.augur;

/** Optional frecency signal supplied by a host or Augur's default usage tracker. */
public interface Usage {
	int score(String identifierLower);

	default void observeStatement(String sql) { }

	default void observeAcceptance(Candidate candidate) { }

	static Usage none() {
		return NoUsage.INSTANCE;
	}

	enum NoUsage implements Usage {
		INSTANCE;

		@Override
		public int score(String identifierLower) {
			return 0;
		}
	}
}
