package fix

import scala.meta._
import scala.util.Try

import scalafix.v1._

import metaconfig.Configured

/** Names every positional `Nil` or `None` argument after the parameter it is
  * passed to: `f(a, None)` becomes `f(a, b = None)`.
  *
  * A bare `Nil` or `None` says nothing about which parameter it fills, so
  * the rule rewrites it to a named argument. A wrong name can compile and
  * silently move the value to another parameter, so it only names an
  * argument when it is sure, and leaves alone:
  *
  *   - an argument list with a single argument, such as `Future.successful(None)`
  *   - a repeated (varargs) parameter list, such as `List(None, x)`
  *   - a Java-defined method, whose parameter names SemanticDB does not keep
  *   - a `using` or implicit argument list, or any call whose method has one
  *     at or before the list being named, since the call may not write it
  *   - an extension method called on its receiver, such as `1.ext(Nil, None)`
  *   - a call whose written argument lists do not fit the method's parameter
  *     lists one for one
  *   - a parameter whose declared type cannot hold the argument
  *   - a call it cannot resolve, such as one through a function value, into
  *     a library compiled by Scala 3, or one whose overloads disagree on the
  *     parameter's name
  */
final class NamedNilNoneArgumentsChecker(index: SemanticdbIndex)
    extends SemanticRule("NamedNilNoneArgumentsChecker") {

  def this() = this(SemanticdbIndex.empty)

  override def description: String =
    "Rewrites positional Nil and None arguments to named arguments."

  override def isRewrite: Boolean = true

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(new NamedNilNoneArgumentsChecker(SemanticdbIndex.fromConfiguration(config)))

  override def fix(implicit doc: SemanticDocument): Patch = {
    val resolver = new CalleeResolver(index)
    doc.tree.collect {
      case apply: Term.Apply =>
        val (fun, earlierArgumentCounts) = CalleeResolver.unwind(apply)
        fixArguments(apply.argClause, resolver.resolveFunction(fun).map(Callee(_, earlierArgumentCounts)))
      case init: Init =>
        init.argClauses.zipWithIndex.map { case (argClause, parameterListIndex) =>
          val earlierArgumentCounts = init.argClauses.take(parameterListIndex).map(_.values.size).toList
          fixArguments(argClause, resolver.resolveInit(init).map(Callee(_, earlierArgumentCounts)))
        }.asPatch
    }.asPatch
  }

  private def fixArguments(argClause: Term.ArgClause, target: => Option[Callee])(implicit
      doc: SemanticDocument
  ): Patch = {
    val args = argClause.values
    val emptyValues = args.zipWithIndex.flatMap { case (arg, argumentIndex) =>
      CalleeResolver.emptyValue(arg).map(value => (arg, argumentIndex, value))
    }
    if (args.size < 2 || emptyValues.isEmpty || argClause.mod.nonEmpty) Patch.empty
    else
      // A symbol the symbol table cannot load is a call the rule cannot
      // resolve, not a reason to stop the run.
      Try(target).toOption.flatten.fold(Patch.empty) { callee =>
        emptyValues.map { case (arg, argumentIndex, value) =>
          callee.parameterName(argumentIndex, args.size, value).fold(Patch.empty) { name =>
            Patch.addLeft(arg, s"$name = ")
          }
        }.asPatch
      }
  }
}

/** `Nil` or `None`, as an argument, and the declared parameter types it fits. */
sealed abstract class EmptyValue(val acceptedTypes: Set[String])

object EmptyValue {
  private val commonSupertypes = Set(
    "scala/AnyRef#",
    "java/lang/Object#",
    "scala/Product#",
    "scala/Equals#",
    "scala/Serializable#",
    "java/io/Serializable#",
    "scala/collection/IterableOnce#"
  )

  case object NoneValue extends EmptyValue(commonSupertypes + "scala/Option#")

  case object NilValue
      extends EmptyValue(
        commonSupertypes ++ Set(
          "scala/package.List#",
          "scala/collection/immutable/List#",
          "scala/package.Seq#",
          "scala/collection/Seq#",
          "scala/collection/immutable/Seq#",
          "scala/package.Iterable#",
          "scala/collection/Iterable#",
          "scala/collection/immutable/Iterable#",
          "scala/package.LinearSeq#",
          "scala/collection/LinearSeq#",
          "scala/collection/immutable/LinearSeq#",
          "scala/package.Traversable#",
          "scala/collection/Traversable#",
          "scala/package.TraversableOnce#",
          "scala/collection/TraversableOnce#"
        )
      )
}

/** The overloads a call may resolve to, as their parameter lists.
  *
  * `requiresExactFit` is set when the call is `Foo(...)` on an object with a
  * parent the rule cannot see into, which might declare an `apply` the call
  * binds to instead. Scala prefers an overload that needs no default
  * arguments, and among equally good ones the one the object declares, so
  * the call can only bind to such a hidden `apply` if every declared one
  * either needs a default or does not accept the arguments. An exact fit
  * rules out the first. In the second, naming an argument at its own
  * position changes nothing for the declared ones, so the call either keeps
  * binding as before or stops compiling.
  */
final case class Candidates(overloads: List[List[List[Parameter]]], requiresExactFit: Boolean)

/** The candidates for a call, and how many arguments each written list
  * before the one being named holds. The list being named fills parameter
  * list `earlierArgumentCounts.size`.
  */
final case class Callee(candidates: Candidates, earlierArgumentCounts: List[Int]) {
  private val parameterListIndex = earlierArgumentCounts.size

  /** The name to give the argument at `argumentIndex`, when every overload
    * the call could reach agrees on it.
    */
  def parameterName(argumentIndex: Int, argumentCount: Int, value: EmptyValue): Option[String] = {
    val reachable = candidates.overloads.filter(isReachable(_, argumentCount))
    val fits = !candidates.requiresExactFit || reachable.forall(fitsExactly(_, argumentCount))
    val names = if (fits) reachable.map(nameIn(_, argumentIndex, value)) else Nil
    names.distinct match {
      case List(Some(name)) => Some(Term.Name(name).syntax)
      case _ => None
    }
  }

  private def writtenCounts(argumentCount: Int): List[(Int, Int)] =
    (earlierArgumentCounts :+ argumentCount).zipWithIndex

  /** Whether the call could be to this overload: no written list has more
    * arguments than the parameter list at its index takes.
    */
  private def isReachable(parameterLists: List[List[Parameter]], argumentCount: Int): Boolean =
    writtenCounts(argumentCount).forall { case (count, index) =>
      parameterLists.lift(index).forall(parameters => parameters.size >= count || parameters.exists(_.isRepeated))
    }

  /** Whether every written list fills its parameter list with no defaults. */
  private def fitsExactly(parameterLists: List[List[Parameter]], argumentCount: Int): Boolean =
    writtenCounts(argumentCount).forall { case (count, index) =>
      parameterLists.lift(index).exists(parameters => parameters.size == count && !parameters.exists(_.isRepeated))
    }

  private def nameIn(parameterLists: List[List[Parameter]], argumentIndex: Int, value: EmptyValue): Option[String] = {
    // Scala 3 lets a call leave out a `using` list at any position, which
    // shifts every later list, so one at or before the target makes the
    // written lists impossible to line up.
    val omittable = parameterLists.take(parameterListIndex + 1).exists(_.exists(_.isImplicit))
    if (omittable) None
    else
      parameterLists.lift(parameterListIndex).flatMap { parameters =>
        parameters.lift(argumentIndex).filter { parameter =>
          !parameters.exists(_.isRepeated) &&
          CalleeResolver.isPlainName(parameter.name) &&
          CalleeResolver.canHold(parameter, value)
        }.map(_.name)
      }
  }
}

object CalleeResolver {
  private val nilOrNone = Set(
    "scala/package.Nil.",
    "scala/collection/immutable/Nil.",
    "scala/None."
  )

  /** Parents that declare no `apply` a companion could inherit, or whose
    * `apply` the companion must itself implement: `FunctionN`'s is abstract.
    */
  private val parentsWithoutApply = Set(
    "scala/Any#",
    "scala/AnyRef#",
    "java/lang/Object#",
    "scala/Product#",
    "scala/Equals#",
    "scala/Serializable#",
    "java/io/Serializable#",
    "scala/deriving/Mirror#",
    "scala/deriving/Mirror.Product#",
    "scala/deriving/Mirror.Singleton#",
    "scala/deriving/Mirror.Sum#"
  )

  private val function = "scala/(runtime/Abstract)?Function[0-9]+#".r

  def isWithoutApply(parent: Symbol): Boolean =
    parentsWithoutApply.contains(parent.value) || function.pattern.matcher(parent.value).matches

  def emptyValue(arg: Term)(implicit doc: SemanticDocument): Option[EmptyValue] = {
    val symbol = arg match {
      case name @ Term.Name("Nil" | "None") => Some(name.symbol.value).filter(nilOrNone.contains)
      case Term.Select(qualifier, name @ Term.Name("Nil" | "None")) =>
        // Scala 2's SemanticDB records some qualified names on the qualifier:
        // 2.12 does for `scala.Nil`, 2.13 for `immutable.Nil`. Neither `Nil`
        // nor `None` has a member of either name, so a qualifier that resolves
        // to one of them can only be such a misplaced name.
        List(name.symbol.value, qualifier.symbol.value).find(nilOrNone.contains)
      case _ => None
    }
    symbol.map(value => if (value == "scala/None.") EmptyValue.NoneValue else EmptyValue.NilValue)
  }

  def isPlainName(name: String): Boolean =
    name.nonEmpty && !name.contains("$") && name != "<init>"

  /** Whether the parameter's declared type can hold the value. A type
    * parameter can. Anything the rule cannot read, a type alias included,
    * counts as no.
    */
  def canHold(parameter: Parameter, value: EmptyValue): Boolean =
    parameter.typeSymbol.exists(symbol => symbol.endsWith("]") || value.acceptedTypes.contains(symbol))

  /** The function a call applies, and how many arguments each argument list
    * written before the outermost one holds: `f(a)(b, None)` is `f` with one
    * earlier list of one argument.
    */
  def unwind(apply: Term.Apply): (Term, List[Int]) = {
    @annotation.tailrec
    def loop(fun: Term, earlier: List[Int]): (Term, List[Int]) =
      fun match {
        case inner: Term.Apply => loop(inner.fun, inner.argClause.values.size :: earlier)
        case Term.ApplyType(inner, _) => loop(inner, earlier)
        case other => (other, earlier)
      }
    loop(apply.fun, Nil)
  }
}

/** Finds the overloads a call may go to: first through Scalafix's own symbol
  * table, then through the project's SemanticDB index.
  */
final class CalleeResolver(index: SemanticdbIndex)(implicit doc: SemanticDocument) {

  def resolveFunction(fun: Term): Option[Candidates] =
    fun match {
      case name: Term.Name => resolveName(name.symbol)
      case Term.Select(qualifier, name) if !isExtensionCall(qualifier, name.symbol) => resolveName(name.symbol)
      case _ => None
    }

  /** A method of an object or package called on some other value is an
    * extension method: `1.described(Nil, None)`. Its first parameter list
    * takes the receiver, which the call does not write, so the argument
    * lists no longer line up with the parameter lists. SemanticDB does not
    * mark extension methods, so this is the only direct sign of one.
    *
    * An extension method defined in a class or trait, or called from a
    * sibling inside its `extension` block, is not caught here. Its shifted
    * lists fail the arity and type checks in `Callee` instead.
    */
  private def isExtensionCall(qualifier: Term, method: Symbol): Boolean = {
    val owner = method.owner.value
    val receiver = qualifier.symbol.value
    val isOwnerOrItsPackage =
      qualifier.isInstanceOf[Term.This] || receiver == owner ||
        (receiver.endsWith("/") && owner.startsWith(receiver))
    owner.endsWith(".") && !isOwnerOrItsPackage
  }

  /** `new Foo(a, None)` and `extends Foo(a, None)`. */
  def resolveInit(init: Init): Option[Candidates] = {
    val symbol = init.tpe match {
      case Type.Apply(inner, _) => inner.symbol
      case other => other.symbol
    }
    if (symbol.isNone) None
    else if (isClass(symbol)) exact(constructors(symbol))
    else method(symbol).flatMap(lists => exact(List(lists)))
  }

  private def resolveName(symbol: Symbol): Option[Candidates] =
    if (symbol.isNone) None
    else
      method(symbol) match {
        case Some(lists) => exact(List(lists))
        case None if isObject(symbol) => reachableApplies(symbol)
        // Scala 3 lets `Foo(a, None)` call a constructor without `new`.
        case None if isClass(symbol) => exact(constructors(symbol))
        case None => None
      }

  /** Candidates that are every overload the call could reach. */
  private def exact(overloads: List[List[List[Parameter]]]): Option[Candidates] =
    if (overloads.isEmpty) None else Some(Candidates(overloads, requiresExactFit = false))

  /** Every `apply` that `Foo(...)` could reach on the object `Foo`: the ones it
    * declares, the case class's synthetic one included, and the ones it
    * inherits from parents the rule can see into. A parent it cannot see into
    * may hide one more, which `Candidates.requiresExactFit` guards against.
    */
  private def reachableApplies(obj: Symbol): Option[Candidates] = {
    @annotation.tailrec
    def loop(
        pending: List[Symbol],
        seen: Set[Symbol],
        found: List[List[List[Parameter]]],
        isComplete: Boolean
    ): Option[Candidates] =
      pending match {
        case Nil =>
          if (found.isEmpty) None else Some(Candidates(found, requiresExactFit = !isComplete))
        case owner :: rest if seen.contains(owner) || CalleeResolver.isWithoutApply(owner) =>
          loop(rest, seen + owner, found, isComplete)
        case owner :: rest =>
          typeMembers(owner) match {
            case Some((Some(applies), parents)) =>
              loop(rest ++ parents, seen + owner, found ++ applies, isComplete)
            case Some((None, parents)) => loop(rest ++ parents, seen + owner, found, isComplete = false)
            case None => loop(rest, seen + owner, found, isComplete = false)
          }
      }
    loop(List(obj), Set.empty, Nil, isComplete = true)
  }

  /** A type's own `apply` methods (`None` when one of them is not a Scala
    * method the rule can read) and its parents, or `None` when the rule has
    * no information about the type at all.
    */
  private def typeMembers(owner: Symbol): Option[(Option[List[List[List[Parameter]]]], List[Symbol])] =
    owner.info.map(_.signature) match {
      case Some(signature: ClassSignature) =>
        val applies = signature.declarations.filter(info => info.isMethod && info.displayName == "apply")
        val lists = applies.flatMap(info => method(info.symbol))
        val parents = signature.parents.map {
          case TypeRef(_, symbol, _) => symbol
          case _ => Symbol.None
        }
        Some((if (lists.size == applies.size) Some(lists) else None, parents))
      case Some(_) => None
      case None =>
        index.parents(owner.value).map { parents =>
          (Some(index.applies(owner.value)), parents.map(Symbol(_)))
        }
    }

  private def isObject(symbol: Symbol): Boolean =
    symbol.isGlobal && symbol.value.endsWith(".") && !symbol.value.endsWith(").")

  private def isClass(symbol: Symbol): Boolean =
    symbol.isGlobal && symbol.value.endsWith("#")

  /** The parameter lists of a Scala method, or `None` for anything else. */
  private def method(symbol: Symbol): Option[List[List[Parameter]]] =
    symbol.info match {
      case Some(info) if info.isJava => None
      case Some(info) =>
        info.signature match {
          case signature: MethodSignature if info.isMethod || info.isConstructor =>
            Some(signature.parameterLists.map(_.map(parameter)))
          case _ => None
        }
      case None => index.method(symbol.value)
    }

  private def parameter(info: SymbolInformation): Parameter = {
    val tpe = info.signature match {
      case ValueSignature(tpe) => Some(tpe)
      case _ => None
    }
    Parameter(
      name = info.displayName,
      isImplicit = info.isImplicit || info.isGiven,
      isRepeated = tpe.exists(_.isInstanceOf[RepeatedType]),
      typeSymbol = tpe.flatMap(typeSymbol)
    )
  }

  private def typeSymbol(tpe: SemanticType): Option[String] =
    tpe match {
      case TypeRef(_, symbol, _) => Some(symbol.value)
      case ByNameType(inner) => typeSymbol(inner)
      case AnnotatedType(_, inner) => typeSymbol(inner)
      case _ => None
    }

  private def constructors(cls: Symbol): List[List[List[Parameter]]] =
    cls.info.map(_.signature) match {
      case Some(signature: ClassSignature) =>
        signature.declarations.filter(_.isConstructor).flatMap(info => method(info.symbol))
      case _ => index.constructors(cls.value)
    }
}
