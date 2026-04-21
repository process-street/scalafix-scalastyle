package fix

import metaconfig.ConfDecoder
import metaconfig.Configured
import scala.meta.Decl
import scala.meta.Defn
import scala.meta.Position
import scala.meta.Term
import scala.meta.Tree
import scalafix.v1.Configuration
import scalafix.v1.Diagnostic
import scalafix.v1.Patch
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule

final case class CyclomaticComplexityCheckerConfig(
    maximum: Int = 10,
    countCases: Boolean = true
)

object CyclomaticComplexityCheckerConfig {
  val default: CyclomaticComplexityCheckerConfig =
    CyclomaticComplexityCheckerConfig()

  implicit val surface
      : metaconfig.generic.Surface[CyclomaticComplexityCheckerConfig] =
    metaconfig.generic.deriveSurface[CyclomaticComplexityCheckerConfig]

  implicit val decoder: ConfDecoder[CyclomaticComplexityCheckerConfig] =
    metaconfig.generic.deriveDecoder(default)
}

class CyclomaticComplexityChecker(config: CyclomaticComplexityCheckerConfig)
    extends SyntacticRule("CyclomaticComplexityChecker") {
  def this() = this(CyclomaticComplexityCheckerConfig.default)

  override def withConfiguration(conf: Configuration): Configured[Rule] =
    conf.conf
      .getOrElse("CyclomaticComplexityChecker")(config)
      .map(new CyclomaticComplexityChecker(_))

  override def fix(implicit doc: SyntacticDocument): Patch =
    CyclomaticComplexityChecker.topLevelMethods(doc.tree).collect {
      case method: Defn.Def =>
        val complexity =
          CyclomaticComplexityChecker.complexity(method, config.countCases)
        if (complexity > config.maximum) {
          Patch.lint(
            CyclomaticComplexityDiagnostic(
              method.name.pos,
              complexity.toString,
              config.maximum.toString
            )
          )
        } else {
          Patch.empty
        }
      case method: Decl.Def =>
        val complexity =
          CyclomaticComplexityChecker.complexity(method, config.countCases)
        if (complexity > config.maximum) {
          Patch.lint(
            CyclomaticComplexityDiagnostic(
              method.name.pos,
              complexity.toString,
              config.maximum.toString
            )
          )
        } else {
          Patch.empty
        }
    }.asPatch
}

object CyclomaticComplexityChecker {
  private val DefaultTokens = Set("if", "while", "do", "for")

  private[fix] def topLevelMethods(tree: Tree): List[Tree] = {
    val methods = tree.collect {
      case d: Defn.Def => d: Tree
      case d: Decl.Def => d: Tree
    }

    methods.filterNot { method =>
      methods.exists { other =>
        (other ne method) &&
        containsPosition(other.pos, method.pos)
      }
    }
  }

  private[fix] def complexity(method: Tree, countCases: Boolean): Int = {
    val root = cyclomaticComplexity(method, countCases)
    val subs =
      nestedMethodsToSubtract(method).map(cyclomaticComplexity(_, countCases)).sum
    root - subs + 1
  }

  private def cyclomaticComplexity(method: Tree, countCases: Boolean): Int = {
    method.tokens.count { token =>
      DefaultTokens.contains(token.text) ||
      token.text == "&&" ||
      token.text == "||" ||
      (if (countCases) token.text == "case" else token.text == "match")
    }
  }

  private def nestedMethodsToSubtract(method: Tree): List[Tree] =
    method.collect {
      case d: Defn.Def
          if (d ne method) && !d.body.is[Term.Block] =>
        d
      case d: Decl.Def if d ne method =>
        d
    }

  private def containsPosition(outer: Position, inner: Position): Boolean =
    outer.start <= inner.start && outer.end >= inner.end
}

final case class CyclomaticComplexityDiagnostic(
    position: Position,
    value: String,
    maximum: String
) extends Diagnostic {
  override def message: String =
    s"Cyclomatic complexity is $value, maximum allowed is $maximum."
}
