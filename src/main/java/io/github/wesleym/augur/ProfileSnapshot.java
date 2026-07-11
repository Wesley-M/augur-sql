package io.github.wesleym.augur;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Immutable in-memory {@link Profiles} implementation for host-provided value distributions. */
public final class ProfileSnapshot implements Profiles {
	private final Map<Key, ColumnProfile> columns;

	private ProfileSnapshot(Map<Key, ColumnProfile> columns) {
		this.columns = Map.copyOf(columns);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public List<ValueShare> values(String table, String column) {
		return profile(table, column).map(ColumnProfile::values).orElse(List.of());
	}

	@Override
	public OptionalLong distinctCount(String table, String column) {
		ColumnProfile profile = columns.get(new Key(table, column));
		return profile == null || profile.distinctCount() == null
				? OptionalLong.empty()
				: OptionalLong.of(profile.distinctCount());
	}

	@Override
	public OptionalDouble nullFraction(String table, String column) {
		ColumnProfile profile = columns.get(new Key(table, column));
		return profile == null || profile.nullFraction() == null
				? OptionalDouble.empty()
				: OptionalDouble.of(profile.nullFraction());
	}

	public Optional<ColumnProfile> profile(String table, String column) {
		return Optional.ofNullable(columns.get(new Key(table, column)));
	}

	public record ColumnProfile(List<ValueShare> values, Long distinctCount, Double nullFraction) {
		public ColumnProfile {
			values = copy(values);
			if (distinctCount != null && distinctCount < 0) {
				throw new IllegalArgumentException("distinctCount must be non-negative: " + distinctCount);
			}
			if (nullFraction != null && (Double.isNaN(nullFraction) || nullFraction < 0.0 || nullFraction > 1.0)) {
				throw new IllegalArgumentException("nullFraction must be between 0 and 1: " + nullFraction);
			}
		}
	}

	public static final class Builder {
		private final Map<Key, ColumnProfileDraft> columns = new LinkedHashMap<>();

		private Builder() { }

		public Builder values(String table, String column, List<ValueShare> values) {
			draft(table, column).values = copy(values);
			return this;
		}

		public Builder distinctCount(String table, String column, long distinctCount) {
			if (distinctCount < 0) {
				throw new IllegalArgumentException("distinctCount must be non-negative: " + distinctCount);
			}
			draft(table, column).distinctCount = distinctCount;
			return this;
		}

		public Builder nullFraction(String table, String column, double nullFraction) {
			if (Double.isNaN(nullFraction) || nullFraction < 0.0 || nullFraction > 1.0) {
				throw new IllegalArgumentException("nullFraction must be between 0 and 1: " + nullFraction);
			}
			draft(table, column).nullFraction = nullFraction;
			return this;
		}

		public Builder column(String table, String column, List<ValueShare> values,
				Long distinctCount, Double nullFraction) {
			ColumnProfile profile = new ColumnProfile(values, distinctCount, nullFraction);
			ColumnProfileDraft draft = draft(table, column);
			draft.values = profile.values();
			draft.distinctCount = profile.distinctCount();
			draft.nullFraction = profile.nullFraction();
			return this;
		}

		public ProfileSnapshot build() {
			Map<Key, ColumnProfile> out = new LinkedHashMap<>();
			for (Map.Entry<Key, ColumnProfileDraft> entry : columns.entrySet()) {
				ColumnProfileDraft draft = entry.getValue();
				out.put(entry.getKey(), new ColumnProfile(draft.values, draft.distinctCount, draft.nullFraction));
			}
			return new ProfileSnapshot(out);
		}

		private ColumnProfileDraft draft(String table, String column) {
			return columns.computeIfAbsent(new Key(table, column), ignored -> new ColumnProfileDraft());
		}
	}

	private record Key(String table, String column) {
		private Key {
			table = Catalog.key(table);
			column = Catalog.key(column);
		}
	}

	private static final class ColumnProfileDraft {
		private List<ValueShare> values = List.of();
		private Long distinctCount;
		private Double nullFraction;
	}

	private static List<ValueShare> copy(List<ValueShare> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<ValueShare> out = new ArrayList<>(values.size());
		for (ValueShare value : values) {
			if (value != null) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}
}
