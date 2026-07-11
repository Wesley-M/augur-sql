package io.github.wesleym.augur;

import io.github.wesleym.augur.insert.InsertionEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Completion result for one `(sql, caret)` request. */
public record Completion(List<Candidate> candidates, TextSpan replaceSpan) {
	public Completion {
		candidates = copy(candidates);
		replaceSpan = Objects.requireNonNull(replaceSpan, "replaceSpan");
	}

	public boolean isEmpty() {
		return candidates.isEmpty();
	}

	public Optional<Candidate> first() {
		return candidates.stream().findFirst();
	}

	public InsertionEdit edit(Candidate candidate) {
		Candidate value = Objects.requireNonNull(candidate, "candidate");
		return new InsertionEdit(replaceSpan, value.insertText(), value.caretAfter());
	}

	public Optional<InsertionEdit> firstEdit() {
		return first().map(this::edit);
	}

	public String apply(String sql, Candidate candidate) {
		return edit(candidate).applyTo(sql);
	}

	public String applyFirst(String sql) {
		return firstEdit().map(edit -> edit.applyTo(sql)).orElse(Catalog.text(sql));
	}

	private static List<Candidate> copy(List<Candidate> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<Candidate> out = new ArrayList<>(values.size());
		for (Candidate value : values) {
			if (value != null) {
				out.add(value);
			}
		}
		return List.copyOf(out);
	}
}
