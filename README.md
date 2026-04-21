# scalafix-scalastyle

`scalafix-scalastyle` is a scalafix rule project which reimplements selected scalastyle checks as scalafix lint rules.

The project goal is a pragmatic replacement for scalastyle.

That means:

- a Scala 2 codebase should be able to move off scalastyle without losing the most useful checks
- the same rule set should still be usable when that codebase later moves to Scala 3
- rules already covered well by `scalafmt` or built-in scalafix should not be duplicated here
- compatibility is measured by behavior and migrated tests, not by preserving scalastyle's exact implementation or config format

In other words, this repo is trying to be a practical migration path, not a byte-for-byte clone of scalastyle.

Concretely, the approach is:

- reuse `scalafmt` where a formatting rule is already covered there
- reuse built-in scalafix rules where they already cover the intent
- implement the remaining scalastyle rules as custom `SyntacticRule`s
- port the upstream scalastyle test cases for each implemented rule

The current plan and rule categorization live in [PLAN.md](./PLAN.md).

## Current Status

Implemented rules:

- `NotImplementedErrorUsage`
- `EmptyInterpolatedStringChecker`
- `LowercasePatternMatchChecker`
- `IllegalImportsChecker`
- `MethodLengthChecker`
- `CyclomaticComplexityChecker`

Each implemented rule has:

- one rule file under `rules/src/main/scala/fix/`
- one rule-specific test suite under `tests/src/test/scala/fix/`
- registration in `rules/src/main/resources/META-INF/services/scalafix.v1.Rule`

Current limitation:

- some implemented rules currently cover only the subset of upstream scalastyle behavior that was needed immediately for migration
- for example, `IllegalImportsChecker` currently implements forbidden-import matching but not the full upstream `exemptImports` feature set
- these are deliberate partial implementations, not claims of full parity
- missing pieces can be added later, but they should be treated as known gaps until explicitly implemented and tested

## Implemented Rules

### NotImplementedErrorUsage

Flags `???` placeholders.

`.scalafix.conf`:

```hocon
rules = [
  NotImplementedErrorUsage
]
```

### EmptyInterpolatedStringChecker

Flags `s` and `f` interpolated strings that do not actually interpolate any variables.

Examples that are flagged:

- `s"foo"`
- `s""`
- `f"value"`

Examples that are not flagged:

- `"foo"`
- `s"$foo bar"`
- `raw"foo"`

`.scalafix.conf`:

```hocon
rules = [
  EmptyInterpolatedStringChecker
]
```

### LowercasePatternMatchChecker

Flags simple lowercase pattern matches such as `case lc => ...`, where the intent is often a stable identifier match that should have been written with backticks.

Examples that are allowed:

- ``case `lc` => ...``
- `case s: Int => ...`
- `case List(x, y) => ...`

`.scalafix.conf`:

```hocon
rules = [
  LowercasePatternMatchChecker
]
```

### IllegalImportsChecker

Flags imports that match configured forbidden import prefixes.

`.scalafix.conf`:

```hocon
rules = [
  IllegalImportsChecker
]

IllegalImportsChecker.illegalImports = [
  "sun._",
  "java.awt._"
]
```

Current limitation:

- `exemptImports` is not implemented yet

### MethodLengthChecker

Flags methods whose body length exceeds a configured maximum.

`.scalafix.conf`:

```hocon
rules = [
  MethodLengthChecker
]

MethodLengthChecker.maxLength = 50
MethodLengthChecker.ignoreComments = false
MethodLengthChecker.ignoreEmpty = false
```

### CyclomaticComplexityChecker

Flags methods whose cyclomatic complexity exceeds a configured maximum.

`.scalafix.conf`:

```hocon
rules = [
  CyclomaticComplexityChecker
]

CyclomaticComplexityChecker.maximum = 13
CyclomaticComplexityChecker.countCases = true
```

## Repository Layout

```text
.
├── build.sbt
├── flake.nix
├── PLAN.md
├── project/
│   ├── TargetAxis.scala
│   ├── build.properties
│   └── plugins.sbt
├── rules/
│   └── src/main/
│       ├── resources/META-INF/services/scalafix.v1.Rule
│       └── scala/fix/
├── tests/
│   └── src/test/scala/fix/
├── input/
└── output/
```

Important directories:

- `rules/`: custom scalafix rule implementations
- `tests/`: ScalaTest + scalafix testkit suites
- `input/` / `output/`: reserved for a more standard scalafix-testkit file-based layout; currently mostly unused
- `project/TargetAxis.scala`: helper for the sbt project-matrix test wiring

## Build And Test Model

This repo uses `sbt-projectmatrix` plus `sbt-scalafix`.

Rules:

- are compiled for Scala `2.12` and `2.13`
- are implemented as syntactic rules so they can run on both Scala 2 and Scala 3 source

Tests:

- run against Scala `2.12`
- run against Scala `2.13`
- run against Scala `3.3.7`

The test matrix is configured in [build.sbt](./build.sbt). The important point is that the rule implementation is cross-built for Scala 2, while the test inputs are exercised against both Scala 2 and Scala 3 dialects.

## Development Environment

This repo includes a Nix dev shell in [flake.nix](./flake.nix).

It provides:

- `jdk17_headless`
- `sbt`
- `coursier`

Enter the shell with:

```bash
nix develop
```

Then run normal sbt commands inside that shell.

## Common Commands

Run the full test suite:

```bash
sbt tests/test
```

Compile everything:

```bash
sbt compile
```

If you want the interactive sbt shell for repeated work:

```bash
sbt
```

## How To Add A Rule

The current workflow is:

1. Check whether the scalastyle rule is already covered by `scalafmt` or built-in scalafix.
2. Read the upstream scalastyle implementation and tests.
3. Add a rule implementation in `rules/src/main/scala/fix/<RuleName>.scala`.
4. Register it in `rules/src/main/resources/META-INF/services/scalafix.v1.Rule`.
5. Port the upstream tests into a dedicated suite file in `tests/src/test/scala/fix/<RuleName>Suite.scala`.
6. Run `sbt tests/test` until the ported cases pass across the matrix.

When a rule is intentionally implemented as a partial subset, that should be documented in this README and kept visible in the tests rather than implied away.

Current convention:

- one rule per source file
- one suite per rule
- tests are written with `AbstractSyntacticRuleSuite`

## Notes On Test Style

The current suites use inline `check(...)` calls instead of the more file-based `input/` / `output/` scalafix-testkit layout.

That is intentional for now:

- it keeps each rule self-contained while the project is still small
- it makes porting upstream scalastyle test cases straightforward
- it still exercises the rules through scalafix testkit

A later cleanup can migrate these suites to `input/` / `output/` fixtures if that becomes more useful.

## Compatibility Philosophy

This project is not trying to preserve scalastyle’s exact config format or exact internal implementation details.

It is trying to preserve:

- the intent of each rule
- the observable behavior covered by upstream tests
- usability on both Scala 2 and Scala 3 codebases

When a rule is already better handled by `scalafmt`, it should not be reimplemented here.

That tradeoff is deliberate: the repo prefers fewer rules with clearer ownership over reimplementing behavior that is already maintained elsewhere.
