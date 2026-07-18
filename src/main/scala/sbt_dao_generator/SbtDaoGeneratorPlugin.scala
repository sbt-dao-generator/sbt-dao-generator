package sbt_dao_generator

import org.scalafmt.interfaces.RepositoryPackageDownloaderFactory
import org.scalafmt.interfaces.Scalafmt
import org.scalafmt.interfaces.ScalafmtSession
import sbt_dao_generator.model.ColumnDesc
import sbt_dao_generator.model.TableDesc
// format: off
import sbt.{*, given}
// format: on
import sbt.Keys._
import sbt.plugins.JvmPlugin

/**
  * Plugin definition for sbt-dao-generator.
  */
object SbtDaoGeneratorPlugin extends AutoPlugin with SbtDaoGeneratorCompat {

  override def trigger = allRequirements

  override def requires: Plugins = JvmPlugin

  object autoImport extends SbtDaoGeneratorKeys

  import SbtDaoGeneratorKeys._

  @transient
  private[sbt_dao_generator] val daoGeneratorScalafmtInstance =
    taskKey[Option[xsbti.api.Lazy[ScalafmtSession]]]("").withRank(KeyRanks.Invisible)

  // https://github.com/scalameta/scalafmt/blob/b78a999c191d5afc955/scalafmt-dynamic/jvm/src/main/scala/org/scalafmt/dynamic/ConsoleScalafmtReporter.scala
  private class MyScalafmtReporter(log: Logger) extends org.scalafmt.interfaces.ScalafmtReporter {
    def downloadOutputStreamWriter(): java.io.OutputStreamWriter =
      new java.io.OutputStreamWriter(scala.Console.out)
    def downloadWriter(): java.io.PrintWriter =
      new java.io.PrintWriter(scala.Console.out)
    def error(file: java.nio.file.Path, message: String): Unit =
      log.error(s"error: ${file}: ${message}")
    def error(file: java.nio.file.Path, e: Throwable): Unit = {
      log.error(s"error: ${file}: ${e}")
      e.printStackTrace()
    }
    def excluded(filename: java.nio.file.Path): Unit =
      log.info(s"file excluded: $filename")
    def parsedConfig(config: java.nio.file.Path, scalafmtVersion: String): Unit =
      log.debug(s"parsed scalafmt config (v$scalafmtVersion): $config")
  }

  override lazy val buildSettings: Seq[Setting[?]] = Def.settings(
    daoGeneratorScalafmtInstance := {
      val s = streams.value
      val log = s.log
      // https://github.com/scalameta/sbt-scalafmt/blob/4c8a4f79dbe5d9c/plugin/src/main/scala/org/scalafmt/sbt/ScalafmtPlugin.scala#L38-L42
      TaskKey[File]("scalafmtConfig").?.value.filter(_.isFile).map { conf =>
        xsbti.api.SafeLazy.apply { () =>
          try {
            val factory = {
              val Array(constructor) = Class
                .forName("org.scalafmt.sbt.ScalafmtSbtDependencyDownloader")
                .getConstructors()

              constructor
                .newInstance(
                  s,
                  (LocalRootProject / csrConfiguration).value: @sbtUnchecked,
                  (LocalRootProject / updateConfiguration).value: @sbtUnchecked
                )
                .asInstanceOf[RepositoryPackageDownloaderFactory]
            }

            Scalafmt
              .create(this.getClass.getClassLoader)
              .withRespectProjectFilters(true)
              .withRepositoryPackageDownloader(factory)
              .withReporter(new MyScalafmtReporter(log))
              .createSession(conf.toPath)
          } catch {
            case e: Throwable =>
              s.log.trace(e)
              Scalafmt
                .create(this.getClass.getClassLoader)
                .withReporter(new MyScalafmtReporter(log))
                .createSession(conf.toPath)
          }
        }
      }
    }
  )

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    daoGeneratorScalafmt := true,
    daoGeneratorEnableManagedClassPath := true,
    daoGeneratorDriverClassName := "",
    daoGeneratorJdbcUrl := "",
    daoGeneratorJdbcUser := "",
    daoGeneratorJdbcPassword := "",
    daoGeneratorSchemaName := None,
    daoGeneratorTemplateDirectory := baseDirectory.value / "templates",
    daoGeneratorTemplateNameMapper := { (_: String) =>
      "template.ftl"
    },
    daoGeneratorPropertyTypeNameMapper := DefaultPropertyTypeNameMapper,
    daoGeneratorAdvancedPropertyTypeNameMapper := { (s: String, _: TableDesc, _: ColumnDesc) => s },
    daoGeneratorTableNameFilter := { (_: String) =>
      true
    },
    daoGeneratorPropertyNameMapper := { (columnName: String) =>
      StringUtil.decapitalize(StringUtil.camelize(columnName))
    },
    daoGeneratorClassNameMapper := { (tableName: String) =>
      Seq(StringUtil.camelize(tableName))
    },
    daoGeneratorOutputDirectoryMapper := { (_: String) =>
      (Compile / sourceManaged).value
    },
    daoGeneratorGenerateAll := Def.uncached(SbtDaoGenerator.generateAllTask.value),
    daoGeneratorGenerateMany := SbtDaoGenerator.generateManyTask.evaluated,
    daoGeneratorGenerateOne := SbtDaoGenerator.generateOneTask.evaluated
  )

}
