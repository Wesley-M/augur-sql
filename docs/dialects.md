# Dialect Authoring

`Dialect` is the single spec object for SQL behavior that varies by database.
It is intentionally data-driven: hosts should describe behavior through the
builder instead of forking lexer, generator, or insertion code.

## Built-ins

Augur ships these handles in `Dialects`:

- `ANSI`
- `POSTGRES`
- `MYSQL`
- `SQLSERVER`
- `SQLANYWHERE`
- `SQLITE`
- `H2`
- `GENERIC`

`Dialects.builtIns()` returns the list used by the dialect compatibility tests.
If a new built-in is added, add it there so it is covered by the same checks.

## Spec Fields

A dialect contains:

- `name`: display/debug name.
- `lex`: `LexProfile` for identifier quotes, comments, and identifier
  characters.
- `keywords`: token-level keyword vocabulary.
- `identifiers`: reserved words and unquoted identifier case folding.
- `qualification`: owner/schema qualification preferences for future
  generators.
- `pagination`: preferred pagination syntax for future generators.
- `types`: ordered SQL type-name rules mapped to Augur `TypeFamily` values.

The legacy constructors remain supported:

```java
new Dialect("Vendor");
new Dialect("Vendor", LexProfile.ansi(), Dialect.ansiKeywords());
```

For new code, prefer the builder:

```java
Dialect acme = Dialect.builder("AcmeDB")
        .lex(LexProfile.ansi()
                .withIdentifierQuote('`', '`')
                .withLineComment("#")
                .withDollarInIdentifiers(true))
        .keywords(KeywordSet.ansi().with("UPSERT", "RETURNING"))
        .identifierCase(IdentifierCase.LOWER)
        .qualification(new Qualification("app", true))
        .pagination(Pagination.limitOffset())
        .types(TypeMap.ansi().with(".*\\bjson_document\\b.*", TypeFamily.TEXT))
        .build();
```

## Identifier Rules

`keywords` controls tokenization. `IdentifierRules` controls whether an
identifier needs quoting and how unquoted identifiers are folded.

By default, reserved words mirror the dialect keyword set. Override them only
when a database tokenizes a word as a keyword but still permits it as an
unquoted identifier, or the reverse.

`InsertionPlanner.identifier(...)` delegates quoting decisions to
`Dialect.needsQuoting(...)`. Quote preference comes from the first
`IdentifierQuote` in `LexProfile`.

Use:

- `withIdentifierQuote(open, close)` to add a preferred quote pair.
- `withAdditionalIdentifierQuote(open, close)` to accept another quote pair
  without changing insertion output.

## Type Maps

`TypeMap` is ordered. Rules added with `with(regex, family)` are prepended, so
custom overrides win over broader ANSI defaults:

```java
TypeMap mysqlTypes = TypeMap.ansi()
        .with(".*\\btinyint\\s*\\(\\s*1\\s*\\).*", TypeFamily.BOOLEAN);
```

Keep rules coarse. Augur needs type families for completion behavior, not a full
database type system.

## Dialect TCK

The dialect tests in `DialectTest` are the current compatibility kit:

- every built-in must expose a complete spec
- every built-in must recognize ANSI keywords
- type families must classify common text, numeric, boolean, temporal, and
  binary types
- built-ins with alternate identifier quotes must still preserve their intended
  insertion quote preference
- builder-authored custom dialects must behave the same way as built-ins

Run the full check before accepting a dialect change:

```bash
./gradlew check
```
