package jp.co.septeni_original.sbt.dao.generator

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

/**
  * sbt-dao-generatorのプラグイン定義。
  */
object SbtDaoGeneratorPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = JvmPlugin

  object autoImport extends SbtDaoGeneratorKeys

  import SbtDaoGeneratorKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    generator / enableManagedClassPath := true,
    generator / driverClassName := "",
    generator / jdbcUrl := "",
    generator / jdbcUser := "",
    generator / jdbcPassword := "",
    generator / schemaName := None,
    generator / templateDirectory := baseDirectory.value / "templates",
    generator / templateNameMapper := { _: String =>
      "template.ftl"
    },
    generator / propertyTypeNameMapper := identity,
    generator / tableNameFilter := { _: String =>
      true
    },
    generator / propertyNameMapper := { columnName: String =>
      StringUtil.decapitalize(StringUtil.camelize(columnName))
    },
    generator / typeNameMapper := (generator / propertyNameMapper).value,
    generator / classNameMapper := { tableName: String =>
      Seq(StringUtil.camelize(tableName))
    },
    generator / outputDirectoryMapper := { _: String =>
      (Compile / sourceManaged).value
    },
    generator / generateAll := SbtDaoGenerator.generateAllTask.value,
    generator / generateMany := SbtDaoGenerator.generateManyTask.evaluated,
    generator / generateOne := SbtDaoGenerator.generateOneTask.evaluated
  )

}
