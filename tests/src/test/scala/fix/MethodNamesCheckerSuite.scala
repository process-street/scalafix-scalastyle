package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class MethodNamesCheckerSuite
    extends AbstractSyntacticRuleSuite
    with AnyFunSuiteLike {
  check(
    new MethodNamesChecker,
    "MethodNamesChecker testDefault",
    """
      |package foobar
      |
      |class Foobar {
      |  def foo() = 1
      |  def +() = 1
      |//  def foo+() = 1
      |  def setting_=(s: Boolean) {}
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def foo() = 1
      |  def +/* scalafix:ok */() = 1
      |//  def foo+() = 1
      |  def setting_=(s: Boolean) {}
      |}
      |""".stripMargin
  )

  check(
    new MethodNamesChecker(MethodNamesCheckerConfig(regex = "^F[o*]*$")),
    "MethodNamesChecker testNonDefault",
    """
      |package foobar
      |
      |class Foobar {
      |  def Foo() = 1
      |  def +() = 1
      |//  def Foo+() = 1
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def Foo() = 1
      |  def +/* scalafix:ok */() = 1
      |//  def Foo+() = 1
      |}
      |""".stripMargin
  )

  check(
    new MethodNamesChecker(MethodNamesCheckerConfig(ignoreRegex = "^\\+$")),
    "MethodNamesChecker testWithIgnoreRegex",
    """
      |package foobar
      |
      |class Foobar {
      |  def foo() = 1
      |  def +() = 1
      |  def -() = 1
      |//  def Foo+() = 1
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def foo() = 1
      |  def +() = 1
      |  def -/* scalafix:ok */() = 1
      |//  def Foo+() = 1
      |}
      |""".stripMargin
  )

  check(
    new MethodNamesChecker(MethodNamesCheckerConfig(ignoreOverride = true)),
    "MethodNamesChecker testIgnoreOverride",
    """
      |package foobar
      |
      |trait Bar {
      |  def +() = 1
      |  def -() = 1
      |  def &() = 1
      |}
      |
      |class Foobar extends Bar {
      |  override def +() = 1
      |  protected override def &() = 1
      |  override protected def -() = 1
      |  def bar() = 1
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |trait Bar {
      |  def +/* scalafix:ok */() = 1
      |  def -/* scalafix:ok */() = 1
      |  def &/* scalafix:ok */() = 1
      |}
      |
      |class Foobar extends Bar {
      |  override def +() = 1
      |  protected override def &() = 1
      |  override protected def -() = 1
      |  def bar() = 1
      |}
      |""".stripMargin
  )
}
