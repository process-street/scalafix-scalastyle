package fix

import metaconfig.ConfDecoder
import metaconfig.Configured
import scala.meta.Importee
import scala.meta.Importer
import scala.meta.Position
import scalafix.v1.Configuration
import scalafix.v1.Diagnostic
import scalafix.v1.Patch
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule

final case class IllegalImportsCheckerConfig(
    illegalImports: List[String] = List("sun._")
)

object IllegalImportsCheckerConfig {
  val default: IllegalImportsCheckerConfig = IllegalImportsCheckerConfig()

  implicit val surface: metaconfig.generic.Surface[IllegalImportsCheckerConfig] =
    metaconfig.generic.deriveSurface[IllegalImportsCheckerConfig]

  implicit val decoder: ConfDecoder[IllegalImportsCheckerConfig] =
    metaconfig.generic.deriveDecoder(default)
}

class IllegalImportsChecker(config: IllegalImportsCheckerConfig)
    extends SyntacticRule("IllegalImportsChecker") {
  def this() = this(IllegalImportsCheckerConfig.default)

  override def withConfiguration(conf: Configuration): Configured[Rule] =
    conf.conf
      .getOrElse("IllegalImportsChecker")(config)
      .map(new IllegalImportsChecker(_))

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case importer: Importer
          if IllegalImportsChecker.matches(importer, config.illegalImports) =>
        Patch.lint(IllegalImportsDiagnostic(importer.pos))
    }.asPatch
}

object IllegalImportsChecker {
  private[fix] def matches(
      importer: Importer,
      illegalImports: List[String]
  ): Boolean = {
    val normalizedPrefixes = illegalImports.map(normalizeConfiguredImport)
    importedNames(importer)
      .exists(imported => normalizedPrefixes.exists(imported.startsWith))
  }

  private def normalizeConfiguredImport(value: String): String =
    value.trim.stripSuffix("_")

  private[fix] def importedNames(importer: Importer): List[String] = {
    val prefix = importer.ref.syntax

    importer.importees.collect {
      case Importee.Name(name) =>
        s"$prefix.${name.value}"
      case Importee.Rename(name, _) =>
        s"$prefix.${name.value}"
      case _: Importee.Wildcard =>
        s"$prefix._"
    }
  }
}

final case class IllegalImportsDiagnostic(position: Position) extends Diagnostic {
  override def message: String = "Illegal import."
}
