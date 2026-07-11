package io.github.wesleym.augur.scope;

import java.util.Objects;

/** One table-like source visible to column completion. */
public record ScopeSource(String name, String alias, SourceKind kind, int start, int end, int depth) {
	public ScopeSource {
		name = text(name);
		alias = text(alias);
		kind = Objects.requireNonNullElse(kind, SourceKind.TABLE);
		if (start < 0 || end < start) {
			throw new IllegalArgumentException("invalid source span: " + start + ".." + end);
		}
		depth = Math.max(0, depth);
	}

	public String qualifier() {
		return alias.isEmpty() ? name : alias;
	}

	public boolean matches(String qualifier) {
		String wanted = text(qualifier);
		return this.qualifier().equalsIgnoreCase(wanted) || name.equalsIgnoreCase(wanted);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
