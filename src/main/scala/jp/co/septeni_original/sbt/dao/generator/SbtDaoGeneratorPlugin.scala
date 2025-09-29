package jp.co.septeni_original.sbt.dao.generator

import jp.co.septeni_original.sbt.dao.generator.model.{ ColumnDesc, TableDesc }
import org.scalafmt.interfaces.{ Scalafmt, ScalafmtSession }
import sbt.Keys._
import sbt.{ *, given }
import sbt.plugins.JvmPlugin

/**
  * sbt-dao-generatorのプラグイン定義。
  */
object SbtDaoGeneratorPlugin extends AutoPlugin with SbtDaoGeneratorCompat {

  override def trigger = allRequirements

  override def requires: Plugins = JvmPlugin

  object autoImport extends SbtDaoGeneratorKeys

  import SbtDaoGeneratorKeys._

  @transient
  private[generator] val daoGeneratorScalafmtInstance =
    taskKey[Option[ScalafmtSession]]("").withRank(KeyRanks.Invisible)

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
      val log = streams.value.log
      // https://github.com/scalameta/sbt-scalafmt/blob/e59fc02237374e6/plugin/src/main/scala/org/scalafmt/sbt/ScalafmtPlugin.scala#L42-L45
      TaskKey[File]("scalafmtConfig").?.value.filter(_.isFile).map { conf =>
        Scalafmt
          .create(this.getClass.getClassLoader)
          .withReporter(new MyScalafmtReporter(log))
          .createSession(conf.toPath)
      }
    }
  )

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    generator / daoGeneratorScalafmt := true,
    generator / enableManagedClassPath := true,
    generator / driverClassName := "",
    generator / jdbcUrl := "",
    generator / jdbcUser := "",
    generator / jdbcPassword := "",
    generator / schemaName := None,
    generator / templateDirectory := baseDirectory.value / "templates",
    generator / templateNameMapper := { (_: String) =>
      "template.ftl"
    },
    generator / propertyTypeNameMapper := DefaultPropertyTypeNameMapper,
    generator / advancedPropertyTypeNameMapper := { (s: String, _: TableDesc, _: ColumnDesc) => s },
    generator / tableNameFilter := { (_: String) =>
      true
    },
    generator / propertyNameMapper := { (columnName: String) =>
      StringUtil.decapitalize(StringUtil.camelize(columnName))
    },
    generator / typeNameMapper := (generator / propertyNameMapper).value,
    generator / classNameMapper := { (tableName: String) =>
      Seq(StringUtil.camelize(tableName))
    },
    generator / outputDirectoryMapper := { (_: String) =>
      (Compile / sourceManaged).value
    },
    generator / generateAll := Def.uncached(SbtDaoGenerator.generateAllTask.value),
    generator / generateMany := SbtDaoGenerator.generateManyTask.evaluated,
    generator / generateOne := SbtDaoGenerator.generateOneTask.evaluated
  )

}
