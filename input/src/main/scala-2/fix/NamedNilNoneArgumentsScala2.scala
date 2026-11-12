/*
rule = NamedNilNoneArgumentsChecker
*/
package fix

object NamedNilNoneArgumentsScala2 {
  // Scala 2 records which `apply` a call binds to only with synthetics on, so
  // the rule collects every reachable `apply`, and in each of these they
  // disagree on the name.
  val inheritedApply = Setting(Some("a"), None)
  val overloadedApply = Overloaded(1, None)
}
