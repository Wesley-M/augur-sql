package io.github.wesleym.augur.rank;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.match.HumpMatcher;
import io.github.wesleym.augur.match.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression tests for order-independent, complete role-bucket matching. */
class RoleBucketTest {
	private static final int ROLE_SLOT = 5;

	@Test
	void primaryKeyOutranksSensitiveRegardlessOfBadgeOrder() {
		int pkFirst = roleSlot(List.of("pk", "sensitive"));
		int sensitiveFirst = roleSlot(List.of("sensitive", "pk"));

		assertEquals(0, pkFirst);
		assertEquals(pkFirst, sensitiveFirst);
	}

	@Test
	void spelledOutForeignKeyBadgeStillHitsForeignKeyBucket() {
		assertEquals(1, roleSlot(List.of("foreign key")));
		assertEquals(1, roleSlot(List.of("fk")));
	}

	@Test
	void sensitiveOnlyColumnIsDemoted() {
		assertEquals(8, roleSlot(List.of("sensitive")));
		assertEquals(3, roleSlot(List.of("payload")));
	}

	private static int roleSlot(List<String> badges) {
		Candidate candidate = new Candidate(new CandidateKind.Column(), "x", "", "x", 1, null,
				new CandidateDoc("t.x", "varchar", badges, "", null, null, List.of()));
		MatchResult match = HumpMatcher.match("x", "x");
		return Ranker.key(candidate, match, 0, new Context.ColumnRef("x"))[ROLE_SLOT];
	}
}
