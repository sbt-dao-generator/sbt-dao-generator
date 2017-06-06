package jp.co.septeni_original.sbt.dao.generator

import org.seasar.util.lang.StringUtil
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
    driverClassName in generator := "",
    jdbcUrl in generator := "",
    jdbcUser in generator := "",
    jdbcPassword in generator := "",
    schemaName in generator := None,
    templateDirectory in generator := baseDirectory.value / "templates",
    templateNameMapper in generator := { _: String => "template.ftl" },
    typeNameMapper in generator := identity,
    tableNameFilter in generator := { _: String => true },
    propertyNameMapper in generator := { columnName: String =>
      StringUtil.decapitalize(StringUtil.camelize(columnName))
    },
    classNameMapper in generator := { tableName: String => Seq(StringUtil.camelize(tableName)) },
    outputDirectoryMapper in generator := { (modelName: String) => (sourceManaged in Compile).value },
    generateAll in generator := SbtDaoGenerator.generateAllTask.value,
    generateMany in generator <<= SbtDaoGenerator.generateManyTask,
    generateOne in generator <<= SbtDaoGenerator.generateOneTask
  )

}
