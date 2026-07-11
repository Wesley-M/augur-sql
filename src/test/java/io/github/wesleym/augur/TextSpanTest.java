package io.github.wesleym.augur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextSpanTest {
	@Test
	void spanReportsLength() {
		assertEquals(3, TextSpan.of(2, 5).length());
	}

	@Test
	void spanRejectsInvalidRanges() {
		assertThrows(IllegalArgumentException.class, () -> TextSpan.of(-1, 0));
		assertThrows(IllegalArgumentException.class, () -> TextSpan.of(5, 2));
	}
}
