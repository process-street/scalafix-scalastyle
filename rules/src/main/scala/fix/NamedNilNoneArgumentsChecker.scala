package fix

import scala.meta._
import scala.util.Try

import scalafix.v1._

import metaconfig.Configured

/** Names every positional `Nil` or `None` argument after the parameter it is
  * passed to: `f(a, None)` becomes `f(a, b = None)`.
  *
  * A bare `Nil` or `None` says nothing about which parameter it fills, so
  * the rule rewrites it to a named argument. It only rewrites a call it can
  * resolve to a Scala method whose parameter names it knows, and leaves alone:
  *
  *   - an argument list with a single argument, such as `Future.successful(None)`
  *   - a repeated (varargs) parameter list, such as `List(None, x)`
  *   - a Java-defined method, whose parameter names SemanticDB does not keep
  *   - a `using` or implicit argument list
  *   - an extension method called on its receiver, such as `1.ext(Nil, None)`
  *   - a call it cannot resolve, such as one through a function value, or one
  *     whose overloads disagree on the parameter's name
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
        fixArguments(apply.argClause, resolver.resolveApply(apply))
      case init: Init =>
        init.argClauses.zipWithIndex.map { case (argClause, parameterListIndex) =>
          fixArguments(argClause, resolver.resolveInit(init).map(_.copy(parameterListIndex = parameterListIndex)))
        }.asPatch
    }.asPatch
  }

  private def fixArguments(argClause: Term.ArgClause, target: => Option[Callee])(implicit
      doc: SemanticDocument
  ): Patch = {
    val args = argClause.values
    val nilOrNone = args.zipWithIndex.filter { case (arg, _) => CalleeResolver.isNilOrNone(arg) }
    if (args.size < 2 || nilOrNone.isEmpty || argClause.mod.nonEmpty) Patch.empty
    else
      // A symbol the symbol table cannot load is a call the rule cannot
      // resolve, not a reason to stop the run.
      Try(target).toOption.flatten.fold(Patch.empty) { callee =>
        nilOrNone.map { case (arg, argumentIndex) =>
          callee.parameterName(argumentIndex, args.size).fold(Patch.empty) { name =>
            Patch.addLeft(arg, s"$name = ")
          }
        }.asPatch
      }
  }
}

/** The methods a call may resolve to, as their parameter lists, and which of
  * those lists the argument list fills.
  */
final case class Callee(overloads: List[List[List[Parameter]]], parameterListIndex: Int) {

  /** The name to give the argument at `argumentIndex`, when every overload
    * the call could reach agrees on it.
    */
  def parameterName(argumentIndex: Int, argumentCount: Int): Option[String] = {
    val reachable = overloads.map(_.lift(parameterListIndex)).filterNot {
      // Too few parameters, and none of them repeated: the call cannot be
      // to this overload.
      case Some(parameters) => parameters.size < argumentCount && !parameters.exists(_.isRepeated)
      case None => false
    }
    val names = reachable.map(_.flatMap { parameters =>
      parameters.lift(argumentIndex).filter { parameter =>
        parameters.forall(p => !p.isImplicit && !p.isRepeated) &&
        CalleeResolver.isPlainName(parameter.name)
      }.map(_.name)
    })
    names.distinct match {
      case List(Some(name)) => Some(Term.Name(name).syntax)
      case _ => None
    }
  }
}

object CalleeResolver {
  private val nilOrNone = Set(
    "scala/package.Nil.",
    "scala/collection/immutable/Nil.",
    "scala/None."
  )

  def isNilOrNone(arg: Term)(implicit doc: SemanticDocument): Boolean =
    arg match {
      case name @ Term.Name("Nil" | "None") => nilOrNone.contains(name.symbol.value)
      case Term.Select(qualifier, name @ Term.Name("Nil" | "None")) =>
        // Scala 2's SemanticDB records some qualified names on the qualifier:
        // 2.12 does for `scala.Nil`, 2.13 for `immutable.Nil`. Neither `Nil`
        // nor `None` has a member of either name, so a qualifier that resolves
        // to one of them can only be such a misplaced name.
        nilOrNone.contains(name.symbol.value) || nilOrNone.contains(qualifier.symbol.value)
      case _ => false
    }

  def isPlainName(name: String): Boolean =
    name.nonEmpty && !name.contains("$") && name != "<init>"
}

/** Finds the parameter lists a call goes to: first through Scalafix's own
  * symbol table, then through the project's SemanticDB index.
  */
final class CalleeResolver(index: SemanticdbIndex)(implicit doc: SemanticDocument) {

  /** `f(a)(b, None)` fills the second parameter list of `f`. */
  def resolveApply(apply: Term.Apply): Option[Callee] = {
    def loop(fun: Term, depth: Int): Option[Callee] =
      fun match {
        case inner: Term.Apply => loop(inner.fun, depth + 1)
        case Term.ApplyType(inner, _) => loop(inner, depth)
        case name: Term.Name => resolveName(name.symbol, depth)
        case Term.Select(qualifier, name) if !isExtensionCall(qualifier, name.symbol) =>
          resolveName(name.symbol, depth)
        case _ => None
      }
    loop(apply.fun, 0)
  }

  /** A method of an object or package called on some other value is an
    * extension method: `1.described(Nil, None)`. Its first parameter list
    * takes the receiver, which the call does not write, so the argument
    * lists no longer line up with the parameter lists. SemanticDB does not
    * mark extension methods, so this is the only sign of one.
    *
    * An extension method defined in a class or trait is not caught, but the
    * argument count check still keeps a single-list one from being renamed.
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
  def resolveInit(init: Init): Option[Callee] = {
    val symbol = init.tpe match {
      case Type.Apply(inner, _) => inner.symbol
      case other => other.symbol
    }
    if (symbol.isNone) None
    else if (isClass(symbol)) nonEmpty(constructors(symbol)).map(Callee(_, 0))
    else method(symbol).map(lists => Callee(List(lists), 0))
  }

  private def resolveName(symbol: Symbol, depth: Int): Option[Callee] =
    if (symbol.isNone) None
    else
      method(symbol) match {
        case Some(lists) => Some(Callee(List(lists), depth))
        case None if isObject(symbol) => resolveCompanionApply(symbol, depth)
        // Scala 3 lets `Foo(a, None)` call a constructor without `new`.
        case None if isClass(symbol) => nonEmpty(constructors(symbol)).map(Callee(_, depth))
        case None => None
      }

  /** `Foo(a, None)` calls an `apply` that `Foo`'s companion declares, the
    * synthetic one of a case class included. An `apply` the companion only
    * inherits is not looked for, so such a call is left alone.
    */
  private def resolveCompanionApply(companion: Symbol, depth: Int): Option[Callee] =
    nonEmpty(applies(companion)).map(Callee(_, depth))

  private def nonEmpty[A](list: List[A]): Option[List[A]] =
    if (list.isEmpty) None else Some(list)

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

  private def parameter(info: SymbolInformation): Parameter =
    Parameter(
      name = info.displayName,
      isImplicit = info.isImplicit || info.isGiven,
      isRepeated = info.signature match {
        case ValueSignature(_: RepeatedType) => true
        case _ => false
      }
    )

  private def declarations(symbol: Symbol): Option[List[SymbolInformation]] =
    symbol.info.map(_.signature).collect { case signature: ClassSignature => signature.declarations }

  private def applies(obj: Symbol): List[List[List[Parameter]]] =
    declarations(obj) match {
      case Some(members) =>
        members.filter(info => info.isMethod && info.displayName == "apply").flatMap(info => method(info.symbol))
      case None => index.applies(obj.value)
    }

  private def constructors(cls: Symbol): List[List[List[Parameter]]] =
    declarations(cls) match {
      case Some(members) => members.filter(_.isConstructor).flatMap(info => method(info.symbol))
      case None => index.constructors(cls.value)
    }
}
