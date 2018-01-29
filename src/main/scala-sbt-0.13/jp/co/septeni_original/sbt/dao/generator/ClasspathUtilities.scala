package jp.co.septeni_original.sbt.dao.generator

import java.io.File

import sbt.classpath.{ClasspathUtilities => CU}

object ClasspathUtilities {

  def toLoader(paths: Seq[File], parent: ClassLoader): ClassLoader =
    CU.toLoader(paths, parent)

  lazy val xsbtiLoader: ClassLoader = CU.xsbtiLoader

}
