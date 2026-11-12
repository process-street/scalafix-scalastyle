# scalafix-scalastyle Agent Notes

This file captures the project-specific workflow for porting scalastyle rules into this repo.

## Project Goal

This repo is a pragmatic replacement for scalastyle.

- Prefer `scalafmt` for formatting/whitespace rules.
- Prefer built-in scalafix rules where they already cover the intent.
- Only implement custom rules here when neither of the above is a good fit.
- Compatibility is measured by behavior and migrated tests, not by preserving scalastyle's exact implementation or config format.
- Rules must work for Scala 2 and Scala 3 source so the same rule set can survive a migration.

## Before Implementing A Rule

For every requested rule:

1. Check whether `scalafmt` already covers it.
2. Check whether built-in scalafix already covers it.
3. Only implement it here if it is still needed.

Do not duplicate rules already handled well elsewhere. If a rule is intentionally delegated to `scalafmt` or built-in scalafix, update `PLAN.md` instead of adding code.

## Source Of Truth

Upstream scalastyle is the behavioral source of truth for custom rules.

Before writing a rule:

1. Read the upstream checker implementation.
2. Read the upstream test file for that checker.
3. Port the upstream cases into this repo's test harness as closely as possible.
4. Only then implement the scalafix rule until those cases pass.

Use the local checkout at:

- `/tmp/scalastyle-upstream/src/main/scala/org/scalastyle/...`
- `/tmp/scalastyle-upstream/src/test/scala/org/scalastyle/...`

## Test Style

Current project convention:

- One rule file per implementation under `rules/src/main/scala/fix/`.
- One test suite per rule under `tests/src/test/scala/fix/`.
- Register every implemented rule in `rules/src/main/resources/META-INF/services/scalafix.v1.Rule`.
- Tests use `AbstractSyntacticRuleSuite`.
- Multi-line fixtures should use `stripMargin`.

Keep tests close to upstream scalastyle tests. Do not refactor them into a different style just for aesthetics.

Semantic rules are the exception: they need compiled input, so they use the file-based fixtures in `input/` and `output/` (Scala 3-only cases under `scala-3/`), run by `SemanticRuleSuite`. Each input file names its rule in a header comment.

Important scalafix testkit detail:

- Lint markers must match the exact token position produced by scalafix.
- For infix expressions, the marker often lands on the left operand token, not at the start of the whole expression.
- For prefix or token-based checks, expected output may need inline markers in positions that look unusual.
- If a test fails, inspect obtained vs expected output and update the rule or expected fixture based on actual token placement.

## Implementation Conventions

Default to `SyntacticRule`. Use a `SemanticRule` only when the check needs to know what a name refers to, as `NamedNilNoneArgumentsChecker` does.

On Scala 3, `symbol.info` is empty for anything defined in another file: Scalafix reads symbols from classfiles, and Scala 3 classfiles carry TASTy instead of Scala signatures. Fall back to `SemanticdbIndex`, which reads the build's SemanticDB files, and test a cross-file case on the Scala 3 target. Scala 2's SemanticDB also misplaces some qualified names (`scala.Nil` on 2.12), so match on the qualifier too.

When Scala 3 inserts a method, such as `apply` in `Foo(...)`, the name's symbol is `Foo` and the method it chose is a synthetic on the call (`term.synthetics`). Use that record rather than reasoning about which candidate the compiler would pick: review found two ways the reasoning went wrong. Scala 2 records no synthetics unless `-P:semanticdb:synthetics:on` is passed.

- Use metaconfig config case classes for rules with parameters.
- Configuration lives in `.scalafix.conf`, not scalastyle XML.
- Prefer small, direct syntactic implementations over clever abstractions.
- Match the upstream behavior first; only generalize later if needed.

Be careful with scalameta API compatibility:

- This repo builds rules for Scala 2.12 and 2.13.
- Avoid Scala 3-only syntax in rule implementations.
- Some newer scalameta examples on the web do not match the API available here.
- When in doubt, compile against the actual project instead of assuming a tree shape or helper method exists.

## Partial Implementations

Partial implementations are allowed when:

- they cover the subset immediately needed for migration, and
- the missing behavior is clearly documented.

Rules must not silently claim full parity if they only implement part of the upstream behavior.

When a rule is partial:

- keep the implemented behavior covered by tests
- document the missing pieces in `README.md`
- avoid implying unsupported config or semantics

Example:

- `IllegalImportsChecker` currently supports `illegalImports`
- it does not yet implement upstream `exemptImports`
- that limitation is documented in the README

## README Requirements

For every implemented rule, update `README.md`:

1. Add it to `Current Status`.
2. Add a short `Implemented Rules` section entry.
3. Include a `.scalafix.conf` example.
4. Mention any deliberate limitation only if one actually exists.

Keep README entries terse. Do not add extra prose about config surface unless it matters.

## Verification

Use the Nix shell for verification.

Primary command:

```bash
nix develop --command sbt tests/test
```

This project already runs a matrix covering:

- Scala 2.12
- Scala 2.13
- Scala 3 source inputs

Do not consider a rule done until the full suite passes.
