package io.github.wesleym.augur;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Optional value-distribution snapshot supplied by a host. */
public interface Profiles {
	List<ValueShare> values(String table, String column);

	default OptionalLong distinctCount(String table, String column) {
		return OptionalLong.empty();
	}

	default OptionalDouble nullFraction(String table, String column) {
		return OptionalDouble.empty();
	}

	static Profiles empty() {
		return EmptyProfiles.INSTANCE;
	}

	static ProfileSnapshot.Builder builder() {
		return ProfileSnapshot.builder();
	}

	enum EmptyProfiles implements Profiles {
		INSTANCE;

		@Override
		public List<ValueShare> values(String table, String column) {
			return List.of();
		}
	}
}
