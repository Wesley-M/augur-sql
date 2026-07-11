package io.github.wesleym.augur;

import java.util.Objects;

/** Preferred pagination syntax for a dialect. */
public record Pagination(Style style) {
	public enum Style {
		NONE,
		LIMIT_OFFSET,
		TOP,
		FETCH_FIRST
	}

	public Pagination {
		style = Objects.requireNonNullElse(style, Style.NONE);
	}

	public static Pagination none() {
		return new Pagination(Style.NONE);
	}

	public static Pagination limitOffset() {
		return new Pagination(Style.LIMIT_OFFSET);
	}

	public static Pagination top() {
		return new Pagination(Style.TOP);
	}

	public static Pagination fetchFirst() {
		return new Pagination(Style.FETCH_FIRST);
	}
}
