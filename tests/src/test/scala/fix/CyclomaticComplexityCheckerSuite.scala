package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class CyclomaticComplexityCheckerSuite
    extends AbstractSyntacticRuleSuite
    with AnyFunSuiteLike {
  private val max11 =
    new CyclomaticComplexityChecker(
      CyclomaticComplexityCheckerConfig(maximum = 11)
    )
  private val max3 =
    new CyclomaticComplexityChecker(
      CyclomaticComplexityCheckerConfig(maximum = 3)
    )
  private val max3NoCases =
    new CyclomaticComplexityChecker(
      CyclomaticComplexityCheckerConfig(maximum = 3, countCases = false)
    )
  private val max3Cases =
    new CyclomaticComplexityChecker(
      CyclomaticComplexityCheckerConfig(maximum = 3, countCases = true)
    )

  check(
    max11,
    "CyclomaticComplexityChecker testKO",
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar(i: Int): Int = {
      |    if (i == 1) {
      |      5
      |    } else if (i == 2) {
      |      true && false || true
      |      5 match {
      |        case 4 =>
      |        case 5 =>
      |        case _ =>
      |      }
      |    } else {
      |      var f = 0
      |      while (f > 0) {}
      |      do {} while (f > 0)
      |      for (t <- List())
      |      3
      |    }
      |  }
      |
      |  def barbar(i: Int): Int = {
      |    if (i == 1) {
      |      5
      |    } else if (i == 2) {
      |      true && false || true
      |      5 match {
      |        case 4 =>
      |        case 5 =>
      |        case _ =>
      |      }
      |    } else {
      |      var f = 0
      |      while (f > 0) {}
      |      do {} while (f > 0)
      |      for (t <- List())
      |      3
      |    }
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar/* scalafix:ok */(i: Int): Int = {
      |    if (i == 1) {
      |      5
      |    } else if (i == 2) {
      |      true && false || true
      |      5 match {
      |        case 4 =>
      |        case 5 =>
      |        case _ =>
      |      }
      |    } else {
      |      var f = 0
      |      while (f > 0) {}
      |      do {} while (f > 0)
      |      for (t <- List())
      |      3
      |    }
      |  }
      |
      |  def barbar/* scalafix:ok */(i: Int): Int = {
      |    if (i == 1) {
      |      5
      |    } else if (i == 2) {
      |      true && false || true
      |      5 match {
      |        case 4 =>
      |        case 5 =>
      |        case _ =>
      |      }
      |    } else {
      |      var f = 0
      |      while (f > 0) {}
      |      do {} while (f > 0)
      |      for (t <- List())
      |      3
      |    }
      |  }
      |}
      |""".stripMargin
  )

  check(
    max3,
    "CyclomaticComplexityChecker testEmbeddedMethods",
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar(i: Int): Int = {
      |    // 1 for method, 2 for inner methods and 3 for if/elseif clause
      |    def bar1(i: Int) = if (i == 1) 1 else 2
      |    def bar2(i: Int) = if (i == 2) 1 else 2
      |
      |    if (i == 1) {
      |      1
      |    } else if (i == 2) {
      |      2
      |    } else if (i == 3) {
      |      3
      |    } else {
      |      4
      |    }
      |  }
      |
      |  def barbar(i: Int): Int = {
      |    // 1 for method, 2 for inner methods and 3 for if/elseif clause
      |    def bar1(i: Int) = {
      |      if (i == 1) {
      |        1
      |      } else if (i == 2) {
      |        2
      |      } else if (i == 3) {
      |        3
      |      } else {
      |        4
      |      }
      |    }
      |
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar/* scalafix:ok */(i: Int): Int = {
      |    // 1 for method, 2 for inner methods and 3 for if/elseif clause
      |    def bar1(i: Int) = if (i == 1) 1 else 2
      |    def bar2(i: Int) = if (i == 2) 1 else 2
      |
      |    if (i == 1) {
      |      1
      |    } else if (i == 2) {
      |      2
      |    } else if (i == 3) {
      |      3
      |    } else {
      |      4
      |    }
      |  }
      |
      |  def barbar/* scalafix:ok */(i: Int): Int = {
      |    // 1 for method, 2 for inner methods and 3 for if/elseif clause
      |    def bar1(i: Int) = {
      |      if (i == 1) {
      |        1
      |      } else if (i == 2) {
      |        2
      |      } else if (i == 3) {
      |        3
      |      } else {
      |        4
      |      }
      |    }
      |
      |  }
      |}
      |""".stripMargin
  )

  check(
    max3,
    "CyclomaticComplexityChecker testEmbeddedClasses",
    """
      |package foobar
      |
      |class Foobar {
      |  // This is not caught by the checker. Don't know whether it should or not.
      |  val f = if (i == 1) {
      |    1
      |  } else if (i == 2) {
      |    2
      |  } else if (i == 3) {
      |    3
      |  } else {
      |    4
      |  }
      |
      |  def foobar(i: Int): Int = {
      |    // 1 for method, 2 for inner methods and 3 for if/elseif clause
      |    class Foo2 {
      |      def bar1(i: Int) = if (i == 1) 1 else 2
      |      def bar2(i: Int) = if (i == 2) 1 else 2
      |    }
      |
      |    if (i == 1) {
      |      1
      |    } else if (i == 2) {
      |      2
      |    } else if (i == 3) {
      |      3
      |    } else {
      |      4
      |    }
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  // This is not caught by the checker. Don't know whether it should or not.
      |  val f = if (i == 1) {
      |    1
      |  } else if (i == 2) {
      |    2
      |  } else if (i == 3) {
      |    3
      |  } else {
      |    4
      |  }
      |
      |  def foobar/* scalafix:ok */(i: Int): Int = {
      |    // 1 for method, 2 for inner methods and 3 for if/elseif clause
      |    class Foo2 {
      |      def bar1(i: Int) = if (i == 1) 1 else 2
      |      def bar2(i: Int) = if (i == 2) 1 else 2
      |    }
      |
      |    if (i == 1) {
      |      1
      |    } else if (i == 2) {
      |      2
      |    } else if (i == 3) {
      |      3
      |    } else {
      |      4
      |    }
      |  }
      |}
      |""".stripMargin
  )

  check(
    max3NoCases,
    "CyclomaticComplexityChecker testFlagCountCasesFalse",
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar(i: Int): Int = i match {
      |    case 1 => 1
      |    case 2 => 2
      |    case 3 => 3
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar(i: Int): Int = i match {
      |    case 1 => 1
      |    case 2 => 2
      |    case 3 => 3
      |  }
      |}
      |""".stripMargin
  )

  check(
    max3Cases,
    "CyclomaticComplexityChecker testFlagCountCasesTrue",
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar(i: Int): Int = i match {
      |    case 1 => 1
      |    case 2 => 2
      |    case 3 => 3
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foobar {
      |  def foobar/* scalafix:ok */(i: Int): Int = i match {
      |    case 1 => 1
      |    case 2 => 2
      |    case 3 => 3
      |  }
      |}
      |""".stripMargin
  )
}
