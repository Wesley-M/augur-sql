package io.github.wesleym.augur.samples;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Catalog;
import io.github.wesleym.augur.ColumnRole;
import io.github.wesleym.augur.Dialect;
import io.github.wesleym.augur.Dialects;
import io.github.wesleym.augur.Profiles;
import io.github.wesleym.augur.Provenance;
import io.github.wesleym.augur.ValueShare;

import java.util.List;

/**
 * Shared demo data for the runnable samples: a small Roman legion schema —
 * soldiers, generals, provinces, the battles that tie them together, the
 * tribute those battles yield, and the oath that binds a legionary to a
 * general. Foreign keys, a junction table, a view, column roles, row counts,
 * and value profiles are all here so every completion family has something to
 * chew on. Everything the samples show is driven from this one place.
 */
public final class DemoCatalog {
	private DemoCatalog() { }

	/** The dialect the samples complete against. */
	public static final Dialect DIALECT = Dialects.POSTGRES;

	public static Augur augur() {
		return Augur.builder(catalog())
				.dialect(DIALECT)
				.profiles(profiles())
				.build();
	}

	public static Catalog catalog() {
		return Catalog.builder()
				.table("legionary", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("name", "varchar")
						.column("cognomen", "varchar")
						.column("century", "varchar")
						.column("denarii", "integer", c -> c.role(ColumnRole.SENSITIVE))
						.column("enlisted_on", "date")
						.column("created_at", "timestamp", c -> c.role(ColumnRole.SYSTEM))
						.rowCount(30_142))
				.table("general", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("name", "varchar")
						.column("rank", "varchar")
						.rowCount(214))
				.table("province", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("name", "varchar")
						.column("garrison", "integer")
						.rowCount(46))
				.table("battle", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("legionary_id", "integer",
								c -> c.referencing("legionary", "id", Provenance.DECLARED))
						.column("general_id", "integer",
								c -> c.referencing("general", "id", Provenance.DECLARED))
						.column("province_id", "integer",
								c -> c.referencing("province", "id", Provenance.INFERRED))
						.column("outcome", "varchar")
						.column("fought_on", "timestamp")
						.column("legions", "integer")
						.rowCount(9_216))
				.table("tribute", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("battle_id", "integer",
								c -> c.referencing("battle", "id", Provenance.DECLARED))
						.column("denarii", "integer")
						.column("paid", "boolean")
						.rowCount(8_730))
				.table("oath", t -> t
						.column("legionary_id", "integer",
								c -> c.primaryKey().referencing("legionary", "id", Provenance.DECLARED))
						.column("general_id", "integer",
								c -> c.primaryKey().referencing("general", "id", Provenance.DECLARED)))
				.view("legendary_battle", t -> t
						.column("battle_id", "integer")
						.column("legionary_name", "varchar")
						.column("general_name", "varchar")
						.column("fought_on", "timestamp"))
				.build();
	}

	public static Profiles profiles() {
		return Profiles.builder()
				.values("battle", "outcome", List.of(
						new ValueShare("triumph", 0.44, false),
						new ValueShare("defeat", 0.29, false),
						new ValueShare("siege", 0.18, false),
						new ValueShare("truce", 0.09, false)))
				.distinctCount("battle", "outcome", 4)
				.nullFraction("battle", "outcome", 0.0)
				.values("general", "rank", List.of(
						new ValueShare("centurion", 0.41, true),
						new ValueShare("tribune", 0.22, true),
						new ValueShare("legatus", 0.14, true)))
				.distinctCount("general", "rank", 8)
				.values("tribute", "paid", List.of(
						new ValueShare("true", 0.82, false),
						new ValueShare("false", 0.18, false)))
				.distinctCount("tribute", "paid", 2)
				.nullFraction("legionary", "cognomen", 0.06)
				.build();
	}
}
