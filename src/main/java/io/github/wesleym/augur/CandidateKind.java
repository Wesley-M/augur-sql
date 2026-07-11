package io.github.wesleym.augur;

import java.util.Objects;

/** Candidate categories; hosts can pattern-match for icons and styling. */
public sealed interface CandidateKind permits CandidateKind.Table, CandidateKind.View, CandidateKind.Column,
		CandidateKind.Keyword, CandidateKind.Alias, CandidateKind.JoinClause, CandidateKind.JoinPath,
		CandidateKind.Value, CandidateKind.Scaffold, CandidateKind.Expansion {
	record Table() implements CandidateKind { }
	record View() implements CandidateKind { }
	record Column() implements CandidateKind { }
	record Keyword() implements CandidateKind { }
	record Alias() implements CandidateKind { }
	record Value() implements CandidateKind { }
	record Scaffold() implements CandidateKind { }
	record Expansion() implements CandidateKind { }

	record JoinClause(Provenance provenance) implements CandidateKind {
		public JoinClause {
			provenance = Objects.requireNonNullElse(provenance, Provenance.INFERRED);
		}
	}

	record JoinPath(String via) implements CandidateKind {
		public JoinPath {
			via = Catalog.text(via);
		}
	}
}
