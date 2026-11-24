package fix

import scala.meta._
import scala.util.Try

import scalafix.v1._

import metaconfig.ConfDecoder
import metaconfig.Configured

final case class DiscardedValueCheckerConfig(
    types: List[String] = Nil,
    hint: String = ""
)

object DiscardedValueCheckerConfig {
  val default: DiscardedValueCheckerConfig = DiscardedValueCheckerConfig()

  implicit val surface: metaconfig.generic.Surface[DiscardedValueCheckerConfig] =
    metaconfig.generic.deriveSurface[DiscardedValueCheckerConfig]

  implicit val decoder: ConfDecoder[DiscardedValueCheckerConfig] =
    metaconfig.generic.deriveDecoder(default)
}

/** Flags a value of one of the configured types, or a `Future` of one, that
  * the code throws away. Some results have to be looked at: a permission
  * check that answers `Unauthorized` instead of failing lets every caller
  * through when its answer is dropped.
  *
  * A value is thrown away when it is
  *
  *   - bound to `_` in a `for`, by `_ <- check` or `_ = check`
  *   - bound to a name in a `for`, or to a local `val`, that nothing reads
  *   - the right-hand side of `val _ = check`
  *   - a statement in a block or template, other than a block's last
  *   - the receiver of `map`, `flatMap` or `foreach` given a function that
  *     ignores its parameter, such as `check.flatMap(_ => next)`
  *
  * The rule reads the type a method or value is declared to return, inferred
  * or written. It flags the listed type itself, not its subtypes: a subtype
  * is usually the success the check already proved, as `Authorized` is. An
  * expression it cannot type, such as an `if` or a call through a function
  * value, is left alone, and so is a generic method whose type argument
  * happens to be a listed type.
  */
final class DiscardedValueChecker(config: DiscardedValueCheckerConfig, index: SemanticdbIndex)
    extends SemanticRule("DiscardedValueChecker") {

  def this() = this(DiscardedValueCheckerConfig.default, SemanticdbIndex.empty)

  override def description: String =
    "Flags a discarded value of a type whose values must be used."

  override def withConfiguration(configuration: Configuration): Configured[Rule] =
    configuration.conf
      .getOrElse("DiscardedValueChecker")(config)
      .map(new DiscardedValueChecker(_, SemanticdbIndex.fromConfiguration(configuration)))

  override def fix(implicit doc: SemanticDocument): Patch =
    if (config.types.isEmpty) Patch.empty
    else new DiscardedValueFinder(config, index).patch
}

/** One file's discarded values. */
final class DiscardedValueFinder(config: DiscardedValueCheckerConfig, index: SemanticdbIndex)(implicit
    doc: SemanticDocument
) {
  private val future = "scala/concurrent/Future#"

  private val callbacks = Set("map", "flatMap", "foreach")

  private val listed = SymbolMatcher.normalized(config.types: _*)

  /** Every symbol the file reads, as opposed to defines. */
  private lazy val read: Set[Symbol] =
    doc.tree.collect {
      case name: Term.Name if !name.parent.exists(isDefinition) => name.symbol
    }.toSet

  def patch: Patch =
    doc.tree.collect {
      case Enumerator.Generator(pat, rhs) => checkBinding(pat, rhs)
      case Enumerator.Val(pat, rhs) => checkBinding(pat, rhs)
      case value @ Defn.Val(_, List(pat), _, rhs) =>
        pat match {
          case _: Pat.Wildcard => check(rhs)
          case _ if value.parent.exists(_.isInstanceOf[Term.Block]) => checkBinding(pat, rhs)
          case _ => Patch.empty
        }
      case block: Term.Block =>
        block.stats.dropRight(1).collect { case term: Term => check(term) }.asPatch
      case template: Template =>
        template.stats.collect { case term: Term => check(term) }.asPatch
      case apply @ Term.Apply(Term.Select(receiver, Term.Name(callback)), List(function))
          if apply.argClause.mod.isEmpty && callbacks.contains(callback) && ignoresParameter(function) =>
        check(receiver)
    }.asPatch

  private def isDefinition(parent: Tree): Boolean =
    parent.isInstanceOf[Pat.Var] || parent.isInstanceOf[Term.Param]

  private def checkBinding(pat: Pat, rhs: Term): Patch =
    pat match {
      case _: Pat.Wildcard => check(rhs)
      case Pat.Var(name) => discarded(rhs).filter(_ => isUnread(name)).fold(Patch.empty)(lint(rhs, _))
      case _ => Patch.empty
    }

  /** Whether nothing in the file reads the name. A name the rule cannot
    * resolve counts as read.
    */
  private def isUnread(name: Term.Name): Boolean = {
    val symbol = name.symbol
    !symbol.isNone && !read.contains(symbol)
  }

  private def ignoresParameter(function: Term): Boolean =
    function match {
      case Term.Block(List(inner: Term)) => ignoresParameter(inner)
      case function: Term.Function =>
        function.params match {
          case List(param) =>
            param.name match {
              case name: Term.Name => isUnread(name)
              case _ => true
            }
          case _ => false
        }
      case _ => false
    }

  private def check(term: Term): Patch =
    discarded(term).fold(Patch.empty)(lint(term, _))

  private def lint(term: Term, found: Found): Patch =
    Patch.lint(DiscardedValueDiagnostic(term.pos, found, config.hint))

  /** The listed value the term evaluates to, when the rule can tell. */
  private def discarded(term: Term): Option[Found] =
    term match {
      case apply: Term.Apply =>
        val (_, fun, _) = CalleeResolver.unwind(apply)
        reference(fun).flatMap(found(_, isApplied = true))
      case Term.ApplyType(fun, _) => reference(fun).flatMap(found(_, isApplied = false))
      case infix: Term.ApplyInfix => found(infix.op, isApplied = true)
      case name: Term.Name => found(name, isApplied = false)
      case Term.Select(_, name) => found(name, isApplied = false)
      case Term.Annotate(inner, _) => discarded(inner)
      case _ => None
    }

  private def reference(fun: Term): Option[Term.Name] =
    fun match {
      case name: Term.Name => Some(name)
      case Term.Select(_, name) => Some(name)
      case _ => None
    }

  /** A reference that is not applied gives its result only when it takes no
    * explicit parameters: otherwise it is a function value.
    */
  private def found(name: Term.Name, isApplied: Boolean): Option[Found] = {
    val symbol = name.symbol
    if (symbol.isNone) None
    else
      resultType(symbol)
        .filter(result => isApplied || result.explicitParameterLists == 0)
        .map(_.shape)
        .filter(isListed)
        .map(shape => Found(name.value, render(shape)))
  }

  private def isListed(shape: TypeShape): Boolean =
    isListedSymbol(shape.symbol) || (shape.symbol == future && (shape.arguments match {
      case List(argument) => isListedSymbol(argument.symbol)
      case _ => false
    }))

  private def isListedSymbol(symbol: String): Boolean =
    listed.matches(Symbol(symbol))

  /** Scalafix's symbol table first, then the build's SemanticDB index, which
    * on Scala 3 is the only place a symbol from another file is described.
    */
  private def resultType(symbol: Symbol): Option[ResultType] =
    Try(symbol.info).toOption.flatten match {
      case Some(info) => fromSignature(info.signature)
      case None => index.resultType(symbol.value)
    }

  private def fromSignature(signature: Signature): Option[ResultType] =
    signature match {
      case MethodSignature(_, parameterLists, returnType) =>
        val explicit = parameterLists.count { parameters =>
          !parameters.headOption.exists(parameter => parameter.isImplicit || parameter.isGiven)
        }
        shape(returnType).map(ResultType(_, explicit))
      case ValueSignature(tpe) => shape(tpe).map(ResultType(_, 0))
      case _ => None
    }

  private def shape(tpe: SemanticType): Option[TypeShape] =
    tpe match {
      case TypeRef(_, symbol, arguments) =>
        val shapes = arguments.map(shape)
        if (shapes.forall(_.isDefined)) Some(TypeShape(symbol.value, shapes.flatten)) else None
      case ByNameType(inner) => shape(inner)
      case AnnotatedType(_, inner) => shape(inner)
      case _ => None
    }

  /** `Future[PermissionResult.Result]`: each type by its name, qualified by
    * the object it is defined in, if any.
    */
  private def render(shape: TypeShape): String = {
    val symbol = Symbol(shape.symbol)
    val owner = symbol.owner
    val name =
      if (owner.isGlobal && owner.value.endsWith(".")) s"${owner.displayName}.${symbol.displayName}"
      else symbol.displayName
    if (shape.arguments.isEmpty) name else s"$name[${shape.arguments.map(render).mkString(", ")}]"
  }
}

/** The method or value whose result is thrown away, and its type. */
final case class Found(name: String, tpe: String)

final case class DiscardedValueDiagnostic(position: Position, found: Found, hint: String) extends Diagnostic {
  override def message: String = {
    val base = s"This throws away the `${found.tpe}` that `${found.name}` returns."
    if (hint.isEmpty) base else s"$base $hint"
  }
}
