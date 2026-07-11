package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.Profiles;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.scope.ResolvedScope;

import java.util.ArrayList;
import java.util.List;

/** Small orchestration layer for the currently implemented generators. */
public final class CandidateGenerator {
	private CandidateGenerator() { }

	public static List<Candidate> generate(Catalog catalog, Context context, InsertionPlanner insertion) {
		return generate(catalog, Profiles.empty(), ResolvedScope.empty(), context, insertion);
	}

	public static List<Candidate> generate(Catalog catalog, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		return generate(catalog, Profiles.empty(), scope, context, insertion);
	}

	public static List<Candidate> generate(Catalog catalog, Profiles profiles, ResolvedScope scope, Context context,
			InsertionPlanner insertion) {
		if (context instanceof Context.Quiet) {
			return List.of();
		}
		List<Candidate> out = new ArrayList<>();
		out.addAll(JoinGenerator.generate(catalog, scope, context, insertion));
		out.addAll(InsertGenerator.generate(catalog, scope, context, insertion));
		out.addAll(ValueGenerator.generate(catalog, profiles, scope, context, insertion));
		out.addAll(GroupByGenerator.generate(context));
		if (context instanceof Context.TableRef || context instanceof Context.JoinTarget) {
			out.addAll(TableGenerator.generate(catalog, insertion));
		}
		out.addAll(AliasGenerator.generate(scope, context));
		out.addAll(ColumnGenerator.generate(catalog, scope, context, insertion));
		out.addAll(ExpansionGenerator.generate(catalog, scope, context, insertion));
		out.addAll(KeywordGenerator.generate(context, insertion));
		return List.copyOf(out);
	}
}
