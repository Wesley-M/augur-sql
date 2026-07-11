package io.github.wesleym.augur.gen;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.insert.InsertionPlanner;

import java.util.ArrayList;
import java.util.List;

import static io.github.wesleym.augur.gen.GeneratorSupport.effectivePlanner;

/** Generates conservative context-follow keyword candidates. */
public final class KeywordGenerator {
	private KeywordGenerator() { }

	public static List<Candidate> generate(Context context, InsertionPlanner insertion) {
		InsertionPlanner planner = effectivePlanner(insertion);
		List<String> keywords = keywords(context);
		if (keywords.isEmpty()) {
			return List.of();
		}
		String prefix = context == null ? "" : context.prefix();
		List<Candidate> out = new ArrayList<>(keywords.size());
		for (String keyword : keywords) {
			String insertText = planner.keyword(prefix, keyword);
			out.add(new Candidate(new CandidateKind.Keyword(), keyword, "keyword", insertText, insertText.length(),
					null, CandidateDoc.empty()));
		}
		return List.copyOf(out);
	}

	private static List<String> keywords(Context context) {
		if (context instanceof Context.StatementHead) {
			return List.of("select", "with", "insert", "update", "delete");
		}
		if (context instanceof Context.ExpressionRef) {
			return List.of("where", "group by", "order by", "having", "limit");
		}
		if (context instanceof Context.ColumnRef || context instanceof Context.OnCondition) {
			return List.of("and", "or", "not", "null", "case");
		}
		if (context instanceof Context.ValueLiteral) {
			return List.of("null", "true", "false");
		}
		if (context instanceof Context.TableRef) {
			return List.of("select");
		}
		if (context instanceof Context.JoinTarget) {
			return List.of("select");
		}
		return List.of();
	}
}
