package io.github.wesleym.augur.scope;

/** Where a visible SQL source came from. */
public enum SourceKind {
	TABLE,
	CTE,
	DERIVED_TABLE,
	UPDATE_TARGET,
	DELETE_TARGET,
	INSERT_TARGET
}
