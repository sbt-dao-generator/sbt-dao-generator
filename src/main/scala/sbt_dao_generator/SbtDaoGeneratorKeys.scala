package sbt_dao_generator

import sbt._
import sbt_dao_generator.model.ColumnDesc
import sbt_dao_generator.model.TableDesc

/**
  * Key definitions for sbt-dao-generator.
  */
trait SbtDaoGeneratorKeys {

  val daoGeneratorDriverClassName = settingKey[String]("driver-class-name")

  val daoGeneratorJdbcUrl = settingKey[String]("jdbc-url")

  val daoGeneratorJdbcUser = settingKey[String]("jdbc-user")

  val daoGeneratorJdbcPassword = settingKey[String]("jdbc-password")

  val daoGeneratorSchemaName = settingKey[Option[String]]("schema-name")

  val daoGeneratorGenerateAll = taskKey[Seq[File]]("generate-all")

  val daoGeneratorGenerateOne = inputKey[Seq[File]]("generate-one")

  val daoGeneratorGenerateMany = inputKey[Seq[File]]("generate-many")

  val daoGeneratorTemplateDirectory = settingKey[File]("template-dir")

  val daoGeneratorClassNameMapper = settingKey[String => Seq[String]]("class-name-mapper")

  val daoGeneratorTemplateNameMapper = settingKey[String => String]("template-name-mapper")

  val daoGeneratorPropertyTypeNameMapper = settingKey[String => String]("property-type-mapper")

  val daoGeneratorAdvancedPropertyTypeNameMapper =
    settingKey[(String, TableDesc, ColumnDesc) => String]("advanced-property-type-mapper")

  val daoGeneratorTableNameFilter = settingKey[String => Boolean]("table-name-filter")

  val daoGeneratorPropertyNameMapper = settingKey[String => String]("property-name-mapper")

  val daoGeneratorOutputDirectoryMapper = settingKey[String => File]("output-directory-mapper")

  val daoGeneratorEnableManagedClassPath = settingKey[Boolean]("enable-managed-class-path")

  val daoGeneratorScalafmt = settingKey[Boolean]("")
}

object SbtDaoGeneratorKeys extends SbtDaoGeneratorKeys
