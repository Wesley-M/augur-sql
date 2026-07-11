package io.github.wesleym.augur.scope;

import java.util.ArrayList;
import java.util.List;

/** A common-table-expression binding declared by a statement. */
public record CteBinding(String name, List<String> columns, ResolvedScope queryScope, int start, int end) {
	public CteBinding {
		name = text(name);
		columns = copyText(columns);
		if (queryScope == null) {
			queryScope = ResolvedScope.empty();
		}
		if (start < 0 || end < start) {
			throw new IllegalArgumentException("invalid CTE span: " + start + ".." + end);
		}
	}

	private static List<String> copyText(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(values.size());
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
