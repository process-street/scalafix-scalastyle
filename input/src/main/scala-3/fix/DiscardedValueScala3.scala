/*
rule = DiscardedValueChecker
DiscardedValueChecker.types = ["fix.Permission.Result"]
*/
package fix

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait UsingPermissions:
  def canReadAs(id: Int)(using user: String): Future[Permission.Result]

class DiscardedValueScala3(permissions: Permissions, usingPermissions: UsingPermissions)(using ExecutionContext):
  given String = "user"

  def usingListLeftOut(id: Int): Future[Int] =
    for
      _ <- usingPermissions.canReadAs(id) // assert: DiscardedValueChecker
    yield id

  def usingListWritten(id: Int): Future[Int] =
    for
      _ <- usingPermissions.canReadAs(id)(using "other") // assert: DiscardedValueChecker
    yield id

  def braceless(id: Int): Int =
    permissions.canRead(id) // assert: DiscardedValueChecker
    id

  def bracelessLast(id: Int): Future[Permission.Result] =
    val next = id + 1
    permissions.canRead(next)
