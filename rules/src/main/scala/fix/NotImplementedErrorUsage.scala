package fix

import scala.meta.Position
import scalafix.v1._

class NotImplementedErrorUsage extends SyntacticRule("NotImplementedErrorUsage") {
  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tokens.collect {
      case token if token.text == "???" =>
        Patch.lint(NotImplementedErrorDiagnostic(token.pos))
    }.asPatch
}

final case class NotImplementedErrorDiagnostic(position: Position)
    extends Diagnostic {
  override def message: String = "Avoid using ??? as a not-implemented placeholder."
}
