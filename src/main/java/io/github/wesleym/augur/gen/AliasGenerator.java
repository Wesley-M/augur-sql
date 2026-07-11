package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.github.wesleym.augur.gen.GeneratorSupport.key;

/** Generates alias candidates for visible sources. */
public final class AliasGenerator {
	private AliasGenerator() { }

	public static List<Candidate> generate(ResolvedScope scope, Context context) {
		if (!aliasContext(context) || scope == null || scope.visibleSources().isEmpty()) {
			return List.of();
		}
		List<Candidate> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (ScopeSource source : scope.visibleSources()) {
			String alias = source.qualifier();
			if (!alias.isEmpty() && seen.add(key(alias))) {
				out.add(new Candidate(new CandidateKind.Alias(), alias, "alias for " + source.name(), alias,
						alias.length(), null,
						new CandidateDoc(alias, key(source.kind().name()), List.of("alias"), source.name(),
								null, null, List.of())));
			}
		}
		return List.copyOf(out);
	}

	private static boolean aliasContext(Context context) {
		if (context instanceof Context.ColumnRef columnRef && !columnRef.qualifier().isEmpty()) {
			return false;
		}
		return context instanceof Context.ColumnRef || context instanceof Context.OnCondition
				|| context instanceof Context.GroupByRef || context instanceof Context.OrderByRef
				|| context instanceof Context.ExpressionRef;
	}
}
