package io.github.wesleym.augur.samples.swing;

import io.github.wesleym.augur.CandidateKind;

import java.awt.Color;

/** Maps a {@link CandidateKind} to a short label, glyph, and accent color. */
public record KindStyle(String label, String glyph, Color color) {

	public static KindStyle of(CandidateKind kind) {
		if (kind instanceof CandidateKind.Table) {
			return new KindStyle("table", "T", Theme.ACCENT);
		}
		if (kind instanceof CandidateKind.View) {
			return new KindStyle("view", "V", Theme.CYAN);
		}
		if (kind instanceof CandidateKind.Column) {
			return new KindStyle("column", "C", Theme.GREEN);
		}
		if (kind instanceof CandidateKind.Alias) {
			return new KindStyle("alias", "@", Theme.AMBER);
		}
		if (kind instanceof CandidateKind.JoinClause) {
			return new KindStyle("join", "⋈", Theme.PURPLE);
		}
		if (kind instanceof CandidateKind.JoinPath) {
			return new KindStyle("path", "⇢", Theme.PURPLE);
		}
		if (kind instanceof CandidateKind.Value) {
			return new KindStyle("value", "=", Theme.CYAN);
		}
		if (kind instanceof CandidateKind.Scaffold) {
			return new KindStyle("scaffold", "{", Theme.PINK);
		}
		if (kind instanceof CandidateKind.Expansion) {
			return new KindStyle("expand", "*", Theme.GREEN);
		}
		if (kind instanceof CandidateKind.Keyword) {
			return new KindStyle("keyword", "K", Theme.SYN_KEYWORD);
		}
		return new KindStyle("item", "•", Theme.TEXT_MUTED);
	}
}
