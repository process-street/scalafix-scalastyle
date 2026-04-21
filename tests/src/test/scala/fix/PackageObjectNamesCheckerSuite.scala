package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class PackageObjectNamesCheckerSuite
    extends AbstractSyntacticRuleSuite
    with AnyFunSuiteLike {
  check(
    new PackageObjectNamesChecker,
    "PackageObjectNamesChecker testZero",
    """
      |package foobar
      |
      |package object foobar {
      |  val foo = 1
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |package object foobar {
      |  val foo = 1
      |}
      |""".stripMargin
  )

  check(
    new PackageObjectNamesChecker,
    "PackageObjectNamesChecker testOne",
    """
      |package foobar
      |
      |package object Foobar {
      |}
      |package object Barbar {
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |package object Foobar/* scalafix:ok */ {
      |}
      |package object Barbar/* scalafix:ok */ {
      |}
      |""".stripMargin
  )

  check(
    new PackageObjectNamesChecker,
    "PackageObjectNamesChecker testPackageObject",
    """
      |package foobar
      |
      |object foobar {
      |  object barbar {
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object foobar {
      |  object barbar {
      |  }
      |}
      |""".stripMargin
  )
}
