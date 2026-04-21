package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class IllegalImportsCheckerSuite
    extends AbstractSyntacticRuleSuite
    with AnyFunSuiteLike {
  check(
    new IllegalImportsChecker,
    "IllegalImportsChecker allows other imports",
    """package foobar
      |
      |import java.util._
      |
      |object Foobar {
      |  val foo = 1
      |}
      |""".stripMargin,
    """package foobar
      |
      |import java.util._
      |
      |object Foobar {
      |  val foo = 1
      |}
      |""".stripMargin
  )

  check(
    new IllegalImportsChecker(IllegalImportsCheckerConfig(List("sun._", "java.awt._"))),
    "IllegalImportsChecker flags imports from the configured XML",
    """package foobar
      |
      |import java.util._
      |import sun.com.foobar
      |import sun._
      |import java.awt._
      |
      |object Foobar {
      |}
      |""".stripMargin,
    """package foobar
      |
      |import java.util._
      |import sun/* scalafix:ok */.com.foobar
      |import sun/* scalafix:ok */._
      |import java/* scalafix:ok */.awt._
      |
      |object Foobar {
      |}
      |""".stripMargin
  )

  check(
    new IllegalImportsChecker(IllegalImportsCheckerConfig(List("sun._", "java.awt._"))),
    "IllegalImportsChecker respects XML-style configuration",
    """package foobar
      |
      |import java.util._
      |import sun.com.foobar
      |import sun._
      |import java.awt.{Color, _}
      |
      |object Foobar {
      |}
      |""".stripMargin,
    """package foobar
      |
      |import java.util._
      |import sun/* scalafix:ok */.com.foobar
      |import sun/* scalafix:ok */._
      |import java/* scalafix:ok */.awt.{Color, _}
      |
      |object Foobar {
      |}
      |""".stripMargin
  )
}
