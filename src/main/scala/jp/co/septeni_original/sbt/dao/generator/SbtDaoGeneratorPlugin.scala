package jp.co.septeni_original.sbt.dao.generator

import org.seasar.util.lang.StringUtil
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object SbtDaoGeneratorPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = JvmPlugin

  object autoImport extends SbtDaoGeneratorKeys

  import SbtDaoGeneratorKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    driverClassName in generator := "",
    jdbcUrl in generator := "",
    jdbcUser in generator := "",
    jdbcPassword in generator := "",
    schemaName in generator := None,
    templateDirectory in generator := baseDirectory.value / "templates",
    templateName in generator := "template.ftl",
    typeNameMapper in generator := identity,
    tableNameFilter in generator := { _: String => true },
    modelNameMapper in generator := { tableName: String =>
      StringUtil.camelize(tableName)
    },
    propertyNameMapper in generator := { columnName: String =>
      StringUtil.decapitalize(StringUtil.camelize(columnName))
    },
    generateAll in generator <<= SbtDaoGenerator.generateAllTask,
    generateOne in generator <<= SbtDaoGenerator.generateOneTask
  )

}
