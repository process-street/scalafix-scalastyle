package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class EmptyInterpolatedStringCheckerSuite
    extends AbstractSyntacticRuleSuite
    with AnyFunSuiteLike {
  check(
    new EmptyInterpolatedStringChecker,
    "EmptyInterpolatedStringChecker testZero",
    """package foobar
      |
      |class Foobar {
      |  val foo = "foo"
      |}
      |""".stripMargin,
    """package foobar
      |
      |class Foobar {
      |  val foo = "foo"
      |}
      |""".stripMargin
  )

  check(
    new EmptyInterpolatedStringChecker,
    "EmptyInterpolatedStringChecker testCorrect",
    """package foobar
      |
      |class Foobar {
      |  val foo = "foo"
      |  val bar = s"$foo bar"
      |}
      |""".stripMargin,
    """package foobar
      |
      |class Foobar {
      |  val foo = "foo"
      |  val bar = s"$foo bar"
      |}
      |""".stripMargin
  )

  check(
    new EmptyInterpolatedStringChecker,
    "EmptyInterpolatedStringChecker testOne",
    """package foobar
      |
      |class Foobar {
      |  val foo = s"foo"
      |}
      |""".stripMargin,
    """package foobar
      |
      |class Foobar {
      |  val foo = s/* scalafix:ok */"foo"
      |}
      |""".stripMargin
  )

  check(
    new EmptyInterpolatedStringChecker,
    "EmptyInterpolatedStringChecker testMultiple",
    """package foobar
      |
      |class Foobar {
      |  val foo = s"foo"
      |  val bar = s""
      |  val baz = s"   baz     "
      |}
      |""".stripMargin,
    """package foobar
      |
      |class Foobar {
      |  val foo = s/* scalafix:ok */"foo"
      |  val bar = s/* scalafix:ok */""
      |  val baz = s/* scalafix:ok */"   baz     "
      |}
      |""".stripMargin
  )

  check(
    new EmptyInterpolatedStringChecker,
    "EmptyInterpolatedStringChecker testMix",
    """package foobar
      |
      |class Foobar {
      |  val foo = s"foo"
      |  val bar = s""
      |  val real = s"this is $foo real $bar"
      |  val baz = s"   baz     "
      |}
      |""".stripMargin,
    """package foobar
      |
      |class Foobar {
      |  val foo = s/* scalafix:ok */"foo"
      |  val bar = s/* scalafix:ok */""
      |  val real = s"this is $foo real $bar"
      |  val baz = s/* scalafix:ok */"   baz     "
      |}
      |""".stripMargin
  )

  check(
    new EmptyInterpolatedStringChecker,
    "EmptyInterpolatedStringChecker testRaw",
    """package foobar
      |
      |class Foobar {
      |  val foo = s"foo"
      |  val bar = raw"test"
      |}
      |""".stripMargin,
    """package foobar
      |
      |class Foobar {
      |  val foo = s/* scalafix:ok */"foo"
      |  val bar = raw"test"
      |}
      |""".stripMargin
  )
}
