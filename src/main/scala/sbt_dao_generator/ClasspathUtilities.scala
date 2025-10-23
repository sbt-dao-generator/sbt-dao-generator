package sbt_dao_generator

import java.io.File
import java.net.URLClassLoader

object ClasspathUtilities {

  def toLoader(paths: Seq[File], parent: ClassLoader): ClassLoader =
    new URLClassLoader(paths.map(_.toPath.toUri.toURL).toArray, parent)

  lazy val xsbtiLoader: ClassLoader = classOf[xsbti.Launcher].getClassLoader

}
