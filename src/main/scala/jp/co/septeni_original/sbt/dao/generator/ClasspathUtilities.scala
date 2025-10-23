package jp.co.septeni_original.sbt.dao.generator

import java.io.File
import java.net.URLClassLoader

object ClasspathUtilities {

  def toLoader(paths: Seq[File], parent: ClassLoader): ClassLoader =
    new URLClassLoader(paths.map(_.toPath.toUri.toURL).toArray, parent)

  lazy val xsbtiLoader: ClassLoader = classOf[xsbti.Launcher].getClassLoader

}
