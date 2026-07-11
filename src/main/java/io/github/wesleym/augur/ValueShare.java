package io.github.wesleym.augur;

/** One profiled value and its approximate share within a column. */
public record ValueShare(String value, double share, boolean approximate) {
	public ValueShare {
		value = Catalog.text(value);
		if (Double.isNaN(share) || share < 0.0 || share > 1.0) {
			throw new IllegalArgumentException("share must be between 0 and 1: " + share);
		}
	}
}
