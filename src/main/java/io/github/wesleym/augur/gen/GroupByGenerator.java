package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.context.Context;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Generates GROUP BY backfill scaffolds from non-aggregate select expressions. */
public final class GroupByGenerator {
	private GroupByGenerator() { }

	public static List<Candidate> generate(Context context) {
		if (!(context instanceof Context.GroupByRef groupBy) || groupBy.expressions().isEmpty()) {
			return List.of();
		}
		Set<String> seen = new LinkedHashSet<>();
		for (String expression : groupBy.expressions()) {
			if (!expression.isBlank()) {
				seen.add(expression);
			}
		}
		if (seen.isEmpty()) {
			return List.of();
		}
		String insertText = String.join(", ", seen);
		String count = seen.size() == 1 ? "1 expression" : seen.size() + " expressions";
		return List.of(new Candidate(new CandidateKind.Scaffold(), insertText, "group by select expressions",
				insertText, insertText.length(), null,
				new CandidateDoc("", "group by", List.of("group by", count), "", null, null, List.of())));
	}
}
