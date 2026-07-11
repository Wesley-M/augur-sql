package io.github.wesleym.augur;

import java.util.Arrays;
import java.util.Objects;

/** One completion candidate, already shaped for host rendering and insertion. */
public record Candidate(CandidateKind kind, String display, String detail, String insertText, int caretAfter,
		int[] matchedChars, CandidateDoc doc) {
	public Candidate {
		kind = Objects.requireNonNull(kind, "kind");
		display = Catalog.text(display);
		detail = Catalog.text(detail);
		insertText = Catalog.text(insertText);
		matchedChars = matchedChars == null ? new int[0] : matchedChars.clone();
		doc = doc == null ? CandidateDoc.empty() : doc;
		if (caretAfter < 0 || caretAfter > insertText.length()) {
			throw new IllegalArgumentException("caretAfter out of range: " + caretAfter);
		}
	}

	@Override
	public int[] matchedChars() {
		return matchedChars.clone();
	}

	// The record's generated equals/hashCode would compare matchedChars by array
	// identity; override so two candidates built from identical data are equal.
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		return other instanceof Candidate that
				&& caretAfter == that.caretAfter
				&& kind.equals(that.kind)
				&& display.equals(that.display)
				&& detail.equals(that.detail)
				&& insertText.equals(that.insertText)
				&& Arrays.equals(matchedChars, that.matchedChars)
				&& doc.equals(that.doc);
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, display, detail, insertText, caretAfter, Arrays.hashCode(matchedChars), doc);
	}
}
