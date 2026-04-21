package fix

import metaconfig.ConfDecoder
import metaconfig.Configured
import scala.meta.Decl
import scala.meta.Defn
import scala.meta.Mod
import scala.meta.Position
import scala.meta.Tree
import scalafix.v1.Configuration
import scalafix.v1.Diagnostic
import scalafix.v1.Patch
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule

final case class MethodNamesCheckerConfig(
    regex: String = "^[a-z][A-Za-z0-9]*(_=)?$",
    ignoreRegex: String = "^$",
    ignoreOverride: Boolean = false
)

object MethodNamesCheckerConfig {
  val default: MethodNamesCheckerConfig = MethodNamesCheckerConfig()

  implicit val surface
      : metaconfig.generic.Surface[MethodNamesCheckerConfig] =
    metaconfig.generic.deriveSurface[MethodNamesCheckerConfig]

  implicit val decoder: ConfDecoder[MethodNamesCheckerConfig] =
    metaconfig.generic.deriveDecoder(default)
}

class MethodNamesChecker(config: MethodNamesCheckerConfig)
    extends SyntacticRule("MethodNamesChecker") {
  def this() = this(MethodNamesCheckerConfig.default)

  override def withConfiguration(conf: Configuration): Configured[Rule] =
    conf.conf
      .getOrElse("MethodNamesChecker")(config)
      .map(new MethodNamesChecker(_))

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val regex = config.regex.r
    val ignoreRegex = config.ignoreRegex.r

    MethodNamesChecker.methods(doc.tree).collect {
      case method
          if MethodNamesChecker.shouldLint(
            method,
            regex,
            ignoreRegex,
            config.ignoreOverride
          ) =>
        Patch.lint(MethodNamesDiagnostic(MethodNamesChecker.namePos(method), config.regex))
    }.asPatch
  }
}

object MethodNamesChecker {
  private[fix] def methods(tree: Tree): List[Tree] =
    tree.collect {
      case d: Defn.Def => d: Tree
      case d: Decl.Def => d: Tree
    }

  private[fix] def shouldLint(
      method: Tree,
      regex: scala.util.matching.Regex,
      ignoreRegex: scala.util.matching.Regex,
      ignoreOverride: Boolean
  ): Boolean = {
    if (ignoreOverride && hasOverride(method)) {
      false
    } else {
      val name = methodName(method)
      regex.findFirstIn(name).isEmpty && ignoreRegex.findFirstIn(name).isEmpty
    }
  }

  private[fix] def namePos(method: Tree): Position = method match {
    case d: Defn.Def => d.name.pos
    case d: Decl.Def => d.name.pos
  }

  private def methodName(method: Tree): String = method match {
    case d: Defn.Def => d.name.value
    case d: Decl.Def => d.name.value
  }

  private def hasOverride(method: Tree): Boolean = method match {
    case d: Defn.Def => d.mods.exists(_.is[Mod.Override])
    case d: Decl.Def => d.mods.exists(_.is[Mod.Override])
    case _ => false
  }
}

final case class MethodNamesDiagnostic(position: Position, regex: String)
    extends Diagnostic {
  override def message: String =
    s"Method name does not match regex: $regex"
}
