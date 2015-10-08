package jp.co.septeni_original.sbt.dao.generator

import sbt._

trait SbtDaoGeneratorKeys {

  val generator = taskKey[Unit]("generator")

  val driverClassName = settingKey[String]("driver-class-name")

  val jdbcUrl = settingKey[String]("jdbc-url")

  val jdbcUser = settingKey[String]("jdbc-user")

  val jdbcPassword = settingKey[String]("jdbc-password")

  val schemaName = settingKey[Option[String]]("schema-name")

  val generateAll = taskKey[Seq[File]]("generate")

  val generateOne = inputKey[Option[File]]("generateTable")

  val templateDirectory = settingKey[File]("template-dir")

  val templateName = settingKey[String]("template-file")

  val typeNameMapper = settingKey[String => String]("type-mapper")

  val tableNameFilter = settingKey[String => Boolean]("table-name-filter")

  val modelNameMapper = settingKey[String => String]("model-name-mapper")

  val propertyNameMapper = settingKey[String => String]("property-name-mapper")
}

object SbtDaoGeneratorKeys extends SbtDaoGeneratorKeys