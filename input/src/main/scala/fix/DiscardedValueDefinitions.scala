/*
rule = DiscardedValueChecker
DiscardedValueChecker.types = ["fix.Permission.Result"]
*/
package fix

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

// Defined apart from the checks in DiscardedValue, so on Scala 3 the rule has
// to read these result types from the build's SemanticDB index.
object Permission {
  sealed trait Result {
    def and(that: Result): Result = this
  }

  final case class Authorized(id: Int) extends Result

  final case class Unauthorized(message: String) extends Result
}

trait Permissions {
  def canRead(id: Int): Future[Permission.Result]

  def canReadAs(id: Int)(implicit user: String): Future[Permission.Result]

  def check(id: Int): Permission.Result

  def pending: Future[Permission.Result]

  def canEdit(id: Int): Future[Boolean]

  def lookup[A](id: Int): Future[A]
}

object Authorize {
  def authorizeF(check: Future[Permission.Result])(implicit ec: ExecutionContext): Future[Permission.Authorized] =
    check.map {
      case authorized: Permission.Authorized => authorized
      case _ => throw new IllegalStateException("refused")
    }
}
