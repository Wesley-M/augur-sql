package io.github.wesleym.augur.testkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaretCaseTest {
	@Test
	void parsesSingleCaretMarker() {
		CaretCase parsed = CaretCase.parse("select * from app|ointment");

		assertEquals("select * from appointment", parsed.sql());
		assertEquals(17, parsed.caretOffset());
	}

	@Test
	void rejectsMissingAndDuplicateMarkers() {
		assertThrows(NullPointerException.class, () -> CaretCase.parse(null));
		assertThrows(IllegalArgumentException.class, () -> CaretCase.parse("select"));
		assertThrows(IllegalArgumentException.class, () -> CaretCase.parse("s|elect|"));
		assertThrows(IllegalArgumentException.class, () -> new CaretCase("select", 7));
	}
}
