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
  *   - an extension method, in any form of call, or a method the index
  *     cannot place, such as an `export` forwarder
  *   - a method reached through an implicit class or conversion on the
  *     receiver
  *   - a call whose written argument lists do not fit the method's parameter
  *     lists one for one
  *   - a parameter whose declared type cannot hold the argument
  *   - a call it cannot resolve, such as one through a function value, into
  *     a library compiled by Scala 3, or one whose overloads disagree on the
  *     parameter's name
  */
final class NamedNilNoneArgumentsChecker(index: SemanticdbIndex, isScala3: Boolean)
    extends SemanticRule("NamedNilNoneArgumentsChecker") {

  def this() = this(SemanticdbIndex.empty, isScala3 = false)

  override def description: String =
    "Rewrites positional Nil and None arguments to named arguments."

  override def isRewrite: Boolean = true

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(
      new NamedNilNoneArgumentsChecker(
        SemanticdbIndex.fromConfiguration(config),
        isScala3 = config.scalaVersion.startsWith("3")
      )
    )

  override def fix(implicit doc: SemanticDocument): Patch = {
    val resolver = new CalleeResolver(index, isScala3)
    doc.tree.collect {
      case apply: Term.Apply =>
        val (call, fun, earlierArgumentCounts) = CalleeResolver.unwind(apply)
        fixArguments(apply.argClause, resolver.resolveCall(call, fun).map(Callee(_, earlierArgumentCounts)))
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

/** The overloads a call may resolve to, as their parameter lists, and how many
  * arguments each written list before the one being named holds. The list
  * being named fills parameter list `earlierArgumentCounts.size`.
  */
final case class Callee(overloads: List[List[List[Parameter]]], earlierArgumentCounts: List[Int]) {
  private val parameterListIndex = earlierArgumentCounts.size

  /** The name to give the argument at `argumentIndex`, when every overload
    * the call could reach agrees on it.
    */
  def parameterName(argumentIndex: Int, argumentCount: Int, value: EmptyValue): Option[String] = {
    val names = overloads.filter(isReachable(_, argumentCount)).map(nameIn(_, argumentIndex, value))
    names.distinct match {
      case List(Some(name)) => Some(Term.Name(name).syntax)
      case _ => None
    }
  }

  /** Whether the call could be to this overload: no written list has more
    * arguments than the parameter list at its index takes.
    */
  private def isReachable(parameterLists: List[List[Parameter]], argumentCount: Int): Boolean =
    (earlierArgumentCounts :+ argumentCount).zipWithIndex.forall { case (count, index) =>
      parameterLists.lift(index).forall(parameters => parameters.size >= count || parameters.exists(_.isRepeated))
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
    "java/io/Serializable#"
  )

  private val function = "scala/(runtime/Abstract)?Function[0-9]+#".r

  private val functionApply = "scala/Function[0-9]+#apply\\(\\)\\.".r

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

  /** The innermost application of a call, the function it applies, and how
    * many arguments each argument list written before the outermost one
    * holds: `f(a)(b, None)` is `f(a)`, `f`, and one earlier list of one
    * argument.
    */
  def unwind(apply: Term.Apply): (Term.Apply, Term, List[Int]) = {
    @annotation.tailrec
    def loop(call: Term.Apply, fun: Term, earlier: List[Int]): (Term.Apply, Term, List[Int]) =
      fun match {
        case inner: Term.Apply => loop(inner, inner.fun, inner.argClause.values.size :: earlier)
        case Term.ApplyType(inner, _) => loop(call, inner, earlier)
        case other => (call, other, earlier)
      }
    loop(apply, apply.fun, Nil)
  }

  /** The method a Scala 3 synthetic says the call applies, when it is a plain
    * reference to one. A synthetic that goes through anything else, such as
    * an implicit conversion, gives none, and so does a function value's
    * `apply`, whose `v1`, `v2` names say nothing.
    */
  def chosenMethod(synthetic: SemanticTree): Option[Symbol] = {
    def isPlain(qualifier: SemanticTree): Boolean =
      qualifier match {
        case _: OriginalTree | _: IdTree => true
        case SelectTree(inner, _) => isPlain(inner)
        case TypeApplyTree(inner, _) => isPlain(inner)
        case _ => false
      }
    def method(function: SemanticTree): Option[Symbol] =
      function match {
        case id: IdTree => Some(id.symbol)
        case TypeApplyTree(inner, _) => method(inner)
        case SelectTree(qualifier, id) if isPlain(qualifier) => Some(id.symbol)
        case _ => None
      }
    synthetic match {
      // An implicit argument list applied after the call wraps it.
      case ApplyTree(call: ApplyTree, _) => chosenMethod(call)
      case ApplyTree(function, _) =>
        method(function).filterNot(symbol => functionApply.pattern.matcher(symbol.value).matches)
      case _ => None
    }
  }
}

/** Finds the overloads a call may go to: first through Scalafix's own symbol
  * table, then through the project's SemanticDB index.
  */
final class CalleeResolver(index: SemanticdbIndex, isScala3: Boolean)(implicit doc: SemanticDocument) {

  /** `call` is the innermost application, `fun` the function it applies. */
  def resolveCall(call: Term.Apply, fun: Term): Option[List[List[List[Parameter]]]] = {
    val (receiver, name) = fun match {
      case name: Term.Name => (None, Some(name))
      case Term.Select(qualifier, name) => (Some(qualifier), Some(name))
      case _ => (None, None)
    }
    name.flatMap { name =>
      val symbol = name.symbol
      if (symbol.isNone) None
      else
        method(symbol) match {
          case Some(lists) if receiver.forall(isOwnMember(_, symbol)) => Some(List(lists))
          case Some(_) => None
          // A view on the object itself, which supplies the `apply`.
          case None if (isObject(symbol) || isClass(symbol)) && !isConverted(name) =>
            resolveImplicitApply(call, symbol)
          case None => None
        }
    }
  }

  /** Whether the method belongs to the receiver itself, rather than coming
    * from an implicit class or conversion. A call reaches such a method only
    * when the receiver's own member does not take the arguments, and naming
    * one can make that member take them, which Scala then prefers.
    *
    * Scala 3 records every conversion as a synthetic on the receiver. Scala 2
    * records none, so there the method must belong to the receiver's static
    * type or one of its parents, read from the receiver's symbol.
    */
  private def isOwnMember(receiver: Term, method: Symbol): Boolean =
    receiver match {
      case _: Term.This | _: Term.Super => true
      case _ if isScala3 => !isConverted(receiver)
      case _ =>
        receiverType(receiver).exists { tpe =>
          if (tpe.value.endsWith("/")) method.owner.value.startsWith(tpe.value)
          else ancestors(tpe).contains(method.owner)
        }
    }

  private def isConverted(term: Term): Boolean =
    term.synthetics.exists(_.isInstanceOf[ApplyTree])

  /** The static type of a receiver the rule can read without a type checker:
    * a name, or a `new`. A package stands for itself.
    */
  private def receiverType(receiver: Term): Option[Symbol] = {
    def typeOf(symbol: Symbol): Option[Symbol] =
      if (symbol.isNone) None
      else if (symbol.value.endsWith("/")) Some(symbol)
      else
        symbol.info.flatMap { info =>
          if (info.isObject) Some(symbol)
          else
            info.signature match {
              case ValueSignature(TypeRef(_, tpe, _)) => Some(tpe)
              case MethodSignature(_, Nil, TypeRef(_, tpe, _)) => Some(tpe)
              case _ => None
            }
        }
    receiver match {
      case name: Term.Name => typeOf(name.symbol)
      case Term.Select(_, name) => typeOf(name.symbol)
      case Term.New(init) =>
        Some(init.tpe match {
          case Type.Apply(inner, _) => inner.symbol
          case other => other.symbol
        }).filterNot(_.isNone)
      case _ => None
    }
  }

  /** A type and every type it extends, as far as the rule can read them. */
  private def ancestors(tpe: Symbol): Set[Symbol] = {
    @annotation.tailrec
    def loop(pending: List[Symbol], seen: Set[Symbol]): Set[Symbol] =
      pending match {
        case Nil => seen
        case symbol :: rest if seen.contains(symbol) => loop(rest, seen)
        case symbol :: rest =>
          val parents = symbol.info.map(_.signature) match {
            case Some(signature: ClassSignature) =>
              signature.parents.collect { case TypeRef(_, parent, _) => parent }
            case _ => Nil
          }
          loop(rest ++ parents, seen + symbol)
      }
    loop(List(tpe), Set.empty)
  }

  /** `Foo(a, None)`, which calls `Foo.apply` or, in Scala 3, a constructor.
    *
    * Scala 3 records the method it chose as a synthetic over the call, so the
    * rule names from that one alone. An `apply` can come from places the rule
    * cannot see, such as a parent in a library or an implicit conversion, and
    * naming an argument after any other candidate could move the call onto
    * it. Scala 2 records no synthetics unless asked to, but every parent it
    * compiles against is readable, so there the rule collects every `apply`
    * the object declares or inherits.
    */
  private def resolveImplicitApply(call: Term.Apply, symbol: Symbol): Option[List[List[List[Parameter]]]] =
    if (isScala3) {
      // A conversion applied to the call's result shares its range, such as
      // a test matcher's, but takes a single argument, so only the method
      // that can take the call's own arguments is the one it applies.
      val argumentCount = call.argClause.values.size
      val chosen = call.synthetics.flatMap(CalleeResolver.chosenMethod).distinct.flatMap(method).filter {
        _.headOption.exists(parameters => parameters.size >= argumentCount || parameters.exists(_.isRepeated))
      }
      chosen match {
        case List(lists) => Some(List(lists))
        // A constructor proxy records no synthetic. The constructors a class
        // declares are all of them.
        case Nil if isClass(symbol) => Some(constructors(symbol)).filter(_.nonEmpty)
        // No record, as for `Foo[A](...)` with explicit type arguments, or
        // more than one: the rule cannot tell which `apply` the call uses.
        case _ => None
      }
    } else if (isObject(symbol)) reachableApplies(symbol).filter(_.nonEmpty)
    else None

  /** `new Foo(a, None)` and `extends Foo(a, None)`: the constructors a class
    * declares are all of them, so the overloads are known.
    */
  def resolveInit(init: Init): Option[List[List[List[Parameter]]]] = {
    val symbol = init.tpe match {
      case Type.Apply(inner, _) => inner.symbol
      case other => other.symbol
    }
    if (symbol.isNone) None
    else if (isClass(symbol)) Some(constructors(symbol)).filter(_.nonEmpty)
    else method(symbol).map(List(_))
  }

  /** Every `apply` that `Foo(...)` could reach on the object `Foo` in Scala 2:
    * the ones it declares, the case class's synthetic one included, and the
    * ones it inherits. `None` when a parent is one the rule cannot read.
    */
  private def reachableApplies(obj: Symbol): Option[List[List[List[Parameter]]]] = {
    @annotation.tailrec
    def loop(
        pending: List[Symbol],
        seen: Set[Symbol],
        found: List[List[List[Parameter]]]
    ): Option[List[List[List[Parameter]]]] =
      pending match {
        case Nil => Some(found)
        case owner :: rest if seen.contains(owner) || CalleeResolver.isWithoutApply(owner) =>
          loop(rest, seen + owner, found)
        case owner :: rest =>
          owner.info.map(_.signature) match {
            case Some(signature: ClassSignature) =>
              val applies = signature.declarations.filter(info => info.isMethod && info.displayName == "apply")
              val lists = applies.flatMap(info => method(info.symbol))
              val parents = signature.parents.map {
                case TypeRef(_, symbol, _) => symbol
                case _ => Symbol.None
              }
              if (lists.size == applies.size) loop(rest ++ parents, seen + owner, found ++ lists)
              else None
            case _ => None
          }
      }
    loop(List(obj), Set.empty, Nil)
  }

  private def isObject(symbol: Symbol): Boolean =
    symbol.isGlobal && symbol.value.endsWith(".") && !symbol.value.endsWith(").")

  private def isClass(symbol: Symbol): Boolean =
    symbol.isGlobal && symbol.value.endsWith("#")

  /** The parameter lists of a Scala method, or `None` for anything else.
    * That includes extension methods, since a call to one may or may not
    * write the receiver, and methods the index cannot place, such as `export`
    * forwarders, which may be extension methods too.
    */
  private def method(symbol: Symbol): Option[List[List[Parameter]]] =
    if (index.isUnplaced(symbol.value)) None
    else
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
