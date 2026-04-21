package fix

import metaconfig.ConfDecoder
import metaconfig.Configured
import scala.meta.Pkg
import scala.meta.Position
import scalafix.v1.Configuration
import scalafix.v1.Diagnostic
import scalafix.v1.Patch
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule

final case class PackageObjectNamesCheckerConfig(
    regex: String = "^[a-z][A-Za-z]*$"
)

object PackageObjectNamesCheckerConfig {
  val default: PackageObjectNamesCheckerConfig = PackageObjectNamesCheckerConfig()

  implicit val surface
      : metaconfig.generic.Surface[PackageObjectNamesCheckerConfig] =
    metaconfig.generic.deriveSurface[PackageObjectNamesCheckerConfig]

  implicit val decoder: ConfDecoder[PackageObjectNamesCheckerConfig] =
    metaconfig.generic.deriveDecoder(default)
}

class PackageObjectNamesChecker(config: PackageObjectNamesCheckerConfig)
    extends SyntacticRule("PackageObjectNamesChecker") {
  def this() = this(PackageObjectNamesCheckerConfig.default)

  override def withConfiguration(conf: Configuration): Configured[Rule] =
    conf.conf
      .getOrElse("PackageObjectNamesChecker")(config)
      .map(new PackageObjectNamesChecker(_))

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val regex = config.regex.r

    doc.tree.collect {
      case obj: Pkg.Object if regex.findFirstIn(obj.name.value).isEmpty =>
        Patch.lint(PackageObjectNamesDiagnostic(obj.name.pos, config.regex))
    }.asPatch
  }
}

final case class PackageObjectNamesDiagnostic(position: Position, regex: String)
    extends Diagnostic {
  override def message: String =
    s"Package object name does not match regex: $regex"
}
