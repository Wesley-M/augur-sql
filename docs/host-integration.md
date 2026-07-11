# Host Integration

Augur is a pure completion engine. Hosts own editor state, popup UI, database
I/O, persistence, and theme rendering.

## Minimal Loop

```java
Augur augur = Augur.builder(catalog)
        .dialect(Dialects.POSTGRES)
        .usage(usage)
        .profiles(profiles)
        .build();

Completion completion = augur.complete(editorText, caretOffset);
completion.first().ifPresent(candidate -> {
    String nextText = completion.apply(editorText, candidate);
    int nextCaret = completion.edit(candidate).absoluteCaret();
});
```

For simple demos or hosts that accept the top candidate directly:

```java
String nextText = completion.applyFirst(editorText);
```

Real editors usually keep candidate selection in the popup and call
`completion.edit(selectedCandidate)` on accept.

## Snapshots

Build immutable snapshots off the UI thread:

- `Catalog`: tables, views, columns, type names/families, roles, keys, and
  references.
- `Dialect`: choose a built-in or define a custom spec.
- `Usage`: optional ranking signal; `DecayingUsage` is the default
  implementation.
- `Profiles`: optional value-distribution signal; `ProfileSnapshot` is the
  default implementation.

Swap the `Augur` instance by reference when snapshots change. `complete()` is
synchronous and side-effect free.

## Popup Rendering

Each `Candidate` is already shaped for rendering and insertion:

- `kind()` for icons/style buckets
- `display()` for popup text
- `detail()` for right-side text
- `matchedChars()` for highlight ranges
- `doc()` for documentation panes
- `insertText()` and `caretAfter()` for acceptance

Use `candidate.doc().plainText()` or `candidate.doc().html()` as a starting
point, then replace with host-specific rendering as needed.

## Reference Adapter

`samples/java/io/github/wesleym/augur/samples/ReferenceAdapter.java` shows the
minimal adapter shape without any editor dependency. A Swing, LSP, web editor,
or RSyntaxTextArea adapter should have the same boundary:

1. read `(text, caret)` from the editor
2. call `augur.complete(text, caret)`
3. render `Candidate` values
4. apply `completion.edit(selectedCandidate)` on accept

The adapter should stay host-side. Augur should not learn about UI toolkit
types.

## Swing Sandbox

Run the desktop sandbox with:

```bash
./gradlew runSwingSandbox
```

The sandbox is intentionally plain Swing: a `JTextArea`, completion popup,
keyboard navigation, candidate acceptance, and documentation pane. It exercises
the same API boundary a host editor uses while remaining dependency-free.
