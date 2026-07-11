package io.github.wesleym.augur.rank;

import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.CandidateDoc;
import io.github.wesleym.augur.CandidateKind;
import io.github.wesleym.augur.Usage;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.match.HumpMatcher;
import io.github.wesleym.augur.match.MatchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Deterministic candidate ranker using printable lexicographic integer keys. */
public final class Ranker {
	private Ranker() { }

	public static List<RankedCandidate> rank(Collection<Candidate> candidates, String query, Context context,
			Usage usage) {
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}
		Usage effectiveUsage = usage == null ? Usage.none() : usage;
		List<RankedCandidate> out = new ArrayList<>();
		for (Candidate candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			String text = matchText(candidate);
			MatchResult match = HumpMatcher.match(query, text);
			if (!match.matched()) {
				continue;
			}
			int usageScore = Math.max(0, effectiveUsage.score(text.toLowerCase(Locale.ROOT)));
			Candidate highlighted = new Candidate(candidate.kind(), candidate.display(), candidate.detail(),
					candidate.insertText(), candidate.caretAfter(), match.positions(), candidate.doc());
			out.add(new RankedCandidate(highlighted, match, usageScore,
					key(candidate, match, usageScore, context)));
		}
		out.sort(Comparator.comparing(RankedCandidate::key, Arrays::compare)
				.thenComparing(r -> r.candidate().display(), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(r -> r.candidate().insertText(), String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(out);
	}

	public static int[] key(Candidate candidate, MatchResult match, int usageScore, Context context) {
		if (candidate == null) {
			throw new NullPointerException("candidate");
		}
		if (match == null) {
			throw new NullPointerException("match");
		}
		return new int[] {
				contextFit(candidate.kind(), context),
				match.tier().ordinal(),
				-match.score(),
				-Math.max(0, usageScore),
				kindBucket(candidate.kind()),
				roleBucket(candidate.doc()),
				valueShareBucket(candidate.doc()),
				matchText(candidate).length()
		};
	}

	public static String debugKey(int[] key) {
		return Arrays.toString(key == null ? new int[0] : key);
	}

	private static String matchText(Candidate candidate) {
		if (!candidate.display().isEmpty()) {
			return candidate.display();
		}
		return candidate.insertText();
	}

	private static int contextFit(CandidateKind kind, Context context) {
		if (context instanceof Context.TableRef) {
			return kind instanceof CandidateKind.Table || kind instanceof CandidateKind.View ? 0 : 4;
		}
		if (context instanceof Context.JoinTarget) {
			if (kind instanceof CandidateKind.JoinClause || kind instanceof CandidateKind.JoinPath) {
				return 0;
			}
			return kind instanceof CandidateKind.Table || kind instanceof CandidateKind.View ? 1 : 4;
		}
		if (context instanceof Context.OnCondition) {
			if (kind instanceof CandidateKind.JoinClause) {
				return 0;
			}
			return columnLike(kind) ? 1 : 3;
		}
		if (context instanceof Context.InsertColumns) {
			if (kind instanceof CandidateKind.Scaffold) {
				return 0;
			}
			return kind instanceof CandidateKind.Column ? 1 : 3;
		}
		if (context instanceof Context.GroupByRef) {
			if (kind instanceof CandidateKind.Scaffold) {
				return 0;
			}
			return columnLike(kind) ? 1 : 3;
		}
		if (context instanceof Context.ColumnRef || context instanceof Context.OrderByRef
				|| context instanceof Context.SetAssignment) {
			return columnLike(kind) ? 0 : 3;
		}
		if (context instanceof Context.ValueLiteral) {
			return kind instanceof CandidateKind.Value ? 0 : 3;
		}
		if (context instanceof Context.StatementHead) {
			return kind instanceof CandidateKind.Keyword || kind instanceof CandidateKind.Scaffold ? 0 : 3;
		}
		if (context instanceof Context.Quiet) {
			return 9;
		}
		return 1;
	}

	private static boolean columnLike(CandidateKind kind) {
		return kind instanceof CandidateKind.Column || kind instanceof CandidateKind.Alias
				|| kind instanceof CandidateKind.Expansion;
	}

	private static int kindBucket(CandidateKind kind) {
		if (kind instanceof CandidateKind.Alias) {
			return 0;
		}
		if (kind instanceof CandidateKind.Column) {
			return 1;
		}
		if (kind instanceof CandidateKind.Table) {
			return 2;
		}
		if (kind instanceof CandidateKind.View) {
			return 3;
		}
		if (kind instanceof CandidateKind.JoinClause) {
			return 4;
		}
		if (kind instanceof CandidateKind.JoinPath) {
			return 5;
		}
		if (kind instanceof CandidateKind.Value) {
			return 6;
		}
		if (kind instanceof CandidateKind.Keyword) {
			return 7;
		}
		if (kind instanceof CandidateKind.Scaffold) {
			return 8;
		}
		return 9;
	}

	private static int roleBucket(CandidateDoc doc) {
		boolean primaryKey = false;
		boolean foreignKey = false;
		boolean sensitive = false;
		for (String badge : doc.badges()) {
			String normalized = badge.toLowerCase(Locale.ROOT);
			if (normalized.contains("pk") || normalized.contains("primary") || normalized.equals("key")) {
				primaryKey = true;
			} else if (normalized.contains("fk") || normalized.contains("foreign")
					|| normalized.contains("reference")) {
				foreignKey = true;
			} else if (normalized.contains("sensitive")) {
				sensitive = true;
			}
		}
		// Order-independent priority: a primary key always outranks a foreign key,
		// which outranks a neutral column, which outranks a sensitive one.
		if (primaryKey) {
			return 0;
		}
		if (foreignKey) {
			return 1;
		}
		return sensitive ? 8 : 3;
	}

	private static int valueShareBucket(CandidateDoc doc) {
		if (doc.topValues().isEmpty()) {
			return 0;
		}
		return -(int) Math.round(doc.topValues().get(0).share() * 10_000.0);
	}
}
