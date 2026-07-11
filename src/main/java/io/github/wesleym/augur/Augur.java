package io.github.wesleym.augur;

import io.github.wesleym.augur.context.CaretClassifier;
import io.github.wesleym.augur.context.Context;
import io.github.wesleym.augur.gen.CandidateGenerator;
import io.github.wesleym.augur.insert.InsertionPlanner;
import io.github.wesleym.augur.rank.RankedCandidate;
import io.github.wesleym.augur.rank.Ranker;
import io.github.wesleym.augur.scope.ResolvedScope;
import io.github.wesleym.augur.scope.ScopeResolver;

import java.util.List;
import java.util.Objects;

/** Entry point for deterministic SQL completion. */
public final class Augur {
	private final Catalog catalog;
	private final Dialect dialect;
	private final Usage usage;
	private final Profiles profiles;

	private Augur(Builder builder) {
		this.catalog = Objects.requireNonNull(builder.catalog, "catalog");
		this.dialect = Objects.requireNonNull(builder.dialect, "dialect");
		this.usage = Objects.requireNonNullElse(builder.usage, Usage.none());
		this.profiles = Objects.requireNonNullElse(builder.profiles, Profiles.empty());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(Catalog catalog) {
		return builder().catalog(catalog);
	}

	public static Augur create(Catalog catalog) {
		return builder().catalog(catalog).build();
	}

	public static Augur create(Catalog catalog, Dialect dialect) {
		return builder().catalog(catalog).dialect(dialect).build();
	}

	public Catalog catalog() {
		return catalog;
	}

	public Dialect dialect() {
		return dialect;
	}

	public Usage usage() {
		return usage;
	}

	public Profiles profiles() {
		return profiles;
	}

	/**
	 * Returns completion candidates for {@code sqlText} with the caret at the end
	 * of the text.
	 */
	public Completion complete(String sqlText) {
		String sql = Catalog.text(sqlText);
		return complete(sql, sql.length());
	}

	/**
	 * Returns completion candidates for {@code sqlText} at {@code caretOffset}.
	 */
	public Completion complete(String sqlText, int caretOffset) {
		String sql = Catalog.text(sqlText);
		if (caretOffset < 0 || caretOffset > sql.length()) {
			throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
		}
		InsertionPlanner insertion = new InsertionPlanner(dialect);
		TextSpan replaceSpan = insertion.replacementSpan(sql, caretOffset);
		Context context = new CaretClassifier(dialect).classify(sql, caretOffset);
		ResolvedScope scope = new ScopeResolver(dialect).resolve(sql, caretOffset);
		List<Candidate> generated = CandidateGenerator.generate(catalog, profiles, scope, context, insertion);
		List<Candidate> ranked = Ranker.rank(generated, context.prefix(), context, usage)
				.stream()
				.map(RankedCandidate::candidate)
				.toList();
		return new Completion(ranked, replaceSpan);
	}

	/** Fluent builder for an immutable Augur instance. */
	public static final class Builder {
		private Catalog catalog;
		private Dialect dialect = Dialects.ANSI;
		private Usage usage;
		private Profiles profiles;

		private Builder() { }

		public Builder catalog(Catalog catalog) {
			this.catalog = catalog;
			return this;
		}

		public Builder dialect(Dialect dialect) {
			this.dialect = dialect;
			return this;
		}

		public Builder usage(Usage usage) {
			this.usage = usage;
			return this;
		}

		public Builder profiles(Profiles profiles) {
			this.profiles = profiles;
			return this;
		}

		public Augur build() {
			return new Augur(this);
		}
	}
}
