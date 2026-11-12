package fix

import scala.collection.immutable

import fix.Syntax._

object NamedNilNoneArguments {
  final case class Local(items: List[String], label: Option[String])

  def local(first: Int, label: Option[Int], items: List[Int]): Int = first

  // Rewritten: a positional Nil or None in a list of two or more arguments.
  val caseClass = Config(items = Nil, defaultValue = None)
  val localCaseClass = Local(items = Nil, label = None)
  val localMethod = local(1, label = None, items = Nil)
  val constructor = new Plain(items = Nil, label = None)
  val curried = Calls.twoLists(1)(items = Nil, label = None)
  val withDefault = Calls.withDefault(1, label = None)
  val overloadedMethod = Calls.overloaded(1, label = None)
  val explicitApply = WithApply(1, label = None)
  val typed = Calls.typed[String]("a", label = None)
  val qualified = Config(items = scala.Nil, defaultValue = scala.None)
  val immutableNil = Config(items = immutable.Nil, defaultValue = None)
  val partlyNamed = Config(items = Nil, defaultValue = None)
  val nested = Config(items = Nil, Some(Local(items = Nil, label = None).toString))
  object Child extends Parent(items = Nil, label = None)
  val keyword = Calls.keyword(1, `type` = None)
  val generic = new Box[Int](items = Nil, label = None)
  val instanceMethod = new Service().find(items = Nil, label = None)
  val implicitClassMethod = "a".tagged(items = Nil, label = None)

  // Left alone.
  val alreadyNamed = Config(items = Nil, defaultValue = None)
  val single = Some(None)
  val secondaryConstructor = new Plain(None)
  val varargs = Calls.varargs(None, None)
  val varargsAfterFirst = Calls.mixed(1, None)
  val list = List(None, Some(1))
  val throughFunction = Calls.function(1, None)
  val ambiguousApply = Overloaded(1, None)
  val explicitImplicits = Calls.implicitArgs(1)(Nil, None)
  val notNilOrNone = Config(List("a"), Some("b"))
  val javaMethod = java.util.Objects.equals(None, None)
  val tuple = (Nil, None)
  val pattern = caseClass match {
    case Config(Nil, None) => 1
    case _ => 0
  }
}
