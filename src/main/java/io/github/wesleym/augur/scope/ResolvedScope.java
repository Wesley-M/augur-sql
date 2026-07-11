package io.github.wesleym.augur.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolved source bindings for one statement or subquery. */
public record ResolvedScope(int depth, boolean truncated, List<ScopeSource> sources,
		List<ScopeSource> inheritedSources, List<CteBinding> ctes, List<ResolvedScope> children) {
	private static final ResolvedScope EMPTY = new ResolvedScope(0, false, List.of(), List.of(), List.of(),
			List.of());

	public ResolvedScope {
		depth = Math.max(0, depth);
		sources = copy(sources);
		inheritedSources = copy(inheritedSources);
		ctes = copy(ctes);
		children = copy(children);
	}

	public static ResolvedScope empty() {
		return EMPTY;
	}

	public List<ScopeSource> visibleSources() {
		if (inheritedSources.isEmpty()) {
			return sources;
		}
		List<ScopeSource> out = new ArrayList<>(sources.size() + inheritedSources.size());
		out.addAll(sources);
		out.addAll(inheritedSources);
		return List.copyOf(out);
	}

	public Optional<ScopeSource> source(String qualifier) {
		for (ScopeSource source : sources) {
			if (source.matches(qualifier)) {
				return Optional.of(source);
			}
		}
		for (ScopeSource source : inheritedSources) {
			if (source.matches(qualifier)) {
				return Optional.of(source);
			}
		}
		return Optional.empty();
	}

	public Optional<CteBinding> cte(String name) {
		String wanted = text(name);
		return ctes.stream()
				.filter(cte -> cte.name().equalsIgnoreCase(wanted))
				.findFirst();
	}

	private static <T> List<T> copy(List<T> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<T> out = new ArrayList<>(values.size());
		for (T value : values) {
			if (value != null) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
