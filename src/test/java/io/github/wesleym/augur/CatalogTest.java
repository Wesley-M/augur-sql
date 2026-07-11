package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {
	@Test
	void builderCreatesImmutableLookupSnapshot() {
		Catalog catalog = Catalog.builder()
				.table("appointment", t -> t
						.column("id", "integer", c -> c.primaryKey().typeFamily(TypeFamily.INTEGER))
						.column("patient_id", "integer",
								c -> c.referencing("patient", "id", Provenance.DECLARED))
						.column("status", "varchar")
						.rowCount(48_210))
				.view("active_patient", t -> t
						.column("email", "varchar", c -> c.role(ColumnRole.SENSITIVE))
						.junction(false))
				.build();

		Catalog.Table appointment = catalog.table("APPOINTMENT").orElseThrow();
		Catalog.Column id = appointment.column("ID").orElseThrow();
		Catalog.Column patientId = appointment.column("patient_id").orElseThrow();
		Catalog.Table activePatient = catalog.table("active_patient").orElseThrow();

		assertEquals(2, catalog.tables().size());
		assertEquals("appointment", appointment.name());
		assertTrue(appointment.hasKnownRowCount());
		assertEquals(48_210, appointment.rowCount());
		assertTrue(id.primaryKey());
		assertEquals(ColumnRole.KEY, id.role());
		assertEquals(TypeFamily.INTEGER, id.typeFamily());
		assertTrue(patientId.foreignKey());
		assertEquals(Provenance.DECLARED, patientId.reference().provenance());
		assertFalse(appointment.column("status").orElseThrow().foreignKey());
		assertTrue(activePatient.view());
		assertEquals(false, activePatient.junctionOverride());
		assertEquals(ColumnRole.SENSITIVE, activePatient.column("email").orElseThrow().role());
	}

	@Test
	void builderReplacesDuplicateTablesAndColumnsCaseInsensitively() {
		Catalog catalog = Catalog.builder()
				.table("patient", t -> t.column("id", "integer"))
				.table("PATIENT", t -> t.column("ID", "uuid"))
				.build();

		Catalog.Table patient = catalog.table("patient").orElseThrow();

		assertEquals(1, catalog.tables().size());
		assertEquals("PATIENT", patient.name());
		assertEquals("uuid", patient.column("id").orElseThrow().typeName());
		assertTrue(patient.column("missing").isEmpty());
		assertFalse(patient.hasKnownRowCount());
	}

	@Test
	void emptyCatalogAndNullConfigurationAreAllowed() {
		Catalog catalog = Catalog.builder()
				.table(null, null)
				.build();

		assertFalse(catalog.isEmpty());
		assertTrue(catalog.table(null).isPresent());
		assertTrue(catalog.table("").isPresent());
		assertTrue(catalog.table("other").isEmpty());
	}

	@Test
	void publicRecordsNormalizeNullCollectionsAndDefaults() {
		Catalog.Column column = new Catalog.Column(null, null, null, false, null, null);
		Catalog.Table table = new Catalog.Table(null, false, Arrays.asList(null, column), -10, null);
		Catalog.Reference reference = new Catalog.Reference(null, null, null);

		assertEquals("", column.name());
		assertEquals(TypeFamily.UNKNOWN, column.typeFamily());
		assertEquals(ColumnRole.PAYLOAD, column.role());
		assertEquals(1, table.columns().size());
		assertEquals(-1, table.rowCount());
		assertEquals(Provenance.INFERRED, reference.provenance());
	}
}
