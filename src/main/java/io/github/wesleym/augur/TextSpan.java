package io.github.wesleym.augur;

/** Half-open text span `[start, end)` replaced when a candidate is accepted. */
public record TextSpan(int start, int end) {
	public TextSpan {
		if (start < 0 || end < start) {
			throw new IllegalArgumentException("invalid text span: " + start + ".." + end);
		}
	}

	public static TextSpan of(int start, int end) {
		return new TextSpan(start, end);
	}

	public int length() {
		return end - start;
	}
}
