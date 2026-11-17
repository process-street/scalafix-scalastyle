package fix

import scala.meta.Defn
import scala.meta.Enumerator
import scala.meta.Pat
import scala.meta.Position
import scala.meta.Template
import scala.meta.Term
import scala.meta.Tree
import scalafix.v1.Diagnostic
import scalafix.v1.Patch
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule

/** Flags a local `val`, `var` or `def`, or a `name = expr` binding in a `for`,
  * that takes the name of a parameter of the method it is defined in, or of an
  * enclosing method. From that point on the name means the local, so the
  * parameter is silently ignored.
  *
  * Lambda parameters, `case` binders and `for` generators are deliberately
  * left alone: rebinding a name there (`tasks.map { task => ... }`) is common
  * and intended.
  */
class ParameterShadowingChecker
    extends SyntacticRule("ParameterShadowingChecker") {
  override def fix(implicit doc: SyntacticDocument): Patch =
    ParameterShadowingChecker
      .diagnostics(doc.tree, Map.empty)
      .map(Patch.lint)
      .asPatch
}

object ParameterShadowingChecker {
  // Each parameter name in scope, mapped to the method that declares it.
  private type Scope = Map[String, String]

  private def diagnostics(tree: Tree, scope: Scope): List[Diagnostic] =
    tree match {
      case method: Defn.Def =>
        shadowing(method.name, scope) ++
          diagnostics(method.body, scope ++ parameters(method))
      case _: Template =>
        // A class, trait or object, named or anonymous, starts afresh: its
        // members are not locals of the method around it.
        tree.children.flatMap(diagnostics(_, Map.empty))
      case value: Defn.Val =>
        value.pats.flatMap(boundNames).flatMap(shadowing(_, scope)) ++
          diagnostics(value.rhs, scope)
      case variable: Defn.Var =>
        variable.pats.flatMap(boundNames).flatMap(shadowing(_, scope)) ++
          diagnostics(variable.body, scope)
      case binding: Enumerator.Val =>
        // `name = expr` in a `for` defines a value, unlike a `<-` generator.
        boundNames(binding.pat).flatMap(shadowing(_, scope)) ++
          diagnostics(binding.rhs, scope)
      case _ =>
        tree.children.flatMap(diagnostics(_, scope))
    }

  // Every parameter of every clause, `using` and `implicit` ones included,
  // plus an extension method's receiver.
  private def parameters(method: Defn.Def): Scope = {
    val clauses =
      extensionGroup(method).flatMap(_.paramClauseGroup).toList ++
        method.paramClauseGroups
    clauses
      .flatMap(_.paramClauses)
      .flatMap(_.values)
      .collect { case Term.Param(_, name: Term.Name, _, _) => name.value }
      .map(_ -> method.name.value)
      .toMap
  }

  private def extensionGroup(method: Defn.Def): Option[Defn.ExtensionGroup] =
    method.parent.flatMap {
      case group: Defn.ExtensionGroup => Some(group)
      case block =>
        block.parent.collect { case group: Defn.ExtensionGroup => group }
    }

  private def boundNames(pat: Pat): List[Term.Name] =
    pat.collect { case Pat.Var(name) => name }

  private def shadowing(name: Term.Name, scope: Scope): List[Diagnostic] =
    scope
      .get(name.value)
      .map(method => ParameterShadowingDiagnostic(name.pos, name.value, method))
      .toList
}

final case class ParameterShadowingDiagnostic(
    position: Position,
    parameter: String,
    method: String
) extends Diagnostic {
  override def message: String =
    s"Local definition shadows parameter `$parameter` of method `$method`."
}
