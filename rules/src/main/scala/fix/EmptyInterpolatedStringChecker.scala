package fix

import scala.meta.Position
import scala.meta.Term
import scalafix.v1._

class EmptyInterpolatedStringChecker
    extends SyntacticRule("EmptyInterpolatedStringChecker") {
  private val VariableSupportingPrefixes = Set("s", "f")

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case t @ Term.Interpolate(Term.Name(prefix), _, args)
          if VariableSupportingPrefixes.contains(prefix) && args.isEmpty =>
        Patch.lint(EmptyInterpolatedStringDiagnostic(t.pos))
    }.asPatch
}

final case class EmptyInterpolatedStringDiagnostic(position: Position)
    extends Diagnostic {
  override def message: String =
    "Avoid interpolated strings with no interpolated variables."
}
