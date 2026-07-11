package io.github.wesleym.augur.samples;

import io.github.wesleym.augur.Augur;
import io.github.wesleym.augur.Candidate;
import io.github.wesleym.augur.Completion;
import io.github.wesleym.augur.insert.InsertionEdit;

import java.util.Optional;

/** Minimal dependency-free adapter shape a host editor can copy. */
public final class ReferenceAdapter {
	private final Augur augur;

	public ReferenceAdapter(Augur augur) {
		this.augur = augur;
	}

	public Completion complete(EditorState editor) {
		return augur.complete(editor.text(), editor.caretOffset());
	}

	public Optional<EditorState> acceptFirst(EditorState editor) {
		Completion completion = complete(editor);
		return completion.firstEdit().map(edit -> apply(editor, edit));
	}

	public EditorState accept(EditorState editor, Completion completion, Candidate candidate) {
		return apply(editor, completion.edit(candidate));
	}

	private static EditorState apply(EditorState editor, InsertionEdit edit) {
		return new EditorState(edit.applyTo(editor.text()), edit.absoluteCaret());
	}

	public record EditorState(String text, int caretOffset) {
		public EditorState {
			text = text == null ? "" : text;
			if (caretOffset < 0 || caretOffset > text.length()) {
				throw new IllegalArgumentException("caretOffset out of range: " + caretOffset);
			}
		}

		public static EditorState atEnd(String text) {
			String value = text == null ? "" : text;
			return new EditorState(value, value.length());
		}
	}
}
