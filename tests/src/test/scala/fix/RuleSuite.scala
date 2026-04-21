package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite
import scalafix.v1._

class RuleSuite extends AbstractSyntacticRuleSuite with AnyFunSuiteLike {
  check(
    new NoOpRule,
    "NoOpRule",
    "object RuleSuiteSmokeTest",
    "object RuleSuiteSmokeTest"
  )
}

class NoOpRule extends SyntacticRule("NoOpRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = Patch.empty
}
