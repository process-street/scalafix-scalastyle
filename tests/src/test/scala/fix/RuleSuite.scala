package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class RuleSuite extends AbstractSyntacticRuleSuite with AnyFunSuiteLike {
  check(
    new NotImplementedErrorUsage,
    "NotImplementedErrorUsage noErrors",
    """class X {
      |  val x = 0
      |}
      |""".stripMargin,
    """class X {
      |  val x = 0
      |}
      |""".stripMargin
  )

  check(
    new NotImplementedErrorUsage,
    "NotImplementedErrorUsage notImplementedErrorFound",
    """class X {
      |  val x = ???
      |}
      |""".stripMargin,
    """class X {
      |  val x = ???/* scalafix:ok */
      |}
      |""".stripMargin
  )
}
