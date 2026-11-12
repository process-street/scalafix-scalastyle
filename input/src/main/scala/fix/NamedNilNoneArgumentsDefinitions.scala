/*
rule = NamedNilNoneArgumentsChecker
*/
package fix

// Defined apart from the calls in NamedNilNoneArguments, so the rule has to
// look these up through the symbol table rather than the calling file.
final case class Config(items: List[String], defaultValue: Option[String])

class Plain(val items: List[String], val label: Option[String]) {
  def this(label: Option[String]) = this(items = Nil, label = label)
}

class Parent(val items: List[String], val label: Option[String])

class WithApply(val value: Int)

object WithApply {
  def apply(count: Int, label: Option[String]): WithApply = new WithApply(count)
}

class Overloaded(val value: Int)

object Overloaded {
  def apply(count: Int, label: Option[String]): Overloaded = new Overloaded(count)
  def apply(name: String, other: Option[String]): Overloaded = new Overloaded(0)
}

class Box[A](val items: List[A], val label: Option[A], val size: Int = 0)

class Service {
  def find(items: List[Int], label: Option[Int]): Int = 0
}

object Syntax {
  implicit class Tagged(val value: String) {
    def tagged(items: List[Int], label: Option[Int]): String = value
  }
}

// `send(1, None)` does not fit the member, so it goes through the view.
// Naming `timeout` would make the member fit, and Scala prefers members.
class Client {
  def send(id: Int, retries: Int = 3, timeout: Option[Int] = None): String = "member"
}

object ClientSyntax {
  implicit class ClientOps(private val client: Client) {
    def send(id: Int, timeout: Option[Int]): String = "view"
  }
}

// Its companion inherits an `apply` with the same arity as the call below,
// where the synthetic one needs a default. Naming by the synthetic one would
// move the call onto it and swap the values.
trait FromPair[A] {
  def apply(primary: Option[String], fallback: Option[String]): A = build(fallback, primary)
  protected def build(fallback: Option[String], primary: Option[String]): A
}

final case class Setting(fallback: Option[String], primary: Option[String], scope: Option[String] = None)

object Setting extends FromPair[Setting] {
  protected def build(fallback: Option[String], primary: Option[String]): Setting =
    new Setting(fallback, primary)
}

object Calls {
  type Label = Option[Int]

  def seqItems(first: Int, items: Seq[Int]): Int = first
  def anyValue(first: Int, value: Any): Int = first
  def aliased(first: Int, label: Label): Int = first
  def keyword(first: Int, `type`: Option[Int]): Int = first
  def twoLists(first: Int)(items: List[Int], label: Option[Int]): Int = first
  def withDefault(first: Int, label: Option[Int], last: Int = 0): Int = first
  def overloaded(first: Int, label: Option[Int]): Int = first
  def overloaded(first: String, other: Option[Int]): Int = 0
  def varargs(labels: Option[Int]*): Int = labels.size
  def mixed(first: Int, rest: Option[Int]*): Int = first
  def typed[A](first: A, label: Option[A]): A = first
  def implicitArgs(first: Int)(implicit items: List[Int], label: Option[Int]): Int = first
  val function: (Int, Option[Int]) => Int = (first, _) => first
}
