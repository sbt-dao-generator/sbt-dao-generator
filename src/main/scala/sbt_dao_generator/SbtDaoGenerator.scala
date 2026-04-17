package sbt_dao_generator

import java.io.FileWriter
import java.sql.Connection
import java.sql.Driver
import org.scalafmt.interfaces.ScalafmtSession
import sbt_dao_generator.SbtDaoGeneratorKeys._
import sbt_dao_generator.model.ColumnDesc
import sbt_dao_generator.model.PrimaryKeyDesc
import sbt_dao_generator.model.TableDesc
// format: off
import sbt.{*, given}
// format: on
import sbt.Keys._
import sbt.complete.Parser
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Using
import scala.util.control.NonFatal

/**
  * Trait that provides the core logic of sbt-dao-generator.
  */
trait SbtDaoGenerator extends SbtDaoGeneratorCompat {

  import complete.DefaultParsers._

  private val oneStringParser: Parser[String] = token(Space ~> StringBasic, "table name")

  private val manyStringParser: Parser[Seq[String]] = token(Space ~> StringBasic, "table name").+

  private val createScalafmtInstance: Def.Initialize[Task[Option[ScalafmtSession]]] =
    Def.task {
      if (daoGeneratorScalafmt.value) {
        SbtDaoGeneratorPlugin.daoGeneratorScalafmtInstance.value.map(_.get())
      } else {
        None
      }
    }

  /**
    * Task for [[generateOne]].
    *
    * @return Task definition
    */
  def generateOneTask: Def.Initialize[InputTask[Seq[File]]] = Def.inputTask {
    val tableName = oneStringParser.parsed
    implicit val logger: Logger = streams.value.log
    logger.info("sbt-dao-generator: generateOne task")
    logger.info("driverClassName = " + daoGeneratorDriverClassName.value)
    logger.info("jdbcUrl = " + daoGeneratorJdbcUrl.value)
    logger.info("jdbcUser = " + daoGeneratorJdbcUser.value)
    logger.info("schemaName = " + daoGeneratorSchemaName.value.getOrElse(""))
    logger.info("tableName = " + tableName)

    val propertyTypeNameMapperValue = daoGeneratorPropertyTypeNameMapper.value
    val advancedPropertyTypeNameMapperValue = daoGeneratorAdvancedPropertyTypeNameMapper.value

    val classLoader =
      if (daoGeneratorEnableManagedClassPath.value)
        ClasspathUtilities.toLoader(
          compileManagedClasspathValue.value,
          ClasspathUtilities.xsbtiLoader
        )
      else
        ClasspathUtilities.xsbtiLoader

    scala.util.Using.resource(
      getJdbcConnection(
        classLoader,
        daoGeneratorDriverClassName.value,
        daoGeneratorJdbcUrl.value,
        daoGeneratorJdbcUser.value,
        daoGeneratorJdbcPassword.value
      )
    ) { conn =>
      implicit val ctx: GeneratorContext = GeneratorContext(
        logger,
        conn,
        daoGeneratorClassNameMapper.value,
        if (propertyTypeNameMapperValue == DefaultPropertyTypeNameMapper) advancedPropertyTypeNameMapperValue
        else (s, _, _) => propertyTypeNameMapperValue(s),
        daoGeneratorTableNameFilter.value,
        daoGeneratorPropertyNameMapper.value,
        daoGeneratorSchemaName.value,
        daoGeneratorTemplateDirectory.value,
        daoGeneratorTemplateNameMapper.value,
        daoGeneratorOutputDirectoryMapper.value,
        createScalafmtInstance.value
      )
      generateOne(tableName)
    }
  }

  /**
    * Generates files for the specified table name.
    *
    * @param tableName Table name
    * @param ctx       [[GeneratorContext]]
    * @return Generated Seq[File]
    */
  private[sbt_dao_generator] def generateOne(tableName: String)(implicit ctx: GeneratorContext): Seq[File] = {
    implicit val logger: Logger = ctx.logger
    logger.debug(s"generateOne: start")
    val cfg = createTemplateConfiguration(ctx.templateDirectory)
    val tableDescs = getTableDescs(ctx.connection, ctx.schemaName)
    tableDescs
      .filter { tableDesc =>
        ctx.tableNameFilter(tableDesc.tableName)
      }
      .find(_.tableName == tableName)
      .map { tableDesc =>
        generateFiles(cfg, tableDesc)
      }
      .toSeq
      .flatten
  }

  /**
    * Task for [[generateMany]].
    *
    * @return Task definition
    */
  def generateManyTask: Def.Initialize[InputTask[Seq[File]]] = Def.inputTask {
    val tableNames = manyStringParser.parsed
    implicit val logger: Logger = streams.value.log
    logger.info("sbt-dao-generator: generateMany task")
    logger.info("driverClassName = " + daoGeneratorDriverClassName.value)
    logger.info("jdbcUrl = " + daoGeneratorJdbcUrl.value)
    logger.info("jdbcUser = " + daoGeneratorJdbcUser.value)
    logger.info("schemaName = " + daoGeneratorSchemaName.value.getOrElse(""))
    logger.info("tableNames = " + tableNames.mkString(", "))

    val propertyTypeNameMapperValue = daoGeneratorPropertyTypeNameMapper.value
    val advancedPropertyTypeNameMapperValue = daoGeneratorAdvancedPropertyTypeNameMapper.value

    val classLoader =
      if (daoGeneratorEnableManagedClassPath.value)
        ClasspathUtilities.toLoader(
          compileManagedClasspathValue.value,
          ClasspathUtilities.xsbtiLoader
        )
      else
        ClasspathUtilities.xsbtiLoader

    Using
      .resource(
        getJdbcConnection(
          classLoader,
          daoGeneratorDriverClassName.value,
          daoGeneratorJdbcUrl.value,
          daoGeneratorJdbcUser.value,
          daoGeneratorJdbcPassword.value
        )
      ) { connection =>
        implicit val ctx: GeneratorContext = GeneratorContext(
          logger,
          connection,
          daoGeneratorClassNameMapper.value,
          if (propertyTypeNameMapperValue == DefaultPropertyTypeNameMapper) advancedPropertyTypeNameMapperValue
          else (s, _, _) => propertyTypeNameMapperValue(s),
          daoGeneratorTableNameFilter.value,
          daoGeneratorPropertyNameMapper.value,
          daoGeneratorSchemaName.value,
          daoGeneratorTemplateDirectory.value,
          daoGeneratorTemplateNameMapper.value,
          daoGeneratorOutputDirectoryMapper.value,
          createScalafmtInstance.value
        )
        generateMany(tableNames)
      }
      .get()
  }

  /**
    * Obtains a JDBC connection.
    *
    * @param classLoader     Class loader
    * @param driverClassName Driver class name
    * @param jdbcUrl         JDBC URL
    * @param jdbcUser        JDBC user
    * @param jdbcPassword    JDBC user password
    * @return JDBC connection
    */
  private[sbt_dao_generator] def getJdbcConnection(
      classLoader: ClassLoader,
      driverClassName: String,
      jdbcUrl: String,
      jdbcUser: String,
      jdbcPassword: String
  )(implicit logger: Logger): Connection = {
    logger.debug(s"getJdbcConnection($classLoader, $driverClassName, $jdbcUrl, $jdbcUser, $jdbcPassword): start")
    var connection: Connection = null
    try {
      val driver = classLoader.loadClass(driverClassName).getConstructor().newInstance().asInstanceOf[Driver]
      val info = new java.util.Properties()
      info.put("user", jdbcUser)
      info.put("password", jdbcPassword)
      connection = driver.connect(jdbcUrl, info)
    } finally {
      logger.debug(s"getJdbcConnection: finished = $connection")
    }
    connection
  }

  /**
    * Generates files for the specified table names.
    *
    * @param tableNames Table names
    * @param ctx        [[GeneratorContext]]
    * @return Generated Seq[File]
    */
  private[sbt_dao_generator] def generateMany(
      tableNames: Seq[String]
  )(implicit ctx: GeneratorContext): Seq[File] = {
    implicit val logger: Logger = ctx.logger
    logger.debug(s"generateMany($tableNames): start")
    val cfg = createTemplateConfiguration(ctx.templateDirectory)
    val tableDescs = getTableDescs(ctx.connection, ctx.schemaName)
    tableDescs
      .filter { tableDesc =>
        ctx.tableNameFilter(tableDesc.tableName)
      }
      .filter { tableDesc =>
        tableNames.contains(tableDesc.tableName)
      }
      .flatMap(tableDesc => generateFiles(cfg, tableDesc))
  }

  /**
    * Retrieves table descriptions.
    *
    * @param conn       JDBC connection
    * @param schemaName Schema name
    * @return Table descriptions
    */
  private[sbt_dao_generator] def getTableDescs(conn: Connection, schemaName: Option[String])(implicit
      logger: Logger
  ): Seq[TableDesc] = {
    logger.debug(s"getTableDescs($conn, $schemaName): start")
    getTables(conn, schemaName).map { tableName =>
      val primaryKeyDescs = getPrimaryKeyDescs(conn, schemaName, tableName)
      val columnDescs = getColumnDescs(conn, schemaName, tableName)
      TableDesc(tableName, primaryKeyDescs, columnDescs)
    }
  }

  /**
    * Retrieves table names.
    *
    * @param conn       JDBC connection
    * @param schemaName Schema name
    * @return Table names
    */
  private[sbt_dao_generator] def getTables(conn: Connection, schemaName: Option[String])(implicit
      logger: Logger
  ): Seq[String] = {
    logger.debug(s"getColumnDescs($conn, $schemaName): start")
    val dbMeta = conn.getMetaData
    val types = Array("TABLE")
    Using.resource(dbMeta.getTables(null, schemaName.orNull, "%", types)) { rs =>
      val lb = ListBuffer[String]()
      while (rs.next()) {
        if (rs.getString("TABLE_TYPE") == "TABLE") {
          val tableName = rs.getString("TABLE_NAME")
          logger.debug(s"table name = $tableName")
          lb += tableName
        }
      }
      lb.result()
    }
  }

  /**
    * Retrieves column descriptions.
    *
    * @param conn       JDBC connection
    * @param schemaName Schema name
    * @param tableName  Table name
    * @return Column descriptions
    */
  private[sbt_dao_generator] def getColumnDescs(conn: Connection, schemaName: Option[String], tableName: String)(
      implicit logger: Logger
  ): Seq[ColumnDesc] = {
    logger.debug(s"getColumnDescs($conn, $schemaName, $tableName): start")
    val dbMeta = conn.getMetaData
    Using.resource(dbMeta.getColumns(null, schemaName.orNull, tableName, "%")) { rs =>
      val lb = ListBuffer[ColumnDesc]()

      def getOrFalse(f: => Boolean): Boolean = {
        try {
          f
        } catch {
          case NonFatal(err) =>
            logger.debug(err.toString)
            false
        }
      }

      while (rs.next()) {
        lb += ColumnDesc(
          rs.getString("COLUMN_NAME"),
          rs.getString("TYPE_NAME"),
          rs.getString("IS_NULLABLE") == "YES",
          getOrFalse(
            // Oracle9i may throw an exception here.
            rs.getString("IS_AUTOINCREMENT") == "YES"
          ),
          Option(rs.getString("COLUMN_SIZE")).map(_.toInt),
          Option(rs.getString("REMARKS")),
          getOrFalse(
            rs.getString("IS_GENERATEDCOLUMN") == "YES"
          )
        )
      }
      lb.result()
    }
  }

  /**
    * Retrieves primary key descriptions.
    *
    * @param conn       JDBC connection
    * @param schemaName Schema name
    * @param tableName  Table name
    * @return Primary key descriptions
    */
  private[sbt_dao_generator] def getPrimaryKeyDescs(conn: Connection, schemaName: Option[String], tableName: String)(
      implicit logger: Logger
  ): Seq[PrimaryKeyDesc] = {
    logger.debug(s"getPrimaryKeyDescs($conn, $schemaName, $tableName): start")
    val dbMeta = conn.getMetaData
    Using.resource(dbMeta.getPrimaryKeys(null, schemaName.orNull, tableName)) { rs =>
      val lb = ListBuffer[PrimaryKeyDesc]()
      while (rs.next()) {
        lb += PrimaryKeyDesc(rs.getString("COLUMN_NAME"))
      }
      lb.result()
    }
  }

  /**
    * Generates multiple files from a template.
    *
    * @param cfg       Template configuration
    * @param tableDesc [[TableDesc]]
    * @param ctx       [[GeneratorContext]]
    */
  private[sbt_dao_generator] def generateFiles(cfg: freemarker.template.Configuration, tableDesc: TableDesc)(implicit
      ctx: GeneratorContext
  ): Seq[File] = {
    implicit val logger: Logger = ctx.logger
    logger.debug(s"generateFiles($cfg, $tableDesc): start")
    val result = ctx
      .classNameMapper(tableDesc.tableName)
      .map { className =>
        val outputTargetDirectory = ctx.outputDirectoryMapper(className)
        generateFile(cfg, tableDesc, className, outputTargetDirectory)
      }
    logger.debug(s"generateFiles: finished = $result")
    result
  }

  /**
    * Generates a file from a template.
    *
    * @param cfg             Template configuration
    * @param tableDesc       [[TableDesc]]
    * @param className       Class name
    * @param outputDirectory Output directory
    * @param ctx             [[GeneratorContext]]
    */
  private[sbt_dao_generator] def generateFile(
      cfg: freemarker.template.Configuration,
      tableDesc: TableDesc,
      className: String,
      outputDirectory: File
  )(implicit ctx: GeneratorContext): File = {
    implicit val logger: Logger = ctx.logger
    logger.debug(s"generateFile($cfg, $tableDesc, $outputDirectory): start")
    val templateName = ctx.templateNameMapper(className)
    val template = cfg.getTemplate(templateName)
    val file = createFile(outputDirectory, className)
    ctx.logger.info(s"tableName = ${tableDesc.tableName}, templateName = $templateName, generate file = $file")

    if (!outputDirectory.exists())
      IO.createDirectory(outputDirectory)

    Using.resource(new FileWriter(file)) { writer =>
      val primaryKeys = createPrimaryKeysContext(ctx.propertyTypeNameMapper, ctx.propertyNameMapper, tableDesc)
      val columns = createColumnsContext(
        ctx.propertyTypeNameMapper,
        ctx.propertyNameMapper,
        tableDesc
      )
      val context = createContext(primaryKeys, columns, tableDesc.tableName, className)
      template.process(context, writer)
      writer.flush()
      ctx.format(file)
      file
    }
  }

  /**
    * Creates context for primary keys.
    *
    * @param propertyTypeNameMapper Type mapper
    * @param propertyNameMapper     Property mapper
    * @param tableDesc              Table description
    * @return Context
    */
  private[sbt_dao_generator] def createPrimaryKeysContext(
      propertyTypeNameMapper: (String, TableDesc, ColumnDesc) => String,
      propertyNameMapper: String => String,
      tableDesc: TableDesc
  )(implicit logger: Logger): Seq[Map[String, Any]] = {
    logger.debug(s"createPrimaryKeysContext($propertyTypeNameMapper, $propertyNameMapper, $tableDesc): start")
    val primaryKeys = tableDesc.primaryDescs.map { key =>
      val column = tableDesc.columnDescs.find(_.columnName == key.columnName).get
      val propertyName = propertyNameMapper(column.columnName)
      val propertyTypeName = propertyTypeNameMapper(column.typeName, tableDesc, column)
      Map[String, Any](
        "columnName" -> key.columnName,
        "columnTypeName" -> column.typeName,
        "propertyName" -> propertyName,
        "propertyTypeName" -> propertyTypeName,
        "camelizedColumnName" -> StringUtil.camelize(key.columnName),
        "capitalizedColumnName" -> StringUtil.capitalize(key.columnName),
        "capitalizedPropertyName" -> StringUtil.capitalize(propertyName),
        "decamelizedPropertyName" -> StringUtil.decamelize(propertyName),
        "decapitalizedPropertyName" -> StringUtil.decapitalize(propertyName),
        "autoIncrement" -> column.autoIncrement,
        "nullable" -> column.nullable
      )
    }
    logger.debug(s"createPrimaryKeysContext: finished = $primaryKeys")
    primaryKeys
  }

  /**
    * Creates context for columns.
    *
    * @param propertyTypeNameMapper Type mapper
    * @param propertyNameMapper     Property mapper
    * @param tableDesc              Table description
    * @return Context
    */
  private[sbt_dao_generator] def createColumnsContext(
      advancedPropertyTypeNameMapper: (String, TableDesc, ColumnDesc) => String,
      propertyNameMapper: String => String,
      tableDesc: TableDesc
  )(implicit logger: Logger): Seq[Map[String, Any]] = {
    logger.debug(s"createColumnsContext($advancedPropertyTypeNameMapper, $propertyNameMapper, $tableDesc): start")
    val columns = tableDesc.columnDescs
      .filterNot { e =>
        tableDesc.primaryDescs.map(_.columnName).contains(e.columnName)
      }
      .map { column =>
        val propertyName = propertyNameMapper(column.columnName)
        val propertyTypeName = advancedPropertyTypeNameMapper(column.typeName, tableDesc, column)
        Map[String, Any](
          "columnName" -> column.columnName,
          "columnTypeName" -> column.typeName,
          "propertyName" -> propertyName,
          "propertyTypeName" -> propertyTypeName,
          "camelizedColumnName" -> StringUtil.camelize(column.columnName),
          "capitalizedColumnName" -> StringUtil.capitalize(column.columnName),
          "capitalizedPropertyName" -> StringUtil.capitalize(propertyName),
          "decamelizedPropertyName" -> StringUtil.decamelize(propertyName),
          "decapitalizedPropertyName" -> StringUtil.decapitalize(propertyName),
          "nullable" -> column.nullable,
          "generatedColumn" -> column.generatedColumn
        )
      }
    logger.debug(s"createColumnsContext: finished = $columns")
    columns
  }

  /**
    * Creates context.
    *
    * @param logger      Logger
    * @param primaryKeys Primary keys
    * @param columns     Columns
    * @param className   Class name
    * @return Context
    */
  private[sbt_dao_generator] def createContext(
      primaryKeys: Seq[Map[String, Any]],
      columns: Seq[Map[String, Any]],
      tableName: String,
      className: String
  )(implicit logger: Logger): java.util.Map[String, Any] = {
    logger.debug(s"createContext($primaryKeys, $columns, $className): start")
    val context = Map[String, Any](
      "className" -> className,
      "tableName" -> tableName,
      "decapitalizedClassName" -> StringUtil.decapitalize(className),
      "primaryKeys" -> primaryKeys.map(_.asJava).asJava,
      "columns" -> columns.map(_.asJava).asJava,
      "allColumns" -> (primaryKeys ++ columns).map(_.asJava).asJava
    ).asJava
    logger.debug(s"createContext: finished = $context")
    context
  }

  /**
    * Creates the output file.
    *
    * @param outputDirectory Output directory
    * @param className       Class name
    * @return [[File]]
    */
  private[sbt_dao_generator] def createFile(outputDirectory: File, className: String)(implicit logger: Logger): File = {
    logger.debug(s"createFile($outputDirectory, $className): start")
    val file = outputDirectory / (className + ".scala")
    logger.debug(s"createFile: finished = $file")
    file
  }

  /**
    * Creates template configuration.
    *
    * @param templateDirectory Template directory
    * @param logger            Logger
    * @return Template configuration
    */
  private[sbt_dao_generator] def createTemplateConfiguration(
      templateDirectory: File
  )(implicit logger: Logger): freemarker.template.Configuration = {
    logger.debug(s"createTemplateConfiguration($templateDirectory): start")
    var cfg: freemarker.template.Configuration = null
    try {
      cfg = new freemarker.template.Configuration(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
      cfg.setDirectoryForTemplateLoading(templateDirectory)
    } finally {
      logger.debug(s"createTemplateConfiguration: finished = $cfg")
    }
    cfg
  }

  /**
    * Task for [[generateAll]].
    *
    * @return Task definition
    */
  def generateAllTask: Def.Initialize[Task[Seq[File]]] = Def.taskDyn {
    implicit val logger: Logger = streams.value.log
    logger.info("sbt-dao-generator: generateAll task")
    logger.info("driverClassName = " + daoGeneratorDriverClassName.value)
    logger.info("jdbcUrl = " + daoGeneratorJdbcUrl.value)
    logger.info("jdbcUser = " + daoGeneratorJdbcUser.value)
    logger.info("schemaName = " + daoGeneratorSchemaName.value.getOrElse(""))
    val enableManagedClassPathValue = daoGeneratorEnableManagedClassPath.value
    val managedClasspathData = compileManagedClasspathValue.value
    val driverClassNameValue = daoGeneratorDriverClassName.value
    val jdbcUrlValue = daoGeneratorJdbcUrl.value
    val jdbcUserValue = daoGeneratorJdbcUser.value
    val jdbcPasswordValue = daoGeneratorJdbcPassword.value
    val classNameMapperValue = daoGeneratorClassNameMapper.value
    val propertyTypeNameMapperValue = daoGeneratorPropertyTypeNameMapper.value
    val advancedPropertyTypeNameMapperValue = daoGeneratorAdvancedPropertyTypeNameMapper.value
    val tableNameFilterValue = daoGeneratorTableNameFilter.value
    val propertyNameMapperValue = daoGeneratorPropertyNameMapper.value
    val schemaNameValue = daoGeneratorSchemaName.value
    val templateDirectoryValue = daoGeneratorTemplateDirectory.value
    val templateNameMapperValue = daoGeneratorTemplateNameMapper.value
    val outputDirectoryMapperValue = daoGeneratorOutputDirectoryMapper.value
    val scalafmt = createScalafmtInstance.value

    Def.task {
      val classLoader =
        if (enableManagedClassPathValue)
          ClasspathUtilities.toLoader(
            managedClasspathData,
            ClasspathUtilities.xsbtiLoader
          )
        else
          ClasspathUtilities.xsbtiLoader

      Using
        .resource(
          getJdbcConnection(
            classLoader,
            driverClassNameValue,
            jdbcUrlValue,
            jdbcUserValue,
            jdbcPasswordValue
          )
        ) { conn =>
          implicit val ctx: GeneratorContext = GeneratorContext(
            logger,
            conn,
            classNameMapperValue,
            if (propertyTypeNameMapperValue == DefaultPropertyTypeNameMapper) advancedPropertyTypeNameMapperValue
            else (s, _, _) => propertyTypeNameMapperValue(s),
            tableNameFilterValue,
            propertyNameMapperValue,
            schemaNameValue,
            templateDirectoryValue,
            templateNameMapperValue,
            outputDirectoryMapperValue,
            scalafmt
          )
          generateAll
        }
        .get()
    }
  }

  /**
    * Generates files for all tables.
    *
    * @param ctx [[GeneratorContext]]
    * @return Generated Seq[File]
    */
  private[sbt_dao_generator] def generateAll(implicit ctx: GeneratorContext): Seq[File] = {
    implicit val logger: Logger = ctx.logger
    logger.debug(s"generateAll: start")
    val cfg = createTemplateConfiguration(ctx.templateDirectory)
    val tableDescs = getTableDescs(ctx.connection, ctx.schemaName)
    tableDescs
      .filter { tableDesc =>
        ctx.tableNameFilter(tableDesc.tableName)
      }
      .flatMap(tableDesc => generateFiles(cfg, tableDesc))
  }

  case class GeneratorContext(
      logger: Logger,
      connection: Connection,
      classNameMapper: String => Seq[String],
      propertyTypeNameMapper: (String, TableDesc, ColumnDesc) => String,
      tableNameFilter: String => Boolean,
      propertyNameMapper: String => String,
      schemaName: Option[String],
      templateDirectory: File,
      templateNameMapper: String => String,
      outputDirectoryMapper: String => File,
      scalafmt: Option[ScalafmtSession]
  ) {
    def format(file: File): Unit = {
      scalafmt.foreach { fmt =>
        IO.write(
          file,
          fmt.format(file.toPath, IO.read(file))
        )
      }
    }
  }

}

object SbtDaoGenerator extends SbtDaoGenerator

object DefaultPropertyTypeNameMapper extends (String => String) {
  override def apply(v1: String): String = v1
}
