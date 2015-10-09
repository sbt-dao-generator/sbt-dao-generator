package jp.co.septeni_original.sbt.dao.generator

import java.io._
import java.sql.{ Connection, Driver, ResultSet }

import jp.co.septeni_original.sbt.dao.generator.SbtDaoGeneratorKeys._
import jp.co.septeni_original.sbt.dao.generator.model.{ ColumnDesc, PrimaryKeyDesc, TableDesc }
import org.seasar.util.lang.StringUtil
import sbt.Keys._
import sbt.classpath.ClasspathUtilities
import sbt.complete.Parser
import sbt.{ File, _ }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

trait SbtDaoGenerator {

  private[generator] def getJdbcConnection(classLoader: ClassLoader, driverClassName: String, jdbcUrl: String, jdbcUser: String, jdbcPassword: String) = {
    val driver = classLoader.loadClass(driverClassName).newInstance().asInstanceOf[Driver]
    val info = new java.util.Properties()
    info.put("user", jdbcUser)
    info.put("password", jdbcPassword)
    val connection = driver.connect(jdbcUrl, info)
    connection
  }

  private[generator] def getTables(conn: Connection, schemaName: Option[String]): Seq[String] = {
    val dbMeta = conn.getMetaData
    val types = Array("TABLE")
    var rs: ResultSet = null
    try {
      rs = dbMeta.getTables(null, schemaName.orNull, "%", types)
      val lb = ListBuffer[String]()
      while (rs.next()) {
        if (rs.getString("TABLE_TYPE") == "TABLE") {
          lb += rs.getString("TABLE_NAME")
        }
      }
      lb.result()
    } finally {
      if (rs != null) {
        rs.close()
      }
    }

  }

  private[generator] def getColumnDescs(conn: Connection, schemaName: Option[String], tableName: String): Seq[ColumnDesc] = {
    val dbMeta = conn.getMetaData
    var rs: ResultSet = null
    try {
      rs = dbMeta.getColumns(null, schemaName.orNull, tableName, "%")
      val lb = ListBuffer[ColumnDesc]()
      while (rs.next()) {
        lb += ColumnDesc(
          rs.getString("COLUMN_NAME"),
          rs.getString("TYPE_NAME"),
          rs.getString("IS_NULLABLE") == "YES",
          Option(rs.getString("COLUMN_SIZE")).map(_.toInt))
      }
      lb.result()
    } finally {
      if (rs != null) {
        rs.close()
      }
    }
  }

  private[generator] def getPrimaryKeyDescs(conn: Connection, schemaName: Option[String], tableName: String): Seq[PrimaryKeyDesc] = {
    val dbMeta = conn.getMetaData
    var rs: ResultSet = null
    try {
      rs = dbMeta.getPrimaryKeys(null, schemaName.orNull, tableName)
      val lb = ListBuffer[PrimaryKeyDesc]()
      while (rs.next()) {
        lb += PrimaryKeyDesc(
          rs.getString("PK_NAME"),
          rs.getString("COLUMN_NAME"),
          rs.getString("KEY_SEQ")
        )
      }
      lb.result()
    } finally {
      if (rs != null) {
        rs.close()
      }
    }
  }

  private[generator] def getTableDescs(conn: Connection, schemaName: Option[String]): Seq[TableDesc] = {
    getTables(conn, schemaName).map { tableName =>
      TableDesc(tableName, getPrimaryKeyDescs(conn, schemaName, tableName), getColumnDescs(conn, schemaName, tableName))
    }
  }

  private[generator] def createPrimaryKeys(typeNameMapper: String => String, propertyNameMapper: String => String, tableDesc: TableDesc): Seq[Map[String, Any]] = {
    val primaryKeys = tableDesc.primaryDescs.map { key =>
      val column = tableDesc.columnDescs.find(_.columnName == key.cloumnName).get
      Map[String, Any](
        "name" -> propertyNameMapper(key.cloumnName),
        "camelizeName" -> StringUtil.camelize(key.cloumnName),
        "typeName" -> typeNameMapper(column.typeName),
        "nullable" -> column.nullable
      )
    }
    primaryKeys
  }

  private[generator] def createColumns(typeNameMapper: String => String, propertyNameMapper: String => String, tableDesc: TableDesc): Seq[Map[String, Any]] = {
    val columns = tableDesc.columnDescs
      .filterNot(e => tableDesc.primaryDescs.map(_.cloumnName).contains(e.columnName))
      .map { column =>
        Map[String, Any](
          "name" -> propertyNameMapper(column.columnName),
          "camelizeName" -> StringUtil.camelize(column.columnName),
          "typeName" -> typeNameMapper(column.typeName),
          "nullable" -> column.nullable
        )
      }
    columns
  }

  private[generator] def createContext(primaryKeys: Seq[Map[String, Any]], columns: Seq[Map[String, Any]], modelName: String) = {
    val context = Map[String, Any](
      "name" -> modelName,
      "lowerCamelName" -> (modelName.substring(0, 1).toLowerCase + modelName.substring(1)),
      "primaryKeys" -> primaryKeys.map(_.asJava).asJava,
      "columns" -> columns.map(_.asJava).asJava,
      "primaryKeysWithColumns" -> (primaryKeys ++ columns).map(_.asJava).asJava
    ).asJava
    context
  }

  private[generator] def createFile(outputDirectory: File, modelName: String): File = {
    val file = outputDirectory / (modelName + ".scala")
    file
  }

  private[generator] def generateFile(logger: Logger,
                                      cfg: freemarker.template.Configuration,
                                      templateNameMapper: String => String,
                                      tableDesc: TableDesc,
                                      typeNameMapper: String => String,
                                      modelNameMapper: String => String,
                                      propertyNameMapper: String => String,
                                      outputDirectory: File) = {
    var writer: FileWriter = null
    try {
      val templateName = templateNameMapper(tableDesc.tableName)
      val template = cfg.getTemplate(templateName)
      val modelName = modelNameMapper(tableDesc.tableName)
      val file = createFile(outputDirectory, modelName)
      logger.info(s"tableName = ${tableDesc.tableName}, templateName = $templateName,  generate file = $file")
      writer = new FileWriter(file)
      val primaryKeys = createPrimaryKeys(typeNameMapper, propertyNameMapper, tableDesc)
      val columns = createColumns(typeNameMapper, propertyNameMapper, tableDesc)
      val context = createContext(primaryKeys, columns, modelName)
      template.process(context, writer)
      writer.flush()
      file
    } finally {
      if (writer != null)
        writer.close()
    }
  }

  private[generator] def generateOne(logger: Logger,
                                     conn: Connection,
                                     typeNameMapper: String => String,
                                     tableNameFilter: String => Boolean,
                                     modelNameMapper: String => String,
                                     propertyNameMapper: String => String,
                                     schemaName: Option[String],
                                     tableName: String,
                                     templateDirectory: File,
                                     templateNameMapper: String => String,
                                     outputDirectory: File): Option[File] = {
    val cfg = new freemarker.template.Configuration(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
    cfg.setDirectoryForTemplateLoading(templateDirectory)

    if (!outputDirectory.exists())
      IO.createDirectory(outputDirectory)

    getTableDescs(conn, schemaName).filter {
      tableDesc =>
        tableNameFilter(tableDesc.tableName)
    }.find(_.tableName == tableName).map { tableDesc =>
      generateFile(logger, cfg, templateNameMapper, tableDesc, typeNameMapper, modelNameMapper, propertyNameMapper, outputDirectory)
    }
  }

  private[generator] def generateAll(logger: Logger,
                                     conn: Connection,
                                     typeNameMapper: String => String,
                                     tableNameFilter: String => Boolean,
                                     modelNameMapper: String => String,
                                     propertyNameMapper: String => String,
                                     schemaName: Option[String],
                                     templateDirectory: File,
                                     templateNameMapper: String => String,
                                     outputDirectory: File): Seq[File] = {
    val cfg = new freemarker.template.Configuration(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
    cfg.setDirectoryForTemplateLoading(templateDirectory)

    if (!outputDirectory.exists())
      IO.createDirectory(outputDirectory)

    getTableDescs(conn, schemaName).filter {
      tableDesc =>
        tableNameFilter(tableDesc.tableName)
    }.map { tableDesc =>
      generateFile(logger, cfg, templateNameMapper, tableDesc, typeNameMapper, modelNameMapper, propertyNameMapper, outputDirectory)
    }
  }

  import complete.DefaultParsers._

  def generateOneTask: Def.Initialize[InputTask[Option[File]]] = {
    val oneStringParser: Parser[String] = token(Space ~> StringBasic, "TableName")
    Def.inputTask {
      val tableName = oneStringParser.parsed
      val logger = streams.value.log
      var conn: Connection = null
      try {
        logger.info("tableName = " + tableName)
        logger.info("driverClassName = " + (driverClassName in generator).value.toString())
        logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString())
        logger.info("jdbcUser = " + (jdbcUser in generator).value.toString())
        logger.info("jdbcPassword = " + (jdbcPassword in generator).value.toString())
        conn = getJdbcConnection(
          ClasspathUtilities.toLoader(
            (managedClasspath in Compile).value.map(_.data),
            ClasspathUtilities.xsbtiLoader
          ),
          (driverClassName in generator).value,
          (jdbcUrl in generator).value,
          (jdbcUser in generator).value,
          (jdbcPassword in generator).value
        )
        generateOne(
          logger,
          conn,
          (typeNameMapper in generator).value,
          (tableNameFilter in generator).value,
          (modelNameMapper in generator).value,
          (propertyNameMapper in generator).value,
          (schemaName in generator).value,
          tableName,
          (templateDirectory in generator).value,
          (templateNameMapper in generator).value,
          (sourceManaged in Compile).value
        )
      } finally {
        if (conn != null)
          conn.close()
      }
    }
  }

  def generateAllTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val logger = streams.value.log
    var conn: Connection = null
    try {
      logger.info("driverClassName = " + (driverClassName in generator).value.toString())
      logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString())
      logger.info("jdbcUser = " + (jdbcUser in generator).value.toString())
      logger.info("jdbcPassword = " + (jdbcPassword in generator).value.toString())
      conn = getJdbcConnection(
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        ),
        (driverClassName in generator).value,
        (jdbcUrl in generator).value,
        (jdbcUser in generator).value,
        (jdbcPassword in generator).value
      )
      generateAll(
        logger,
        conn,
        (typeNameMapper in generator).value,
        (tableNameFilter in generator).value,
        (modelNameMapper in generator).value,
        (propertyNameMapper in generator).value,
        (schemaName in generator).value,
        (templateDirectory in generator).value,
        (templateNameMapper in generator).value,
        (sourceManaged in Compile).value
      )
    } finally {
      if (conn != null)
        conn.close()
    }
  }

}

object SbtDaoGenerator extends SbtDaoGenerator
