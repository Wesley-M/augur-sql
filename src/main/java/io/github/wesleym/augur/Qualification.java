package io.github.wesleym.augur;

/** Dialect guidance for owner/schema-qualified object names. */
public record Qualification(String ownerPrefix, boolean qualifyOnCollision) {
	public Qualification {
		ownerPrefix = Catalog.text(ownerPrefix);
	}

	public static Qualification none() {
		return new Qualification("", false);
	}

	public boolean hasOwnerPrefix() {
		return !ownerPrefix.isEmpty();
	}
}
