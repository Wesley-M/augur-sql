package io.github.wesleym.augur;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Structured documentation for a candidate; hosts decide how to render it. */
public record CandidateDoc(String qualifiedName, String type, List<String> badges, String referencedTable,
		Double nullFraction, Long distinctCount, List<ValueShare> topValues) {
	private static final CandidateDoc EMPTY = new CandidateDoc("", "", List.of(), "", null, null, List.of());

	public CandidateDoc {
		qualifiedName = Catalog.text(qualifiedName);
		type = Catalog.text(type);
		badges = copyText(badges);
		referencedTable = Catalog.text(referencedTable);
		if (nullFraction != null && (Double.isNaN(nullFraction) || nullFraction < 0.0 || nullFraction > 1.0)) {
			throw new IllegalArgumentException("nullFraction must be between 0 and 1: " + nullFraction);
		}
		topValues = copy(topValues);
	}

	public static CandidateDoc empty() {
		return EMPTY;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String plainText() {
		return CandidateDocs.plainText(this);
	}

	public String html() {
		return CandidateDocs.html(this);
	}

	public Builder toBuilder() {
		return builder()
				.qualifiedName(qualifiedName)
				.type(type)
				.badges(badges)
				.referencedTable(referencedTable)
				.nullFraction(nullFraction)
				.distinctCount(distinctCount)
				.topValues(topValues);
	}

	public static final class Builder {
		private String qualifiedName;
		private String type;
		private final List<String> badges = new ArrayList<>();
		private String referencedTable;
		private Double nullFraction;
		private Long distinctCount;
		private final List<ValueShare> topValues = new ArrayList<>();

		private Builder() { }

		public Builder qualifiedName(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			return this;
		}

		public Builder type(String type) {
			this.type = type;
			return this;
		}

		public Builder badge(String badge) {
			if (badge != null && !badge.isBlank()) {
				this.badges.add(badge);
			}
			return this;
		}

		public Builder badges(Collection<String> badges) {
			if (badges != null) {
				for (String badge : badges) {
					badge(badge);
				}
			}
			return this;
		}

		public Builder referencedTable(String referencedTable) {
			this.referencedTable = referencedTable;
			return this;
		}

		public Builder nullFraction(Double nullFraction) {
			this.nullFraction = nullFraction;
			return this;
		}

		public Builder nullFraction(double nullFraction) {
			this.nullFraction = nullFraction;
			return this;
		}

		public Builder distinctCount(Long distinctCount) {
			this.distinctCount = distinctCount;
			return this;
		}

		public Builder distinctCount(long distinctCount) {
			this.distinctCount = distinctCount;
			return this;
		}

		public Builder topValue(ValueShare value) {
			if (value != null) {
				this.topValues.add(value);
			}
			return this;
		}

		public Builder topValue(String value, double share) {
			return topValue(new ValueShare(value, share, false));
		}

		public Builder topValue(String value, double share, boolean approximate) {
			return topValue(new ValueShare(value, share, approximate));
		}

		public Builder topValues(Collection<ValueShare> values) {
			if (values != null) {
				for (ValueShare value : values) {
					topValue(value);
				}
			}
			return this;
		}

		public CandidateDoc build() {
			return new CandidateDoc(qualifiedName, type, badges, referencedTable, nullFraction, distinctCount,
					topValues);
		}
	}

	private static List<String> copyText(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(values.size());
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}

	private static <T> List<T> copy(List<T> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<T> out = new ArrayList<>(values.size());
		for (T value : values) {
			if (value != null) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}
}
