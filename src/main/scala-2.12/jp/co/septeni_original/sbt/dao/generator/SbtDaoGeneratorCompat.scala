package jp.co.septeni_original.sbt.dao.generator

import sbt._
import sbt.Keys._

private[generator] trait SbtDaoGeneratorCompat {
  val compileManagedClasspathValue: Def.Initialize[Task[Seq[File]]] = Def.task {
    (Compile / managedClasspath).value.map(_.data)
  }

  implicit class DefOps(self: sbt.Def.type) {
    def uncached[A](a: A): A = a
  }
}
