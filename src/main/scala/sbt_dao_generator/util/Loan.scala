package sbt_dao_generator.util

import scala.util._
import scala.util.control.NonFatal

@deprecated("will be removed", "2.1.0")
object Loan {

  def using[A <: AutoCloseable, B](resource: A)(func: A => Try[B]): Try[B] =
    func(resource)
      .recoverWith { case NonFatal(e) =>
        Failure(e)
      }
      .map { r =>
        resource.close()
        r
      }

  def using[A <: AutoCloseable, B](resource: Try[A])(func: A => Try[B]): Try[B] =
    resource.flatMap { r =>
      func(r)
        .recoverWith { case NonFatal(e) =>
          Failure(e)
        }
        .map { v =>
          r.close()
          v
        }
    }

}
