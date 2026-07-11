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
 * Shared demo data for the runnable samples: a small but realistic clinic
 * schema with foreign keys, a junction table, a view, column roles, row counts,
 * and value profiles. Everything the samples show is driven from this one place.
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
				.table("patient", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("first_name", "varchar")
						.column("last_name", "varchar")
						.column("email", "varchar", c -> c.role(ColumnRole.SENSITIVE))
						.column("phone", "varchar", c -> c.role(ColumnRole.SENSITIVE))
						.column("birth_date", "date")
						.column("created_at", "timestamp", c -> c.role(ColumnRole.SYSTEM))
						.rowCount(12_804))
				.table("provider", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("name", "varchar")
						.column("specialty", "varchar")
						.rowCount(214))
				.table("room", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("label", "varchar")
						.column("floor", "integer")
						.rowCount(38))
				.table("appointment", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("patient_id", "integer",
								c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer",
								c -> c.referencing("provider", "id", Provenance.DECLARED))
						.column("room_id", "integer",
								c -> c.referencing("room", "id", Provenance.INFERRED))
						.column("status", "varchar")
						.column("scheduled_at", "timestamp")
						.column("duration_minutes", "integer")
						.rowCount(48_210))
				.table("invoice", t -> t
						.column("id", "integer", c -> c.primaryKey())
						.column("appointment_id", "integer",
								c -> c.referencing("appointment", "id", Provenance.DECLARED))
						.column("amount_cents", "integer")
						.column("paid", "boolean")
						.rowCount(45_997))
				.table("patient_provider", t -> t
						.column("patient_id", "integer",
								c -> c.primaryKey().referencing("patient", "id", Provenance.DECLARED))
						.column("provider_id", "integer",
								c -> c.primaryKey().referencing("provider", "id", Provenance.DECLARED)))
				.view("upcoming_appointment", t -> t
						.column("appointment_id", "integer")
						.column("patient_name", "varchar")
						.column("provider_name", "varchar")
						.column("scheduled_at", "timestamp"))
				.build();
	}

	public static Profiles profiles() {
		return Profiles.builder()
				.values("appointment", "status", List.of(
						new ValueShare("scheduled", 0.48, false),
						new ValueShare("completed", 0.34, false),
						new ValueShare("cancelled", 0.11, false),
						new ValueShare("no_show", 0.07, false)))
				.distinctCount("appointment", "status", 4)
				.nullFraction("appointment", "status", 0.0)
				.values("provider", "specialty", List.of(
						new ValueShare("family_medicine", 0.29, true),
						new ValueShare("pediatrics", 0.18, true),
						new ValueShare("cardiology", 0.12, true)))
				.distinctCount("provider", "specialty", 17)
				.values("invoice", "paid", List.of(
						new ValueShare("true", 0.82, false),
						new ValueShare("false", 0.18, false)))
				.distinctCount("invoice", "paid", 2)
				.nullFraction("patient", "email", 0.06)
				.build();
	}
}
