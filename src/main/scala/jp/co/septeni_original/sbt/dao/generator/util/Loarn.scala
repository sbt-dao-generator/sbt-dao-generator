package jp.co.septeni_original.sbt.dao.generator.util

import scala.util._
import scala.util.control.NonFatal

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
