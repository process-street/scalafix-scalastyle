package fix

object NamedNilNoneArgumentsScala3 {
  def givens(first: Int)(using items: List[Int], label: Option[Int]): Int = first

  extension (value: Int) {
    def described(items: List[Int], label: Option[Int]): Int = value
    def curried(first: Int, second: Int)(items: List[Int], label: Option[Int]): Int = value
  }

  // Rewritten.
  val constructorProxy = Plain(items = Nil, label = None)
  val extensionAsMethod = described(1)(items = Nil, label = None)

  // Left alone.
  val explicitUsing = givens(1)(using Nil, None)
  val extensionCall = 1.described(Nil, None)
  val curriedExtensionCall = 1.curried(2, 3)(Nil, None)
}
