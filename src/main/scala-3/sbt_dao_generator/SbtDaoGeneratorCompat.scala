package sbt_dao_generator

import sbt.*
import sbt.Keys._
import sbt.given

private[sbt_dao_generator] trait SbtDaoGeneratorCompat {
  val compileManagedClasspathValue: Def.Initialize[Task[Seq[File]]] = Def.task {
    val converter = fileConverter.value
    (Compile / managedClasspath).value.map(x => converter.toPath(x.data).toFile)
  }
}
