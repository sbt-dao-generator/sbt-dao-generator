package jp.co.septeni_original.sbt.dao.generator

import sbt._

/**
 * sbt-dao-generatorのキー定義。
 */
trait SbtDaoGeneratorKeys {

  val generator = taskKey[Unit]("generator")

  val driverClassName = settingKey[String]("driver-class-name")

  val jdbcUrl = settingKey[String]("jdbc-url")

  val jdbcUser = settingKey[String]("jdbc-user")

  val jdbcPassword = settingKey[String]("jdbc-password")

  val schemaName = settingKey[Option[String]]("schema-name")

  val generateAll = taskKey[Seq[File]]("generate-all")

  val generateOne = inputKey[Seq[File]]("generate-one")

  val generateMany = inputKey[Seq[File]]("generate-many")

  val templateDirectory = settingKey[File]("template-dir")

  val classNameMapper = settingKey[String => Seq[String]]("class-name-mapper")

  val templateNameMapper = settingKey[String => String]("template-name-mapper")

  val propertyTypeNameMapper = settingKey[String => String]("type-mapper")

  val tableNameFilter = settingKey[String => Boolean]("table-name-filter")

  val propertyNameMapper = settingKey[String => String]("property-name-mapper")

  val outputDirectoryMapper = settingKey[String => File]("output-directory-mapper")

  val enableManagedClassPath = settingKey[Boolean]("enable-managed-class-path")

}

object SbtDaoGeneratorKeys extends SbtDaoGeneratorKeys