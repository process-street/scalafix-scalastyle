/*
rule = NamedNilNoneArgumentsChecker
*/
package fix

import scala.collection.immutable

import fix.Syntax._

object NamedNilNoneArguments {
  final case class Local(items: List[String], label: Option[String])

  def local(first: Int, label: Option[Int], items: List[Int]): Int = first

  // Rewritten: a positional Nil or None in a list of two or more arguments.
  val caseClass = Config(Nil, None)
  val localCaseClass = Local(Nil, None)
  val localMethod = local(1, None, Nil)
  val constructor = new Plain(Nil, None)
  val curried = Calls.twoLists(1)(Nil, None)
  val withDefault = Calls.withDefault(1, None)
  val overloadedMethod = Calls.overloaded(1, None)
  val explicitApply = WithApply(1, None)
  val typed = Calls.typed[String]("a", None)
  val qualified = Config(scala.Nil, scala.None)
  val immutableNil = Config(immutable.Nil, None)
  val partlyNamed = Config(Nil, defaultValue = None)
  val nested = Config(Nil, Some(Local(Nil, None).toString))
  object Child extends Parent(Nil, None)
  val keyword = Calls.keyword(1, None)
  val generic = new Box[Int](Nil, None)
  val instanceMethod = new Service().find(Nil, None)
  val implicitClassMethod = "a".tagged(Nil, None)

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
