/*
rule = ParameterShadowingChecker
*/
package fix

final case class ShadowingContext(name: String)

object ParameterShadowingScala3 {
  def usingClause(x: Int)(using ctx: ShadowingContext): Int = {
    val ctx = ShadowingContext("local") /* assert: ParameterShadowingChecker
        ^^^
Local definition shadows parameter `ctx` of method `usingClause`.
*/
    x + ctx.name.length
  }

  // An anonymous `using` parameter has no name to shadow.
  def anonymousUsing(x: Int)(using ShadowingContext): Int = {
    val ctx = summon[ShadowingContext]
    x + ctx.name.length
  }

  def braceless(x: Int): Int =
    val x = 2 // assert: ParameterShadowingChecker
    x

  extension (s: String)
    def shout: String = {
      val s = "local" // assert: ParameterShadowingChecker
      s.toUpperCase
    }

  extension (n: Int) {
    def double: Int = {
      val n = 1 // assert: ParameterShadowingChecker
      n * 2
    }

    def triple: Int = n * 3
  }

  // Lambda parameters and case binders may rebind a parameter.
  def rebinding(ctx: ShadowingContext, contexts: List[ShadowingContext]): List[String] =
    contexts.map(ctx => ctx.name) ++
      contexts.collect { case ctx if ctx.name.nonEmpty => ctx.name }
}
