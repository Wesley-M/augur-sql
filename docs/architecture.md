# Architecture

Augur is split into a small public API and an internal pipeline.

The public API is intentionally host-shaped:

- `Catalog` is an immutable schema snapshot.
- `Dialect` is a builder-authored SQL behavior spec.
- `Usage` and `Profiles` are optional signal snapshots.
- `CandidateDoc` carries structured metadata; `CandidateDocs` renders default
  plaintext and HTML views.
- `Augur.complete(sql, caret)` returns a `Completion` with ranked `Candidate`
  values and the text span to replace.
- `Completion` exposes edit helpers so hosts can accept candidates without
  depending on internal insertion classes.

The engine pipeline is being built in layers:

- `lex`: offset-bearing SQL tokens, with comments dropped. Implemented for the
  ANSI profile with dialect-supplied identifier quotes, comments, identifier
  characters, and keywords.
- `dialect`: built-in and custom SQL behavior specs. Implemented for lexical
  profile, keyword vocabulary, identifier rules, qualification preferences,
  pagination style, type-family mapping, and parameterized built-in checks.
- `stmt`: semicolon-aware statement splitting over lexer tokens. Implemented,
  including active-statement lookup for a caret.
- `context`: caret classification that degrades gracefully on broken SQL.
  Implemented as the first heuristic pass for table, column, join, ON, insert,
  set, group/order, value, statement-head, expression, and quiet contexts.
- `scope`: aliases, CTEs, derived tables, and subquery scope. Implemented as a
  token scanner for table refs, aliases, UPDATE/DELETE/INSERT targets, CTE
  bindings, derived-table children, correlated subquery children, parent-source
  inheritance, local shadowing, and a recursion depth cap.
- `match`: deterministic fuzzy matching with highlight positions. Implemented
  with exact, case-sensitive prefix, case-insensitive prefix, hump, substring,
  and subsequence tiers.
- `rank`: printable lexicographic rank keys. Implemented with context fit,
  match tier, score, usage, kind bucket, role bucket, and length slots.
- `signals`: optional usage and profile inputs. Implemented with
  `DecayingUsage` persistence, immutable `ProfileSnapshot`, hard value-candidate
  gates, and default candidate-doc renderers.
- `insert`: dialect-aware insertion text and replacement spans. Implemented for
  current-token replacement, keyword case mirroring, and identifier quoting.
- `gen`: context-specific candidate generation. Implemented for the first
  end-to-end slices: catalog table/view candidates, scope-aware column
  candidates, visible alias candidates, star and explicit column-list expansion
  candidates, FK-backed join target and ON predicate candidates, junction
  two-hop join paths, INSERT column/value scaffolds, GROUP BY backfill,
  profiled value candidates, and conservative keyword follow candidates.

The standalone repo is the source of truth for these pieces. Connector Bridge
will become a host after the library can prove itself through tests and samples.
