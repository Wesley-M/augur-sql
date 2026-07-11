package io.github.wesleym.augur;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Immutable schema snapshot supplied by a host. */
public final class Catalog {
	private final List<Table> tables;
	private final Map<String, Table> tablesByLowerName;

	private Catalog(List<Table> tables) {
		this.tables = copy(tables);
		Map<String, Table> byName = new LinkedHashMap<>();
		for (Table table : this.tables) {
			byName.put(key(table.name()), table);
		}
		this.tablesByLowerName = Map.copyOf(byName);
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<Table> tables() {
		return tables;
	}

	public Optional<Table> table(String name) {
		return Optional.ofNullable(tablesByLowerName.get(key(name)));
	}

	public boolean isEmpty() {
		return tables.isEmpty();
	}

	/** One table or view in the catalog. */
	public record Table(String name, boolean view, List<Column> columns, long rowCount, Boolean junctionOverride) {
		public Table {
			name = text(name);
			columns = copy(columns);
			rowCount = Math.max(-1, rowCount);
		}

		public Optional<Column> column(String name) {
			String wanted = key(name);
			return columns.stream()
					.filter(column -> key(column.name()).equals(wanted))
					.findFirst();
		}

		public boolean hasKnownRowCount() {
			return rowCount >= 0;
		}
	}

	/** One column in a table or view. */
	public record Column(String name, String typeName, TypeFamily typeFamily, boolean primaryKey, ColumnRole role,
			Reference reference) {
		public Column {
			name = text(name);
			typeName = text(typeName);
			typeFamily = Objects.requireNonNullElse(typeFamily, TypeFamily.UNKNOWN);
			role = Objects.requireNonNullElse(role, primaryKey ? ColumnRole.KEY : ColumnRole.PAYLOAD);
		}

		public boolean foreignKey() {
			return reference != null;
		}
	}

	/** A foreign-key edge from one column to a target table and column. */
	public record Reference(String table, String column, Provenance provenance) {
		public Reference {
			table = text(table);
			column = text(column);
			provenance = Objects.requireNonNullElse(provenance, Provenance.INFERRED);
		}
	}

	/** Fluent catalog builder. */
	public static final class Builder {
		private final Map<String, TableDraft> tables = new LinkedHashMap<>();

		private Builder() { }

		public Builder table(String name, Consumer<TableBuilder> configure) {
			return table(name, false, configure);
		}

		public Builder view(String name, Consumer<TableBuilder> configure) {
			return table(name, true, configure);
		}

		private Builder table(String name, boolean view, Consumer<TableBuilder> configure) {
			TableDraft draft = new TableDraft(text(name), view);
			if (configure != null) {
				configure.accept(new TableBuilder(draft));
			}
			tables.put(key(draft.name), draft);
			return this;
		}

		public Catalog build() {
			List<Table> out = new ArrayList<>(tables.size());
			for (TableDraft draft : tables.values()) {
				out.add(draft.toTable());
			}
			return new Catalog(out);
		}
	}

	/** Fluent builder for one table or view. */
	public static final class TableBuilder {
		private final TableDraft draft;

		private TableBuilder(TableDraft draft) {
			this.draft = draft;
		}

		public TableBuilder column(String name, String typeName) {
			return column(name, typeName, null);
		}

		public TableBuilder column(String name, String typeName, Consumer<ColumnBuilder> configure) {
			ColumnDraft column = new ColumnDraft(text(name), text(typeName));
			if (configure != null) {
				configure.accept(new ColumnBuilder(column));
			}
			draft.columns.put(key(column.name), column);
			return this;
		}

		public TableBuilder rowCount(long rowCount) {
			draft.rowCount = rowCount;
			return this;
		}

		public TableBuilder junction(boolean junction) {
			draft.junctionOverride = junction;
			return this;
		}
	}

	/** Fluent builder for one column. */
	public static final class ColumnBuilder {
		private final ColumnDraft draft;

		private ColumnBuilder(ColumnDraft draft) {
			this.draft = draft;
		}

		public ColumnBuilder primaryKey() {
			draft.primaryKey = true;
			draft.role = ColumnRole.KEY;
			return this;
		}

		public ColumnBuilder role(ColumnRole role) {
			draft.role = Objects.requireNonNullElse(role, ColumnRole.PAYLOAD);
			return this;
		}

		public ColumnBuilder typeFamily(TypeFamily typeFamily) {
			draft.typeFamily = Objects.requireNonNullElse(typeFamily, TypeFamily.UNKNOWN);
			return this;
		}

		public ColumnBuilder referencing(String table, String column, Provenance provenance) {
			draft.reference = new Reference(table, column, provenance);
			return this;
		}
	}

	private static final class TableDraft {
		private final String name;
		private final boolean view;
		private final Map<String, ColumnDraft> columns = new LinkedHashMap<>();
		private long rowCount = -1;
		private Boolean junctionOverride;

		private TableDraft(String name, boolean view) {
			this.name = name;
			this.view = view;
		}

		private Table toTable() {
			List<Column> out = new ArrayList<>(columns.size());
			for (ColumnDraft column : columns.values()) {
				out.add(column.toColumn());
			}
			return new Table(name, view, out, rowCount, junctionOverride);
		}
	}

	private static final class ColumnDraft {
		private final String name;
		private final String typeName;
		private TypeFamily typeFamily = TypeFamily.UNKNOWN;
		private boolean primaryKey;
		private ColumnRole role = ColumnRole.PAYLOAD;
		private Reference reference;

		private ColumnDraft(String name, String typeName) {
			this.name = name;
			this.typeName = typeName;
		}

		private Column toColumn() {
			return new Column(name, typeName, typeFamily, primaryKey, role, reference);
		}
	}

	static String text(String value) {
		return value == null ? "" : value;
	}

	static String key(String value) {
		return text(value).toLowerCase(Locale.ROOT);
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
