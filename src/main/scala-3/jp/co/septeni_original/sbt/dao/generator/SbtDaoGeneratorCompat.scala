package jp.co.septeni_original.sbt.dao.generator

import sbt.{ *, given }
import sbt.Keys._

private[generator] trait SbtDaoGeneratorCompat { self: SbtDaoGenerator =>
  val compileManagedClasspathValue: Def.Initialize[Task[Seq[File]]] = Def.task {
    val converter = fileConverter.value
    (Compile / managedClasspath).value.map(x => converter.toPath(x.data).toFile)
  }
}
