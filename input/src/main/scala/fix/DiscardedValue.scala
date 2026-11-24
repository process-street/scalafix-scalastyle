/*
rule = DiscardedValueChecker
DiscardedValueChecker.types = ["fix.Permission.Result"]
DiscardedValueChecker.hint = "Authorize on it."
*/
package fix

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import fix.Authorize.authorizeF

class DiscardedValue(permissions: Permissions)(implicit ec: ExecutionContext) {
  implicit val user: String = "user"

  // Flagged.

  def wildcardGenerator(id: Int): Future[Int] =
    for {
      _ <- permissions.canRead(id) /* assert: DiscardedValueChecker
           ^^^^^^^^^^^^^^^^^^^^^^^
This throws away the `Future[Permission.Result]` that `canRead` returns. Authorize on it.
*/
    } yield id

  def wildcardValue(id: Int): Future[Int] =
    for {
      _ <- Future.successful(id)
      _ = permissions.check(id) // assert: DiscardedValueChecker
    } yield id

  def implicitListLeftOut(id: Int): Future[Int] =
    for {
      _ <- permissions.canReadAs(id) // assert: DiscardedValueChecker
    } yield id

  def implicitListWritten(id: Int): Future[Int] =
    for {
      _ <- permissions.canReadAs(id)(user) // assert: DiscardedValueChecker
    } yield id

  def unreadGenerator(id: Int): Future[Int] =
    for {
      result <- permissions.canRead(id) // assert: DiscardedValueChecker
    } yield id

  def unreadValue(id: Int): Future[Int] =
    for {
      _ <- Future.successful(id)
      result = permissions.check(id) // assert: DiscardedValueChecker
    } yield id

  def wildcardVal(id: Int): Int = {
    val _ = permissions.canRead(id) // assert: DiscardedValueChecker
    id
  }

  def unreadLocalVal(id: Int): Int = {
    val result = permissions.canRead(id) // assert: DiscardedValueChecker
    id
  }

  def statement(id: Int): Int = {
    permissions.canRead(id) // assert: DiscardedValueChecker
    id
  }

  def wildcardCallback(id: Int): Future[Int] =
    permissions.canRead(id).flatMap(_ => Future.successful(id)) // assert: DiscardedValueChecker

  def unreadCallback(id: Int): Future[Int] =
    permissions.canRead(id).map { result => // assert: DiscardedValueChecker
      id
    }

  def unappliedValue(id: Int): Future[Int] =
    for {
      _ <- permissions.pending // assert: DiscardedValueChecker
    } yield id

  def sameFileMethod(id: Int): Future[Int] =
    for {
      _ <- declared(id) // assert: DiscardedValueChecker
    } yield id

  def inferredMethod(id: Int): Future[Int] =
    for {
      _ <- inferred(id) // assert: DiscardedValueChecker
    } yield id

  def localVal(id: Int): Future[Int] = {
    val pending = permissions.canRead(id)
    for {
      _ <- pending // assert: DiscardedValueChecker
    } yield id
  }

  def infix(id: Int): Future[Int] =
    for {
      _ <- Future.successful(id)
      _ = permissions.check(id) and permissions.check(id) // assert: DiscardedValueChecker
    } yield id

  // Left alone.

  def authorized(id: Int): Future[Int] =
    for {
      _ <- authorizeF(permissions.canRead(id))
    } yield id

  def mapped(id: Int): Future[Int] =
    for {
      _ <- permissions.canRead(id).map(_.toString)
    } yield id

  def readGenerator(id: Int): Future[Permission.Result] =
    for {
      result <- permissions.canRead(id)
    } yield result

  def readInGuard(id: Int): Future[Int] =
    for {
      result <- permissions.canRead(id)
      if result.isInstanceOf[Permission.Authorized]
    } yield id

  def readLocalVal(id: Int): Future[Permission.Result] = {
    val result = permissions.canRead(id)
    result
  }

  def matched(id: Int): Int = {
    permissions.check(id) match {
      case _: Permission.Authorized => 1
      case _ => 0
    }
    id
  }

  def lastStatement(id: Int): Future[Permission.Result] = {
    val next = id + 1
    permissions.canRead(next)
  }

  def readCallback(id: Int): Future[Int] =
    permissions.canRead(id).map { result =>
      if (result.isInstanceOf[Permission.Authorized]) id else 0
    }

  def notListed(id: Int): Future[Int] =
    for {
      _ <- permissions.canEdit(id)
    } yield id

  def generic(id: Int): Future[Int] =
    for {
      _ <- permissions.lookup[Permission.Result](id)
    } yield id

  def functionValue(id: Int): Future[Int] = {
    val check: Int => Future[Permission.Result] = permissions.canRead
    for {
      _ <- check(id)
    } yield id
  }

  def subtype(id: Int): Future[Int] =
    for {
      _ <- Future.successful(Permission.Authorized(id))
    } yield id

  val member = permissions.canRead(0)

  private def declared(id: Int): Future[Permission.Result] = permissions.canRead(id)

  private def inferred(id: Int) = permissions.canRead(id)
}

class DiscardedValueInTemplate(permissions: Permissions) {
  permissions.canRead(0) // assert: DiscardedValueChecker
}
