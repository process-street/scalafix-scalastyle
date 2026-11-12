package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSemanticRuleSuite

/** Runs every semantic rule fixture under `input/` against the matching file
  * under `output/`. Each input file names its rule in a header comment.
  */
class SemanticRuleSuite extends AbstractSemanticRuleSuite with AnyFunSuiteLike {
  runAllTests()
}
