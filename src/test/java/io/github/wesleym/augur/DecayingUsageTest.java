package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecayingUsageTest {
	@Test
	void observesStatementsAcceptancesAndAppliesDecay() {
		MutableClock clock = new MutableClock();
		DecayingUsage usage = new DecayingUsage(clock, Duration.ofDays(30));

		usage.observeStatement("select `odd``name` from appointment a where status = 'open'", Dialects.MYSQL);

		assertEquals(0, usage.score("select"));
		assertEquals(1_000, usage.score("appointment"));
		assertEquals(1_000, usage.score("odd`name"));

		usage.observeAcceptance(candidate("appointment"));
		assertEquals(6_000, usage.score("appointment"));

		clock.advance(Duration.ofDays(30));
		assertEquals(3_000, usage.score("appointment"));
		assertEquals(500, usage.score("status"));
	}

	@Test
	void persistsLoadsAndPrunesLineOrientedState() {
		MutableClock clock = new MutableClock();
		DecayingUsage usage = new DecayingUsage(clock, Duration.ofDays(30));
		usage.observeIdentifier("patient");
		usage.observeIdentifier("");

		DecayingUsage loaded = DecayingUsage.load(usage.save(), clock);

		assertEquals(1_000, loaded.score("PATIENT"));
		assertEquals(0, loaded.score("missing"));
		assertEquals(0, DecayingUsage.load("augur-usage-v1\t2592000000\nbad\ncGF0aWVudA\t0\t0\n%%\t0\t1\n")
				.score("patient"));

		loaded.pruneBelow(1_001);
		assertEquals(0, loaded.score("patient"));
		assertEquals(0, DecayingUsage.load("", clock).score("patient"));
		assertEquals(0, DecayingUsage.load("").score("patient"));
	}

	@Test
	void validatesHalfLifeAndFormatHeader() {
		assertThrows(IllegalArgumentException.class,
				() -> new DecayingUsage(Clock.systemUTC(), Duration.ZERO));
		assertThrows(IllegalArgumentException.class,
				() -> new DecayingUsage(Clock.systemUTC(), null));
		assertThrows(NullPointerException.class,
				() -> new DecayingUsage(null, Duration.ofDays(1)));
		assertThrows(IllegalArgumentException.class,
				() -> DecayingUsage.load("not-augur\t1", Clock.systemUTC()));
		assertThrows(IllegalArgumentException.class,
				() -> DecayingUsage.load("augur-usage-v1\t0", Clock.systemUTC()));
	}

	@Test
	void createUsesDefaultClockAndNullInputsAreSafe() {
		DecayingUsage usage = DecayingUsage.create();

		usage.observeStatement(null);
		usage.observeStatement("select \"open", null);
		usage.observeAcceptance(null);

		assertTrue(usage.score("open") >= 0);
	}

	private static Candidate candidate(String display) {
		return new Candidate(new CandidateKind.Table(), display, "", display, display.length(), null, null);
	}

	private static final class MutableClock extends Clock {
		private Instant instant = Instant.EPOCH;

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
