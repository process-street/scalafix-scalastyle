package fix

trait TraitSyntax {
  extension (value: Int)
    def pair(first: Int, second: Int)(items: List[Int], label: Option[Int]): Int = value
}

object TraitSyntax extends TraitSyntax

// Its companion extends a trait from the Scala 3 library, which the rule
// cannot see into.
final case class Digits(first: Option[Int], second: Option[Int], third: Option[Int] = None)

object Digits extends scala.util.FromDigits[Digits] {
  def fromDigits(digits: String): Digits = Digits(Some(digits.length), Some(0), Some(0))
}

// `Thing(1, None)` binds to the inherited `apply`, not the synthetic one.
trait ThingFactory {
  def apply(count: Int, extra: Option[Int] = None, label: Option[Int] = None): String = ""
}

final case class Thing(name: String, label: Option[Int])

object Thing extends ThingFactory

// `Other(1, None)` binds to an extension `apply` on the companion.
final case class Other(name: String, label: Option[Int])

extension (other: Other.type) {
  def apply(count: Int, extra: Option[Int] = None, label: Option[Int] = None): String = ""
}

trait WindowSyntax {
  extension (value: Int)
    def window(size: Int, label: Option[Int] = None)(items: List[Int], tag: Option[Int]): Int = value
}

object WindowSyntax extends WindowSyntax

object NamedNilNoneArgumentsScala3 {
  import TraitSyntax.*
  import WindowSyntax.*

  def givens(first: Int)(using items: List[Int], label: Option[Int]): Int = first
  def leadingUsing(using context: String)(first: Int, second: Int)(items: List[Int], label: Option[Int]): Int = first
  given String = ""
  given scala.Conversion[Config, String] = _.toString

  extension (value: Int) {
    def described(items: List[Int], label: Option[Int]): Int = value
    def curried(first: Int, second: Int)(items: List[Int], label: Option[Int]): Int = value
    def sibling: Int = curried(2, 3)(Nil, None)
  }

  // Rewritten.
  val constructorProxy = Plain(items = Nil, label = None)
  val unknownParentExactFit = Digits(Some(1), second = None, third = None)
  val unknownParentWithDefault = Digits(Some(1), second = None)
  val inheritedApply = Setting(Some("a"), fallback = None)
  val inheritedApplyWithDefaults = Thing(1, extra = None)
  val overloadedApply = Overloaded(1, label = None)
  val convertedResult: String = Config(items = Nil, defaultValue = None)

  // Left alone.
  val explicitUsing = givens(1)(using Nil, None)
  val extensionCall = 1.described(Nil, None)
  val curriedExtensionCall = 1.curried(2, 3)(Nil, None)
  val traitExtensionCall = 1.pair(2, 3)(Nil, None)
  val omittedLeadingUsing = leadingUsing(1, 2)(Nil, None)
  val extensionAsMethod = described(1)(Nil, None)
  val extensionWithDefault = 1.window(5)(Nil, None)
  val extensionApplyOnCompanion = Other(1, None)
}
