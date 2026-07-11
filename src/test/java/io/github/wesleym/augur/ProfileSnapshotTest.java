package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileSnapshotTest {
	@Test
	void builderCreatesCaseInsensitiveImmutableProfiles() {
		List<ValueShare> values = new ArrayList<>();
		values.add(new ValueShare("open", 0.60, false));

		ProfileSnapshot profiles = Profiles.builder()
				.values("Appointment", "Status", values)
				.distinctCount("appointment", "status", 2)
				.nullFraction("appointment", "status", 0.10)
				.column("patient", "kind", List.of(new ValueShare("vip", 0.20, true)), 3L, 0.0)
				.build();
		values.add(new ValueShare("closed", 0.40, false));

		assertEquals(List.of(new ValueShare("open", 0.60, false)), profiles.values("appointment", "status"));
		assertEquals(OptionalLong.of(2), profiles.distinctCount("APPOINTMENT", "STATUS"));
		assertEquals(OptionalDouble.of(0.10), profiles.nullFraction("appointment", "status"));
		assertEquals(1, profiles.profile("patient", "kind").orElseThrow().values().size());
		assertEquals(List.of(), profiles.values("missing", "status"));
		assertEquals(OptionalLong.empty(), profiles.distinctCount("patient", "missing"));
		assertEquals(OptionalDouble.empty(), profiles.nullFraction("patient", "missing"));
		assertFalse(profiles.profile("missing", "status").isPresent());
	}

	@Test
	void profileSnapshotValidatesStatsAndCopiesSparseValues() {
		ProfileSnapshot.ColumnProfile profile = new ProfileSnapshot.ColumnProfile(
				Arrays.asList(null, new ValueShare("x", 1.0, false)), null, null);

		assertEquals(1, profile.values().size());
		assertThrows(IllegalArgumentException.class,
				() -> new ProfileSnapshot.ColumnProfile(List.of(), -1L, null));
		assertThrows(IllegalArgumentException.class,
				() -> new ProfileSnapshot.ColumnProfile(List.of(), null, Double.NaN));
		assertThrows(IllegalArgumentException.class,
				() -> Profiles.builder().distinctCount("t", "c", -1));
		assertThrows(IllegalArgumentException.class,
				() -> Profiles.builder().nullFraction("t", "c", 1.1));
	}
}
