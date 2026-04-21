# scalafix-scalastyle Implementation Plan

## Goal

Provide a pragmatic migration path from scalastyle to scalafix/scalafmt for Scala 2 and Scala 3 codebases, in a single sbt project. Reuse existing scalafmt formatting behavior and existing scalafix built-in rules where they already cover the intent of a scalastyle rule. Reimplement only the remaining scalastyle rules as scalafix lint rules. Configuration lives in `.scalafix.conf`, not scalastyle's original config format. Newly implemented rules must pass the migrated scalastyle tests for that rule. README documents every rule with its status, coverage mapping, and any deliberate incompatibilities.

---

## Rule Categorization

### Replaced by scalafmt - 20 rules

These are formatting/whitespace concerns that are intentionally delegated to scalafmt as the migration target. This is a compatibility mapping, not a claim of byte-for-byte equivalence with scalastyle.

| Rule | Why scalafmt covers it |
|---|---|
| FileTabChecker | `indent.main` / tab handling |
| FileLineLengthChecker | `maxColumn` |
| WhitespaceEndOfLineChecker | scalafmt strips trailing whitespace |
| IndentationChecker | `indent.*` settings |
| NewLineAtEofChecker | scalafmt ensures trailing newline |
| NoNewLineAtEofChecker | (opposite of above, niche) |
| SpacesAfterPlusChecker | `spaces.inByNameTypes` etc. |
| SpacesBeforePlusChecker | same |
| NoWhitespaceBeforeLeftBracketChecker | bracket spacing |
| NoWhitespaceAfterLeftBracketChecker | bracket spacing |
| NoWhitespaceBeforeRightBracketChecker | bracket spacing |
| DisallowSpaceAfterTokenChecker | token spacing |
| DisallowSpaceBeforeTokenChecker | token spacing |
| EnsureSingleSpaceAfterTokenChecker | token spacing |
| EnsureSingleSpaceBeforeTokenChecker | token spacing |
| SpaceAfterCommentStartChecker | comment formatting |
| PatternMatchAlignChecker | `align.tokens` |
| ImportOrderChecker | `rewrite.rules = [SortImports]` |
| ImportGroupingChecker | scalafmt import grouping |
| ProcedureDeclarationChecker | `rewrite.rules = [ProcedureSyntax]` |

### Replaced by built-in scalafix rules - 9 rules

These are intentionally mapped to scalafix built-in rules instead of being reimplemented here. This is a migration mapping, not a promise that every original scalastyle option or diagnostic message is preserved.

| Rule | DisableSyntax equivalent |
|---|---|
| NullChecker | `noNulls` |
| ReturnChecker | `noReturns` |
| WhileChecker | `noWhileLoops` |
| VarFieldChecker | `noVars` |
| VarLocalChecker | `noVars` |
| NoFinalizeChecker | `noFinalize` |
| XmlLiteralChecker | `noXml` |
| RegexChecker | `regex` (custom patterns) |
| TokenChecker | `regex` (custom patterns) |

### Included (to implement) - 43 rules

All syntactic lint rules. Grouped by category:

**Naming conventions (8):**
1. `ClassNamesChecker` - class name regex, params: `regex`
2. `ObjectNamesChecker` - object name regex, params: `regex`
3. `PackageNamesChecker` - package name regex, params: `regex`
4. `PackageObjectNamesChecker` - package object name regex, params: `regex`
5. `ClassTypeParameterChecker` - type param regex, params: `regex`
6. `MethodNamesChecker` - method name regex, params: `regex`, `ignoreRegex`, `ignoreOverride`
7. `MethodArgumentNamesChecker` - arg name regex, params: `regex`, `ignoreRegex`
8. `FieldNamesChecker` - field name regex, params: `regex`, `objectFieldRegex`

**Size/complexity limits (7):**
9. `FileLengthChecker` - max file lines, params: `maxFileLength`
10. `MethodLengthChecker` - max method lines, params: `maxLength`, `ignoreComments`, `ignoreEmpty`
11. `ParameterNumberChecker` - max params, params: `maxParameters`
12. `NumberOfTypesChecker` - max types per file, params: `maxTypes`
13. `NumberOfMethodsInTypeChecker` - max methods per type, params: `maxMethods`
14. `CyclomaticComplexityChecker` - cyclomatic complexity, params: `maximum`, `countCases`
15. `MagicNumberChecker` - magic numbers, params: `ignore`

**Code smell / best practice (9):**
16. `EqualsHashCodeChecker` - equals/hashCode consistency
17. `CovariantEqualsChecker` - covariant equals
18. `NoCloneChecker` - no clone()
19. `StructuralTypeChecker` - no structural types
20. `UppercaseLChecker` - uppercase L for long literals
21. `SimplifyBooleanExpressionChecker` - simplify booleans
22. `RedundantIfChecker` - redundant if/else true/false
23. `PublicMethodsHaveTypeChecker` - explicit return types, params: `ignoreOverride`
24. `EmptyClassChecker` - empty class bodies

**Java annotation checks (2):**
25. `DeprecatedJavaChecker` - Java @Deprecated
26. `OverrideJavaChecker` - Java @Override

**Import checks (3):**
27. `IllegalImportsChecker` - forbidden imports, params: `illegalImports`
28. `UnderscoreImportChecker` - wildcard imports, params: `ignoreRegex`
29. `BlockImportChecker` - block imports

**Brace enforcement (4):**
30. `IfBraceChecker` - if braces, params: `singleLineAllowed`, `doubleLineAllowed`
31. `ForBraceChecker` - for braces, params: `singleLineAllowed`
32. `WhileBraceChecker` - while braces
33. `CaseBraceChecker` - disallow case braces

**File-level checks (2):**
34. `HeaderMatchesChecker` - file header, params: `header`, `regex`
35. `TodoCommentChecker` - TODO/FIXME comments, params: `words`

**String/literal checks (3):**
36. `MultipleStringLiteralsChecker` - repeated strings, params: `allowed`, `ignoreRegex`
37. `EmptyInterpolatedStringChecker` - empty string interpolation
38. `NonASCIICharacterChecker` - non-ASCII chars, params: `allowStringLiterals`

**Miscellaneous (5):**
39. `LowercasePatternMatchChecker` - lowercase pattern match
40. `NotImplementedErrorUsage` - forbid `???` operators
41. `ForLoopChecker` - for loop (yield) check
42. `ScalaDocChecker` - ScalaDoc validation, params: `ignoreRegex`, `ignoreTokenTypes`, `ignoreOverride`, `indentStyle`
43. `NamedArgumentChecker` - named arguments, params: `checkString`, `ignoreMethod`

---

## Compatibility Contract

- This project is a pragmatic drop-in replacement for teams migrating off scalastyle, not a byte-for-byte reimplementation.
- Reuse scalafmt and built-in scalafix rules wherever they already cover the intent of an existing scalastyle rule.
- Newly implemented rules must pass the migrated scalastyle tests for that rule.
- If an upstream scalastyle test cannot be expressed in scalafix testkit, depends on behavior delegated to scalafmt or built-in scalafix rules, or is intentionally incompatible, that case must be documented explicitly in the README.
- Any upstream test that is skipped, adapted, or dropped must be tracked explicitly rather than omitted silently.
- Prefer `SyntacticRule` implementations, but treat that as a design default, not an axiom. If a migrated test demonstrates syntax-only is insufficient, revisit that rule's implementation strategy.
- Rule behavior must work on Scala 2 and Scala 3 source code so teams can use the same linter during migration.

## Project Structure

```
scalafix-scalastyle/
├── flake.nix                    # Nix dev shell (JDK 17, sbt, coursier)
├── flake.lock
├── .gitignore
├── build.sbt                    # Root build
├── project/
│   ├── build.properties         # sbt version
│   └── plugins.sbt              # sbt-scalafix, sbt-projectmatrix
├── rules/
│   └── src/main/
│       ├── scala/fix/
│       │   ├── ClassNamesChecker.scala        # One file per rule
│       │   ├── ...
│       │   └── NamedArgumentChecker.scala
│       └── resources/META-INF/services/
│           └── scalafix.v1.Rule               # ServiceLoader registration
├── input/
│   └── src/main/scala/fix/
│       ├── ClassNamesCheckerTests.scala       # Test input files
│       ├── ...
│       └── NamedArgumentCheckerTests.scala
├── output/                                     # Empty for lint-only rules
│   └── src/main/scala/fix/
├── tests/
│   └── src/test/scala/fix/
│       └── RuleSuite.scala                    # ScalafixTestkit suite
├── .scalafix.conf                              # Example config
└── README.md                                   # Full rule table with status
```

### Key design decisions

- **Prefer syntactic rules** (extend `SyntacticRule`) so rules can run without compilation and remain usable on both Scala 2 and Scala 3 source code. If migrated tests show a rule cannot be implemented faithfully enough this way, reassess that rule explicitly.
- **One file per rule** for maintainability.
- **Each rule is independently configurable** via `.scalafix.conf` using metaconfig. This project does not preserve scalastyle's original config format.
- **Lint-only rules** (no rewrites) - they report diagnostics via `Patch.lint()`.
- **Tests are compatibility-first**: migrated scalastyle tests are the primary acceptance criteria for newly implemented rules, and local regression tests fill any gaps needed by the scalafix test harness.
- **README includes a complete table** of all 69+ scalastyle rules showing which are implemented here, which are replaced by scalafmt, which are replaced by built-in scalafix rules, and where behavior intentionally differs.

### Versions

- sbt: 1.10.11
- sbt-scalafix: 0.14.6
- sbt-projectmatrix: 0.10.1
- scalafix-core: 0.14.6 (via `V.scalafixVersion`)
- Scala cross-build: rules built for Scala 2.12.x and 2.13.x; test inputs exercised against Scala 2 and Scala 3 dialects
- JDK: 17
- Nix flake with `jdk17`, `sbt`, `coursier`

---

## Implementation Phases

### Phase 1: Project scaffold + Nix flake
- Create `flake.nix` with JDK 17 + sbt + coursier
- Create `build.sbt`, `project/plugins.sbt`, `project/build.properties`
- Create `.gitignore`
- Create `RuleSuite.scala` test harness
- Create `META-INF/services/scalafix.v1.Rule` (empty initially)
- Set up the test matrix so migrated tests can run against Scala 2 and Scala 3 input sources
- Verify `sbt compile` and `sbt tests/test` work

### Phase 2: Implement rules (batched by category)

For each rule:
1. Port the upstream scalastyle tests for the rule into the scalafix test harness
2. Record any test that must be skipped, adapted, or delegated to scalafmt or built-in scalafix rules
3. Create `rules/src/main/scala/fix/<RuleName>.scala`
4. Create any additional local regression tests needed for scalafix-specific coverage
5. Register in `META-INF/services/scalafix.v1.Rule`
6. Run `sbt tests/test` until the migrated test set passes for that rule in both Scala 2 and Scala 3 modes where applicable

**Batch order:**
1. Simple token/literal checks (UppercaseL, EmptyInterpolatedString, NotImplementedErrorUsage) - warmup
2. Naming convention checkers (8 rules) - similar structure, parameterized regex
3. Size/complexity limits (7 rules) - tree traversal + counting
4. Code smell / best practice (9 rules) - the bulk
5. Java annotation checks (2 rules)
6. Import checks (3 rules)
7. Brace enforcement (4 rules)
8. File-level checks (2 rules)
9. String/literal checks (3 rules)
10. Remaining (LowercasePatternMatch, ForLoop, ScalaDoc, NamedArgument)

### Phase 3: README + Verification
- Write README.md with complete rule mapping table and compatibility notes
- Run full test suite
- Test with example Scala 2 and Scala 3 source files
- Verify all parameters work via `.scalafix.conf`
- Audit all migrated upstream tests and document every skipped, adapted, or intentionally incompatible case

---

## Example rule implementation pattern

```scala
package fix

import metaconfig.Configured
import scalafix.v1._
import scala.meta._

case class MagicNumberCheckerConfig(
  ignore: List[String] = List("-1", "0", "1", "2")
)
object MagicNumberCheckerConfig {
  val default = MagicNumberCheckerConfig()
  implicit val surface = metaconfig.generic.deriveSurface[MagicNumberCheckerConfig]
  implicit val decoder = metaconfig.generic.deriveDecoder(default)
}

class MagicNumberChecker(config: MagicNumberCheckerConfig)
    extends SyntacticRule("MagicNumberChecker") {
  def this() = this(MagicNumberCheckerConfig.default)

  override def withConfiguration(conf: Configuration): Configured[Rule] =
    conf.conf.getOrElse("MagicNumberChecker")(config).map(new MagicNumberChecker(_))

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect {
      case t @ Lit.Int(value) if !config.ignore.contains(value.toString) =>
        Patch.lint(MagicNumberDiagnostic(t))
      case t @ Lit.Long(value) if !config.ignore.contains(value.toString) =>
        Patch.lint(MagicNumberDiagnostic(t))
      case t @ Lit.Float(value) if !config.ignore.contains(value.toString) =>
        Patch.lint(MagicNumberDiagnostic(t))
      case t @ Lit.Double(value) if !config.ignore.contains(value.toString) =>
        Patch.lint(MagicNumberDiagnostic(t))
    }.asPatch
  }
}

case class MagicNumberDiagnostic(tree: Tree) extends Diagnostic {
  override def position: Position = tree.pos
  override def message: String = s"Magic number: avoid using magic numbers"
  override def severity: LintSeverity = LintSeverity.Warning
}
```

## Example test input pattern

```scala
/*
rule = MagicNumberChecker
MagicNumberChecker.ignore = ["-1", "0", "1", "2"]
*/
package fix

object MagicNumberCheckerTests {
  val x = 0        // OK - in ignore list
  val y = 3        /* assert: MagicNumberChecker */
  val z = 1        // OK - in ignore list
}
```
