# Signals

Signals are optional host-provided snapshots that improve ordering and value
completion. They are deliberately separate from database I/O: Augur never reads
history tables, profile tables, or files by itself.

## Usage

`Usage` is the ranking signal interface:

```java
int score(String identifierLower);
```

Hosts can implement it directly or use `DecayingUsage`, Augur's default mutable
tracker. It uses exponential decay with a 30-day half-life by default.

```java
DecayingUsage usage = new DecayingUsage();
usage.observeStatement("select * from appointment where status = 'open'");
usage.observeAcceptance(candidate);

String persisted = usage.save();
DecayingUsage restored = DecayingUsage.load(persisted);
```

The persistence format is line-oriented:

```text
augur-usage-v1<TAB>halfLifeMillis
base64url(identifierLower)<TAB>observedAtMillis<TAB>weight
```

Hosts own where this string is stored. Malformed entry lines are ignored;
unsupported headers fail fast.

## Profiles

`Profiles` is the value-distribution interface. Hosts can implement it against
their own cache or use the immutable `ProfileSnapshot` builder:

```java
Profiles profiles = Profiles.builder()
        .values("appointment", "status", List.of(
                new ValueShare("open", 0.62, false),
                new ValueShare("closed", 0.31, false)))
        .distinctCount("appointment", "status", 2)
        .nullFraction("appointment", "status", 0.10)
        .build();
```

Value completion gates are hard engine rules:

- only comparison-value contexts are eligible
- columns marked `ColumnRole.SENSITIVE` never emit value candidates
- columns with known `distinctCount > 50` never emit value candidates
- emitted values are capped at 15 per target column
- strings are quoted and escaped; numeric and boolean families remain raw

## Candidate Docs

`CandidateDoc` is structured metadata for hosts. Augur ships default renderers
for hosts that want a ready-made view:

```java
String plain = candidate.doc().plainText();
String html = candidate.doc().html();
```

The HTML renderer escapes user/catalog/profile text and emits stable class names
under `augur-doc` so hosts can style it inside their own completion popup.
