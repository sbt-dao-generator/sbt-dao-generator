package jp.co.septeni_original.sbt.dao.generator

import java.io._
import java.sql.{Connection, Driver}

import jp.co.septeni_original.sbt.dao.generator.SbtDaoGeneratorKeys._
import jp.co.septeni_original.sbt.dao.generator.model.{ColumnDesc, PrimaryKeyDesc, TableDesc}
import jp.co.septeni_original.sbt.dao.generator.util.Loan._
import org.seasar.util.lang.StringUtil
import sbt.Keys._
import sbt.classpath.ClasspathUtilities
import sbt.complete.Parser
import sbt.{File, _}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}

trait SbtDaoGenerator {

  import complete.DefaultParsers._

  private val oneStringParser: Parser[String] = token(Space ~> StringBasic, "table name")

  private val manyStringParser: Parser[Seq[String]] = token(Space ~> StringBasic, "table name") +

  case class GeneratorContext(logger: Logger,
                              connection: Connection,
                              classNameMapper: String => Seq[String],
                              typeNameMapper: String => String,
                              tableNameFilter: String => Boolean,
                              propertyNameMapper: String => String,
                              schemaName: Option[String],
                              templateDirectory: File,
                              templateNameMapper: String => String,
                              outputDirectoryMapper: String => File)

  private[generator] def getJdbcConnection(classLoader: ClassLoader, driverClassName: String, jdbcUrl: String, jdbcUser: String, jdbcPassword: String): Try[Connection] = Try {
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
    using(dbMeta.getTables(null, schemaName.orNull, "%", types)) { rs =>
      val lb = ListBuffer[String]()
      while (rs.next()) {
        if (rs.getString("TABLE_TYPE") == "TABLE") {
          lb += rs.getString("TABLE_NAME")
        }
      }
      Success(lb.result())
    }.get
  }

  private[generator] def getColumnDescs(conn: Connection, schemaName: Option[String], tableName: String): Seq[ColumnDesc] = {
    val dbMeta = conn.getMetaData
    using(dbMeta.getColumns(null, schemaName.orNull, tableName, "%")) { rs =>
      val lb = ListBuffer[ColumnDesc]()
      while (rs.next()) {
        lb += ColumnDesc(
          rs.getString("COLUMN_NAME"),
          rs.getString("TYPE_NAME"),
          rs.getString("IS_NULLABLE") == "YES",
          Option(rs.getString("COLUMN_SIZE")).map(_.toInt))
      }
      Success(lb.result())
    }.get
  }

  private[generator] def getPrimaryKeyDescs(conn: Connection, schemaName: Option[String], tableName: String): Seq[PrimaryKeyDesc] = {
    val dbMeta = conn.getMetaData
    using(dbMeta.getPrimaryKeys(null, schemaName.orNull, tableName)) { rs =>
      val lb = ListBuffer[PrimaryKeyDesc]()
      while (rs.next()) {
        lb += PrimaryKeyDesc(
          rs.getString("PK_NAME"),
          rs.getString("COLUMN_NAME"),
          rs.getString("KEY_SEQ")
        )
      }
      Success(lb.result())
    }.get
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

  private[generator] def createContext(logger: Logger, primaryKeys: Seq[Map[String, Any]], columns: Seq[Map[String, Any]], className: String) = {
    val context = Map[String, Any](
      "name" -> className,
      "lowerCamelName" -> (className.substring(0, 1).toLowerCase + className.substring(1)),
      "primaryKeys" -> primaryKeys.map(_.asJava).asJava,
      "columns" -> columns.map(_.asJava).asJava,
      "primaryKeysWithColumns" -> (primaryKeys ++ columns).map(_.asJava).asJava
    ).asJava
    logger.debug(s"context = $context")
    context
  }

  private[generator] def createFile(outputDirectory: File, className: String): File = {
    val file = outputDirectory / (className + ".scala")
    file
  }

  private[generator] def generateFile(cfg: freemarker.template.Configuration,
                                      tableDesc: TableDesc,
                                      className: String,
                                      outputDirectory: File)(implicit ctx: GeneratorContext): Try[File] = {
    val templateName = ctx.templateNameMapper(className)
    val template = cfg.getTemplate(templateName)
    val file = createFile(outputDirectory, className)
    ctx.logger.info(s"tableName = ${tableDesc.tableName}, templateName = $templateName, generate file = $file")

    if (!outputDirectory.exists())
      IO.createDirectory(outputDirectory)

    using(new FileWriter(file)) { writer =>
      val primaryKeys = createPrimaryKeys(ctx.typeNameMapper, ctx.propertyNameMapper, tableDesc)
      val columns = createColumns(ctx.typeNameMapper, ctx.propertyNameMapper, tableDesc)
      val context = createContext(ctx.logger, primaryKeys, columns, className)
      template.process(context, writer)
      writer.flush()
      Success(file)
    }
  }

  private[generator] def foldGenerateFile(cfg: freemarker.template.Configuration, tableDesc: TableDesc)(implicit ctx: GeneratorContext) = {
    ctx.classNameMapper(tableDesc.tableName).foldLeft(Try(Seq.empty[File])) { (result, className) =>
      val outputTargetDirectory = ctx.outputDirectoryMapper(className)
      for {
        r <- result
        file <- generateFile(
          cfg,
          tableDesc,
          className,
          outputTargetDirectory)
      } yield {
        r :+ file
      }
    }
  }

  private[generator] def generateOne(tableName: String)(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    val cfg = createTemplateConfiguration(ctx.templateDirectory)
    getTableDescs(ctx.connection, ctx.schemaName).filter { tableDesc =>
      ctx.tableNameFilter(tableDesc.tableName)
    }.find(_.tableName == tableName).map { tableDesc =>
      foldGenerateFile(cfg, tableDesc)
    }.getOrElse(Success(Seq.empty[File]))
  }

  private def createTemplateConfiguration(templateDirectory: File) = {
    val cfg = new freemarker.template.Configuration(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
    cfg.setDirectoryForTemplateLoading(templateDirectory)
    cfg
  }

  private[generator] def generateMany(tableNames: Seq[String])(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    val cfg = createTemplateConfiguration(ctx.templateDirectory)
    getTableDescs(ctx.connection, ctx.schemaName).filter { tableDesc =>
      ctx.tableNameFilter(tableDesc.tableName)
    }.filter(tableDesc => tableNames.contains(tableDesc.tableName))
      .foldLeft(Try(Seq.empty[File])) { (result, tableDesc) =>
        for {
          r1 <- result
          r2 <- foldGenerateFile(cfg, tableDesc)
        } yield r1 ++ r2
      }
  }

  private[generator] def generateAll(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    val cfg = createTemplateConfiguration(ctx.templateDirectory)
    getTableDescs(ctx.connection, ctx.schemaName).filter { tableDesc =>
      ctx.tableNameFilter(tableDesc.tableName)
    }.foldLeft(Try(Seq.empty[File])) { (result, tableDesc) =>
      for {
        r1 <- result
        r2 <- foldGenerateFile(cfg, tableDesc)
      } yield r1 ++ r2
    }
  }

  def generateOneTask: Def.Initialize[InputTask[Seq[File]]] = Def.inputTask {
    val tableName = oneStringParser.parsed
    val logger = streams.value.log
    logger.info("driverClassName = " + (driverClassName in generator).value.toString)
    logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString)
    logger.info("jdbcUser = " + (jdbcUser in generator).value.toString)
    logger.info("schemaName = " + (schemaName in generator).value.getOrElse(""))
    logger.info("tableName = " + tableName)

    using(
      getJdbcConnection(
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        ),
        (driverClassName in generator).value,
        (jdbcUrl in generator).value,
        (jdbcUser in generator).value,
        (jdbcPassword in generator).value
      )
    ) { conn =>
      implicit val ctx = GeneratorContext(
        logger,
        conn,
        (classNameMapper in generator).value,
        (typeNameMapper in generator).value,
        (tableNameFilter in generator).value,
        (propertyNameMapper in generator).value,
        (schemaName in generator).value,
        (templateDirectory in generator).value,
        (templateNameMapper in generator).value,
        (outputDirectoryMapper in generator).value
      )
      generateOne(tableName)
    }.get
  }

  def generateManyTask: Def.Initialize[InputTask[Seq[File]]] = Def.inputTask {
    val tableNames = manyStringParser.parsed
    val logger = streams.value.log
    logger.info("driverClassName = " + (driverClassName in generator).value.toString)
    logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString)
    logger.info("jdbcUser = " + (jdbcUser in generator).value.toString)
    logger.info("schemaName = " + (schemaName in generator).value.getOrElse(""))
    logger.info("tableNames = " + tableNames.mkString(", "))

    using(
      getJdbcConnection(
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        ),
        (driverClassName in generator).value,
        (jdbcUrl in generator).value,
        (jdbcUser in generator).value,
        (jdbcPassword in generator).value
      )
    ) { connection =>
      implicit val ctx = GeneratorContext(
        logger,
        connection,
        (classNameMapper in generator).value,
        (typeNameMapper in generator).value,
        (tableNameFilter in generator).value,
        (propertyNameMapper in generator).value,
        (schemaName in generator).value,
        (templateDirectory in generator).value,
        (templateNameMapper in generator).value,
        (outputDirectoryMapper in generator).value
      )
      generateMany(tableNames)
    }.get
  }

  def generateAllTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val logger = streams.value.log
    logger.info("driverClassName = " + (driverClassName in generator).value.toString)
    logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString)
    logger.info("jdbcUser = " + (jdbcUser in generator).value.toString)
    logger.info("schemaName = " + (schemaName in generator).value.getOrElse(""))

    using(
      getJdbcConnection(
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        ),
        (driverClassName in generator).value,
        (jdbcUrl in generator).value,
        (jdbcUser in generator).value,
        (jdbcPassword in generator).value
      )
    ) { conn =>
      implicit val ctx = GeneratorContext(
        logger,
        conn,
        (classNameMapper in generator).value,
        (typeNameMapper in generator).value,
        (tableNameFilter in generator).value,
        (propertyNameMapper in generator).value,
        (schemaName in generator).value,
        (templateDirectory in generator).value,
        (templateNameMapper in generator).value,
        (outputDirectoryMapper in generator).value
      )
      generateAll
    }.get
  }

}

object SbtDaoGenerator extends SbtDaoGenerator
