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

## Rule Coverage

Descriptions are adapted from the Scalastyle rules reference.

| Checker | Description | Status |
| --- | --- | --- |
| `BlockImportChecker` | Checks that block imports are not used. | planned |
| `CaseBraceChecker` | Disallow braces around `case` clause bodies. | planned |
| `ClassNamesChecker` | Check that class names match a regular expression. | implemented |
| `ClassTypeParameterChecker` | Checks that type parameters to a class match a regular expression. | planned |
| `CovariantEqualsChecker` | Check that classes and objects do not define `equals` without overriding `equals(java.lang.Object)`. | implemented |
| `CyclomaticComplexityChecker` | Checks that the cyclomatic complexity of a method does exceed a value. | implemented |
| `DeprecatedJavaChecker` | Checks that Java `@Deprecated` is not used; Scala `@deprecated` should be used instead. | planned |
| `DisallowSpaceAfterTokenChecker` | Disallow space after certain tokens. | scalafmt (`spaces` formatting) |
| `DisallowSpaceBeforeTokenChecker` | Disallow space before certain tokens. | scalafmt (`spaces` formatting) |
| `EmptyClassChecker` | If a class or trait has no members, the braces are unnecessary. | planned |
| `EmptyInterpolatedStringChecker` | The interpolation for this string literal is not necessary. | implemented |
| `EnsureSingleSpaceAfterTokenChecker` | Ensure single space after certain tokens. | scalafmt (`spaces` formatting) |
| `EnsureSingleSpaceBeforeTokenChecker` | Ensure single space before certain tokens. | scalafmt (`spaces` formatting) |
| `EqualsHashCodeChecker` | Check that if a class implements either `equals` or `hashCode`, it should implement the other. | implemented |
| `FieldNamesChecker` | Check that field names match a regular expression. | planned |
| `FileLengthChecker` | Check the number of lines in a file. | implemented |
| `FileLineLengthChecker` | Check the number of characters in a line. | scalafmt (`maxColumn`) |
| `FileTabChecker` | Check that there are no tabs in a file. | scalafmt (`indent.main` / tab handling) |
| `ForBraceChecker` | Checks that braces are used in `for` comprehensions. | planned |
| `ForLoopChecker` | Check `for` loop usage. | planned |
| `HeaderMatchesChecker` | Check the first lines of each file matches the text. | planned |
| `IfBraceChecker` | Checks that `if` statements have braces. | scalafmt (brace rewriting / formatting) |
| `IllegalImportsChecker` | Check that a class does not import certain classes. | implemented |
| `ImportGroupingChecker` | Checks that imports are grouped together, not throughout the file. | scalafmt (`SortImports`) |
| `ImportOrderChecker` | Checks that imports are grouped and ordered according to the style configuration. | scalafmt (`SortImports`) |
| `IndentationChecker` | Checks that lines are indented by a multiple of the tab size. | scalafmt (`indent.*`) |
| `LowercasePatternMatchChecker` | Checks that a case statement pattern match is not lower case, as this can cause confusion. | implemented |
| `MagicNumberChecker` | Checks for use of magic numbers. | implemented |
| `MethodArgumentNamesChecker` | Check that method argument names match a regular expression. | planned |
| `MethodLengthChecker` | Checks that methods do not exceed a maximum length. | implemented |
| `MethodNamesChecker` | Check that method names match a regular expression. | implemented |
| `MultipleStringLiteralsChecker` | Checks that a string literal does not appear multiple times. | planned |
| `NamedArgumentChecker` | Checks that argument literals are named. | planned |
| `NewLineAtEofChecker` | Checks that a file ends with a newline character. | scalafmt (trailing newline handling) |
| `NoCloneChecker` | Check that classes and objects do not define the `clone()` method. | implemented |
| `NoFinalizeChecker` | Check that classes and objects do not define the `finalize()` method. | scalafix (`DisableSyntax.noFinalize`) |
| `NoNewLineAtEofChecker` | Checks that a file does not end with a newline character. | scalafmt (trailing newline handling) |
| `NoWhitespaceAfterLeftBracketChecker` | No whitespace after left bracket `[`. | scalafmt (bracket spacing) |
| `NoWhitespaceBeforeLeftBracketChecker` | No whitespace before left bracket `[`. | scalafmt (bracket spacing) |
| `NoWhitespaceBeforeRightBracketChecker` | No whitespace before right bracket `]`. | scalafmt (bracket spacing) |
| `NonASCIICharacterChecker` | Some editors are unfriendly to non-ascii characters. | planned |
| `NotImplementedErrorUsage` | Checks that the code does not have `???` operators. | implemented |
| `NullChecker` | Check that `null` is not used. | scalafix (`DisableSyntax.noNulls`) |
| `NumberOfMethodsInTypeChecker` | Check that a class, trait, or object does not have too many methods. | implemented |
| `NumberOfTypesChecker` | Checks that there are not too many types declared in a file. | implemented |
| `ObjectNamesChecker` | Check that object names match a regular expression. | implemented |
| `OverrideJavaChecker` | Checks that Java `@Override` is not used. | planned |
| `PackageNamesChecker` | Check that package names match a regular expression. | planned |
| `PackageObjectNamesChecker` | Check that package object names match a regular expression. | implemented |
| `ParameterNumberChecker` | Maximum number of parameters for a method. | implemented |
| `PatternMatchAlignChecker` | Check that pattern match arrows align. | scalafmt (`align.tokens`) |
| `ProcedureDeclarationChecker` | Use a `: Unit =` for procedure declarations. | scalafmt (`ProcedureSyntax`) |
| `PublicMethodsHaveTypeChecker` | Check that a method has an explicit return type; it is not inferred. | implemented |
| `RedundantIfChecker` | Checks that `if` expressions are not redundant and can be replaced by a variant of the condition. | planned |
| `RegexChecker` | Checks that a regular expression cannot be matched; if found, reports this. | scalafix (`DisableSyntax.regex`) |
| `ReturnChecker` | Check that `return` is not used. | scalafix (`DisableSyntax.noReturns`) |
| `ScalaDocChecker` | Checks that the ScalaDoc on documentable members is well-formed. | planned |
| `SimplifyBooleanExpressionChecker` | Boolean expression can be simplified. | implemented |
| `SpaceAfterCommentStartChecker` | Checks a space after the start of the comment. | scalafmt (comment formatting) |
| `SpacesAfterPlusChecker` | Check that the plus sign is followed by a space. | scalafmt (`spaces` formatting) |
| `SpacesBeforePlusChecker` | Check that the plus sign is preceded by a space. | scalafmt (`spaces` formatting) |
| `StructuralTypeChecker` | Check that structural types are not used. | implemented |
| `TodoCommentChecker` | Check for use of TODO/FIXME single line comments. | planned |
| `TokenChecker` | Checks that a regular expression cannot be matched in a token; if found, reports this. | scalafix (`DisableSyntax.regex`) |
| `UnderscoreImportChecker` | Avoid wildcard imports. | planned |
| `UppercaseLChecker` | Checks that if a long literal is used, then an uppercase `L` is used. | scalafmt (`literals.long = Upper`) |
| `VarFieldChecker` | Checks that classes and objects do not define mutable fields. | scalafix (`DisableSyntax.noVars`) |
| `VarLocalChecker` | Checks that functions do not define mutable variables. | scalafix (`DisableSyntax.noVars`) |
| `WhileBraceChecker` | Checks that `while` statements use braces. | planned |
| `WhileChecker` | Checks that `while` is not used. | scalafix (`DisableSyntax.noWhileLoops`) |
| `XmlLiteralChecker` | Check that XML literals are not used. | scalafix (`DisableSyntax.noXml`) |

Notes:

- `implemented` means the rule lives in this repo and has migrated tests.
- `planned` means the rule is still intended for this repo but has not been implemented yet.
- `IllegalImportsChecker` is currently partial: it supports `illegalImports`, but not upstream `exemptImports`.

Rules beyond scalastyle:

| Rule | Description | Kind |
| --- | --- | --- |
| `NamedNilNoneArgumentsChecker` | Names positional `Nil` and `None` arguments after their parameter. | semantic rewrite |

## Installation

Once published, install the rules in your sbt build with:

```scala
ThisBuild / scalafixDependencies +=
  "st.process" %% "scalafix-scalastyle" % "VERSION"
```

Then enable whichever rules you want in `.scalafix.conf`.

For Scala 3 builds, use the Scala 2.13 artifact explicitly:

```scala
ThisBuild / scalafixDependencies +=
  ("st.process" %% "scalafix-scalastyle" % "VERSION")
    .cross(CrossVersion.for3Use2_13)
```

This matches the current Scalafix ecosystem pattern: the rules are published for Scala 2.12 and 2.13, and Scala 3 consumers use the 2.13 artifact.

## Implemented Rules

### ClassNamesChecker

Flags class names that do not match a configured regex.

```hocon
rules = [
  ClassNamesChecker
]

ClassNamesChecker.regex = "^[A-Z][A-Za-z]*$"
```

### CovariantEqualsChecker

Flags types that define a covariant `equals` method without also defining `equals(Object)` or `equals(Any)`.

```hocon
rules = [
  CovariantEqualsChecker
]
```

### CyclomaticComplexityChecker

Flags methods whose cyclomatic complexity exceeds a configured maximum.

```hocon
rules = [
  CyclomaticComplexityChecker
]

CyclomaticComplexityChecker.maximum = 13
CyclomaticComplexityChecker.countCases = true
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

```hocon
rules = [
  EmptyInterpolatedStringChecker
]
```

### EqualsHashCodeChecker

Flags types that define `equals` without `hashCode`, or `hashCode` without `equals`.

```hocon
rules = [
  EqualsHashCodeChecker
]
```

### FileLengthChecker

Flags files whose total line count exceeds a configured maximum.

```hocon
rules = [
  FileLengthChecker
]

FileLengthChecker.maxFileLength = 800
```

### IllegalImportsChecker

Flags imports that match configured forbidden import prefixes.

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

### LowercasePatternMatchChecker

Flags simple lowercase pattern matches such as `case lc => ...`, where the intent is often a stable identifier match that should have been written with backticks.

Examples that are allowed:

- ``case `lc` => ...``
- `case s: Int => ...`
- `case List(x, y) => ...`

```hocon
rules = [
  LowercasePatternMatchChecker
]
```

### MagicNumberChecker

Flags numeric literals that are not in the configured ignore list. Literals anywhere inside a `val` right-hand side — including arithmetic, method chains, constructor arguments, and function calls — are treated as named constants and are not flagged. The same exemption applies to default parameter values.

```hocon
rules = [
  MagicNumberChecker
]

MagicNumberChecker.ignore = "-1,0,1,2,3"
```

### MethodLengthChecker

Flags methods whose body length exceeds a configured maximum.

```hocon
rules = [
  MethodLengthChecker
]

MethodLengthChecker.maxLength = 50
MethodLengthChecker.ignoreComments = false
MethodLengthChecker.ignoreEmpty = false
```

### MethodNamesChecker

Flags method names that do not match a configured regex.

```hocon
rules = [
  MethodNamesChecker
]

MethodNamesChecker.regex = "^[a-z][A-Za-z0-9]*(_=)?$"
MethodNamesChecker.ignoreRegex = "^$"
MethodNamesChecker.ignoreOverride = false
```

### NamedNilNoneArgumentsChecker

Rewrites a positional `Nil` or `None` argument to a named one, so a call says which parameter it fills: `Config(Nil, None)` becomes `Config(items = Nil, defaultValue = None)`.

It is a semantic rule, so the build needs SemanticDB (`semanticdbEnabled := true`). It is a rewrite rather than a lint: `scalafix --check` fails with the diff, and `scalafix` applies it.

```hocon
rules = [
  NamedNilNoneArgumentsChecker
]
```

A wrong name can compile and silently move a value to another parameter, so the rule names an argument only when it is sure. It must resolve the call to a Scala method, every overload the call could reach must agree on the name, and the parameter's declared type must be able to hold the value (`Option` for `None`, a supertype of `List` for `Nil`, or a type parameter). It leaves alone:

- an argument list with a single argument, such as `Future.successful(None)`
- a list with a repeated parameter, such as `List(None, x)`
- Java methods
- `using` and implicit argument lists, and any call whose method has one at or before the list being named, since Scala 3 lets a call leave it out
- extension methods, in any form of call, and methods the index cannot place, such as `export` forwarders
- a method reached through an implicit class or conversion on the receiver, since naming an argument can make the receiver's own method take the call instead. On Scala 3 the receiver carries a synthetic for the conversion; on Scala 2 the method must belong to the receiver's static type or a parent, read from a name or a `new`, or the call is left alone
- any call whose written argument lists do not fit the method's parameter lists one for one
- a parameter whose type it cannot read, a type alias included
- anything it cannot resolve, such as a call through a function value

`Foo(...)` on an object calls an `apply` that may come from a parent, an implicit conversion or an extension. On Scala 3 the rule names from the method the compiler recorded for the call (its synthetic), and leaves the call alone when there is no such record, as for `Foo[A](...)` with explicit type arguments. Scala 2 records no synthetics by default, so there the rule collects every `apply` the object declares or inherits, and leaves the call alone when they disagree. One Scala 2 gap remains: an `apply` that an implicit class adds to `Foo.type` is invisible there, so such a call is named after the companion's own `apply`. That fails to compile rather than moving a value, and `// scalafix:ok` silences it.

On Scala 3, Scalafix's symbol table cannot read a symbol defined in another file, because Scala 3 classfiles carry TASTy rather than Scala signatures. The rule falls back to its own index of the build's SemanticDB files, which it finds from the classpath and scalac options Scalafix passes it, including sbt's `meta` and `test-meta` directories.

The index covers only the project's own code. A library compiled by Scala 3 ships no SemanticDB, so a call into one (Tapir, Play's Scala 3 build, cats, circe, and since Scala 3.8 the standard library itself) is left alone: the rule neither rewrites it nor catches a new one.

### NoCloneChecker

Flags zero-argument `clone()` methods.

```hocon
rules = [
  NoCloneChecker
]
```

### NotImplementedErrorUsage

Flags `???` placeholders.

```hocon
rules = [
  NotImplementedErrorUsage
]
```

### NumberOfMethodsInTypeChecker

Flags types containing more than a configured number of directly declared methods.

```hocon
rules = [
  NumberOfMethodsInTypeChecker
]

NumberOfMethodsInTypeChecker.maxMethods = 30
```

### NumberOfTypesChecker

Flags files containing more than a configured number of types.

```hocon
rules = [
  NumberOfTypesChecker
]

NumberOfTypesChecker.maxTypes = 30
```

### ObjectNamesChecker

Flags object names that do not match a configured regex.

```hocon
rules = [
  ObjectNamesChecker
]

ObjectNamesChecker.regex = "^[A-Z][A-Za-z]*$"
```

### PackageObjectNamesChecker

Flags package object names that do not match a configured regex.

```hocon
rules = [
  PackageObjectNamesChecker
]

PackageObjectNamesChecker.regex = "^[a-z][A-Za-z]*$"
```

### ParameterNumberChecker

Flags methods whose parameter count exceeds a configured maximum.

```hocon
rules = [
  ParameterNumberChecker
]

ParameterNumberChecker.maxParameters = 8
```

### PublicMethodsHaveTypeChecker

Flags public methods without an explicit return type.

```hocon
rules = [
  PublicMethodsHaveTypeChecker
]

PublicMethodsHaveTypeChecker.ignoreOverride = false
```

### SimplifyBooleanExpressionChecker

Flags boolean expressions such as `b == true`, `b && false`, and `!true` which can be simplified.

```hocon
rules = [
  SimplifyBooleanExpressionChecker
]
```

### StructuralTypeChecker

Flags structural types such as `AnyRef { def close(): Unit }`.

```hocon
rules = [
  StructuralTypeChecker
]
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
- `input/` / `output/`: file-based fixtures for semantic rules, run by `SemanticRuleSuite` (`scala-2/` and `scala-3/` hold the cases whose result differs by Scala version)
- `project/TargetAxis.scala`: helper for the sbt project-matrix test wiring

## Build And Test Model

This repo uses `sbt-projectmatrix` plus `sbt-scalafix`.

Rules:

- are compiled for Scala `2.12` and `2.13`
- are implemented as syntactic rules so they can run on both Scala 2 and Scala 3 source, except `NamedNilNoneArgumentsChecker`, which needs SemanticDB

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

## Publishing

This repo is set up for `sbt-ci-release` and Sonatype Central Portal publishing.

Before the first release, you still need to do the external setup:

1. Verify the `st.process` namespace in Sonatype Central Portal.
2. Generate a Central publishing token.
3. Generate a dedicated GPG key for signing releases.
4. Add these GitHub Actions secrets to `process-street/scalafix-scalastyle`:
   - `SONATYPE_USERNAME`
   - `SONATYPE_PASSWORD`
   - `PGP_SECRET`
   - `PGP_PASSPHRASE`

Release flow:

- pushes to `main` and pull requests run the tests only
- tags like `v0.1.0` publish a normal release

The release workflow lives at [.github/workflows/release.yml](./.github/workflows/release.yml).

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

The syntactic suites use inline `check(...)` calls instead of the more file-based `input/` / `output/` scalafix-testkit layout.

That is intentional for now:

- it keeps each rule self-contained while the project is still small
- it makes porting upstream scalastyle test cases straightforward
- it still exercises the rules through scalafix testkit

A later cleanup can migrate these suites to `input/` / `output/` fixtures if that becomes more useful.

Semantic rules need compiled input, so they use those fixtures: each file under `input/` names its rule in a header comment, and `SemanticRuleSuite` compares the result with the file of the same path under `output/`.

## Compatibility Philosophy

This project is not trying to preserve scalastyle’s exact config format or exact internal implementation details.

It is trying to preserve:

- the intent of each rule
- the observable behavior covered by upstream tests
- usability on both Scala 2 and Scala 3 codebases

When a rule is already better handled by `scalafmt`, it should not be reimplemented here.

That tradeoff is deliberate: the repo prefers fewer rules with clearer ownership over reimplementing behavior that is already maintained elsewhere.
