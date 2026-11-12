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

object NamedNilNoneArgumentsScala3 {
  import TraitSyntax.*

  def givens(first: Int)(using items: List[Int], label: Option[Int]): Int = first
  def leadingUsing(using context: String)(first: Int, second: Int)(items: List[Int], label: Option[Int]): Int = first
  given String = ""

  extension (value: Int) {
    def described(items: List[Int], label: Option[Int]): Int = value
    def curried(first: Int, second: Int)(items: List[Int], label: Option[Int]): Int = value
    def sibling: Int = curried(2, 3)(Nil, None)
  }

  // Rewritten.
  val constructorProxy = Plain(items = Nil, label = None)
  val extensionAsMethod = described(1)(items = Nil, label = None)
  val unknownParentExactFit = Digits(Some(1), second = None, third = None)

  // Left alone.
  val explicitUsing = givens(1)(using Nil, None)
  val extensionCall = 1.described(Nil, None)
  val curriedExtensionCall = 1.curried(2, 3)(Nil, None)
  val traitExtensionCall = 1.pair(2, 3)(Nil, None)
  val omittedLeadingUsing = leadingUsing(1, 2)(Nil, None)
  val unknownParentWithDefault = Digits(Some(1), None)
}
