package jp.co.septeni_original.sbt.dao.generator

import java.io._
import java.sql.{ Connection, Driver }

import jp.co.septeni_original.sbt.dao.generator.SbtDaoGeneratorKeys._
import jp.co.septeni_original.sbt.dao.generator.model.{ ColumnDesc, PrimaryKeyDesc, TableDesc }
import jp.co.septeni_original.sbt.dao.generator.util.Loan._
import org.seasar.util.lang.StringUtil
import sbt.Keys._
import sbt.classpath.ClasspathUtilities
import sbt.complete.Parser
import sbt.{ File, _ }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{ Success, Try }

/**
  * sbt-dao-generatorのロジックを提供するトレイト。
  */
trait SbtDaoGenerator {

  import complete.DefaultParsers._

  private val oneStringParser: Parser[String] = token(Space ~> StringBasic, "table name")

  private val manyStringParser: Parser[Seq[String]] = token(Space ~> StringBasic, "table name") +

  /**
    * [[generateOne]]のためのタスク。
    *
    * @return タスク定義
    */
  def generateOneTask: Def.Initialize[InputTask[Seq[File]]] = Def.inputTask {
    val tableName       = oneStringParser.parsed
    implicit val logger = streams.value.log
    logger.info("sbt-dao-generator: generateOne task")
    logger.info("driverClassName = " + (driverClassName in generator).value.toString)
    logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString)
    logger.info("jdbcUser = " + (jdbcUser in generator).value.toString)
    logger.info("schemaName = " + (schemaName in generator).value.getOrElse(""))
    logger.info("tableName = " + tableName)

    val classLoader =
      if ((enableManagedClassPath in generator).value)
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        )
      else
        ClasspathUtilities.xsbtiLoader

    using(
      getJdbcConnection(
        classLoader,
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
        (propertyTypeNameMapper in generator).value,
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

  /**
    * テーブル名を指定してファイルを生成する。
    *
    * @param tableName テーブル名
    * @param ctx       [[GeneratorContext]]
    * @return 生成されたSeq[File]
    */
  private[generator] def generateOne(tableName: String)(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateOne: start")
    val result = for {
      cfg        <- createTemplateConfiguration(ctx.templateDirectory)
      tableDescs <- getTableDescs(ctx.connection, ctx.schemaName)
      files <- tableDescs
        .filter { tableDesc =>
          ctx.tableNameFilter(tableDesc.tableName)
        }
        .find(_.tableName == tableName)
        .map { tableDesc =>
          generateFiles(cfg, tableDesc)
        }
        .get
    } yield files
    logger.debug(s"generateOne: finished = $result")
    result
  }

  /**
    * [[generateMany]]のためのタスク。
    *
    * @return タスク定義
    */
  def generateManyTask: Def.Initialize[InputTask[Seq[File]]] = Def.inputTask {
    val tableNames      = manyStringParser.parsed
    implicit val logger = streams.value.log
    logger.info("sbt-dao-generator: generateMany task")
    logger.info("driverClassName = " + (driverClassName in generator).value.toString)
    logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString)
    logger.info("jdbcUser = " + (jdbcUser in generator).value.toString)
    logger.info("schemaName = " + (schemaName in generator).value.getOrElse(""))
    logger.info("tableNames = " + tableNames.mkString(", "))

    val classLoader =
      if ((enableManagedClassPath in generator).value)
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        )
      else
        ClasspathUtilities.xsbtiLoader

    using(
      getJdbcConnection(
        classLoader,
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
        (propertyTypeNameMapper in generator).value,
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

  /**
    * JDBCコネクションを取得する。
    *
    * @param classLoader     クラスローダ
    * @param driverClassName ドライバークラス名
    * @param jdbcUrl         JDBC URL
    * @param jdbcUser        JDBCユーザ
    * @param jdbcPassword    JDBCユーザのパスワード
    * @return JDBCコネクション
    */
  private[generator] def getJdbcConnection(classLoader: ClassLoader,
                                           driverClassName: String,
                                           jdbcUrl: String,
                                           jdbcUser: String,
                                           jdbcPassword: String)(implicit logger: Logger): Try[Connection] = Try {
    logger.debug(s"getJdbcConnection($classLoader, $driverClassName, $jdbcUrl, $jdbcUser, $jdbcPassword): start")
    var connection: Connection = null
    try {
      val driver = classLoader.loadClass(driverClassName).newInstance().asInstanceOf[Driver]
      val info   = new java.util.Properties()
      info.put("user", jdbcUser)
      info.put("password", jdbcPassword)
      connection = driver.connect(jdbcUrl, info)
    } finally {
      logger.debug(s"getJdbcConnection: finished = $connection")
    }
    connection
  }

  /**
    * 複数のテーブル名を指定してファイルを生成する。
    *
    * @param tableNames 複数のテーブル名
    * @param ctx        [[GeneratorContext]]
    * @return 生成されたSeq[File]
    */
  private[generator] def generateMany(tableNames: Seq[String])(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateMany($tableNames): start")
    val result = for {
      cfg        <- createTemplateConfiguration(ctx.templateDirectory)
      tableDescs <- getTableDescs(ctx.connection, ctx.schemaName)
      files <- tableDescs
        .filter { tableDesc =>
          ctx.tableNameFilter(tableDesc.tableName)
        }
        .filter { tableDesc =>
          tableNames.contains(tableDesc.tableName)
        }
        .foldLeft(Try(Seq.empty[File])) { (result, tableDesc) =>
          for {
            r1 <- result
            r2 <- generateFiles(cfg, tableDesc)
          } yield r1 ++ r2
        }
    } yield files
    logger.debug(s"generateMany: finished = $result")
    result
  }

  /**
    * 複数のテーブルディスクリプションを取得する。
    *
    * @param conn       JDBCコネクション
    * @param schemaName スキーマ名
    * @return テーブルディスクリプション
    */
  private[generator] def getTableDescs(conn: Connection,
                                       schemaName: Option[String])(implicit logger: Logger): Try[Seq[TableDesc]] = {
    logger.debug(s"getTableDescs($conn, $schemaName): start")
    val result = getTables(conn, schemaName).flatMap { tables =>
      tables.foldLeft(Try(Seq.empty[TableDesc])) { (result, tableName) =>
        for {
          r               <- result
          primaryKeyDescs <- getPrimaryKeyDescs(conn, schemaName, tableName)
          columnDescs     <- getColumnDescs(conn, schemaName, tableName)
        } yield r :+ TableDesc(tableName, primaryKeyDescs, columnDescs)
      }
    }
    logger.debug(s"getTableDescs: finished = $result")
    result
  }

  /**
    * 複数のテーブル名を取得する。
    *
    * @param conn       JDBCコネクション
    * @param schemaName スキーマ名
    * @return テーブル名
    */
  private[generator] def getTables(conn: Connection,
                                   schemaName: Option[String])(implicit logger: Logger): Try[Seq[String]] = {
    logger.debug(s"getColumnDescs($conn, $schemaName): start")
    val result = Try(conn.getMetaData).flatMap { dbMeta =>
      val types = Array("TABLE")
      using(dbMeta.getTables(null, schemaName.orNull, "%", types)) { rs =>
        val lb = ListBuffer[String]()
        while (rs.next()) {
          if (rs.getString("TABLE_TYPE") == "TABLE") {
            val tableName = rs.getString("TABLE_NAME")
            logger.debug(s"table name = $tableName")
            lb += tableName
          }
        }
        Success(lb.result())
      }
    }
    logger.debug(s"getColumnDescs: finished = $result")
    result
  }

  /**
    * 複数のカラムディスクリプションを取得する。
    *
    * @param conn       JDBCコネクション
    * @param schemaName スキーマ名
    * @param tableName  テーブル名
    * @return カラムディスクリプション
    */
  private[generator] def getColumnDescs(conn: Connection, schemaName: Option[String], tableName: String)(
      implicit logger: Logger
  ): Try[Seq[ColumnDesc]] = {
    logger.debug(s"getColumnDescs($conn, $schemaName, $tableName): start")
    val result = Try(conn.getMetaData).flatMap { dbMeta =>
      using(dbMeta.getColumns(null, schemaName.orNull, tableName, "%")) { rs =>
        val lb = ListBuffer[ColumnDesc]()
        while (rs.next()) {
          lb += ColumnDesc(rs.getString("COLUMN_NAME"),
                           rs.getString("TYPE_NAME"),
                           rs.getString("IS_NULLABLE") == "YES",
                           Option(rs.getString("COLUMN_SIZE")).map(_.toInt))
        }
        Success(lb.result())
      }
    }
    logger.debug(s"getColumnDescs: finished = $result")
    result
  }

  /**
    * 複数のプライマリーキーディスクリプションを取得する。
    *
    * @param conn       JDBCコネクション
    * @param schemaName スキーマ名
    * @param tableName  テーブル名
    * @return プライマリーキーディスクリプション
    */
  private[generator] def getPrimaryKeyDescs(conn: Connection, schemaName: Option[String], tableName: String)(
      implicit logger: Logger
  ): Try[Seq[PrimaryKeyDesc]] = {
    logger.debug(s"getPrimaryKeyDescs($conn, $schemaName, $tableName): start")
    val result = Try(conn.getMetaData).flatMap { dbMeta =>
      using(dbMeta.getPrimaryKeys(null, schemaName.orNull, tableName)) { rs =>
        val lb = ListBuffer[PrimaryKeyDesc]()
        while (rs.next()) {
          lb += PrimaryKeyDesc(
            rs.getString("COLUMN_NAME"),
            try {
              rs.getBoolean("IS_AUTOINCREMENT")
            } catch {
              case ex: Exception => false
            }
          )
        }
        Success(lb.result())
      }
    }
    logger.debug(s"getPrimaryKeyDescs: finished = $result")
    result
  }

  /**
    * テンプレートから複数のファイルを生成する。
    *
    * @param cfg       テンプレートコンフィグレーション
    * @param tableDesc [[TableDesc]]
    * @param ctx       [[GeneratorContext]]
    * @return TryにラップされたSeq[File]
    */
  private[generator] def generateFiles(cfg: freemarker.template.Configuration,
                                       tableDesc: TableDesc)(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateFiles($cfg, $tableDesc): start")
    val result = ctx
      .classNameMapper(tableDesc.tableName)
      .foldLeft(Try(Seq.empty[File])) { (result, className) =>
        val outputTargetDirectory = ctx.outputDirectoryMapper(className)
        for {
          r    <- result
          file <- generateFile(cfg, tableDesc, className, outputTargetDirectory)
        } yield {
          r :+ file
        }
      }
    logger.debug(s"generateFiles: finished = $result")
    result
  }

  /**
    * テンプレートからファイルを生成する。
    *
    * @param cfg             テンプレートコンフィグレーション
    * @param tableDesc       [[TableDesc]]
    * @param className       クラス名
    * @param outputDirectory 出力先ディレクトリ
    * @param ctx             [[GeneratorContext]]
    * @return TryにラップされたFile
    */
  private[generator] def generateFile(cfg: freemarker.template.Configuration,
                                      tableDesc: TableDesc,
                                      className: String,
                                      outputDirectory: File)(implicit ctx: GeneratorContext): Try[File] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateFile($cfg, $tableDesc, $outputDirectory): start")
    val templateName = ctx.templateNameMapper(className)
    val template     = cfg.getTemplate(templateName)
    val file         = createFile(outputDirectory, className)
    ctx.logger.info(s"tableName = ${tableDesc.tableName}, templateName = $templateName, generate file = $file")

    if (!outputDirectory.exists())
      IO.createDirectory(outputDirectory)

    val result = using(new FileWriter(file)) { writer =>
      val primaryKeys = createPrimaryKeysContext(ctx.typeNameMapper, ctx.propertyNameMapper, tableDesc)
      val columns     = createColumnsContext(ctx.typeNameMapper, ctx.propertyNameMapper, tableDesc)
      val context     = createContext(primaryKeys, columns, tableDesc.tableName, className)
      template.process(context, writer)
      writer.flush()
      Success(file)
    }
    logger.debug(s"generateFile: finished = $result")
    result
  }

  /**
    * プライマリーキーのためのコンテキストを生成する。
    *
    * @param propertyTypeNameMapper     タイプマッパー
    * @param propertyNameMapper プロパティマッパー
    * @param tableDesc          テーブルディスクリプション
    * @return コンテキスト
    */
  private[generator] def createPrimaryKeysContext(
      propertyTypeNameMapper: String => String,
      propertyNameMapper: String => String,
      tableDesc: TableDesc
  )(implicit logger: Logger): Seq[Map[String, Any]] = {
    logger.debug(s"createPrimaryKeysContext($propertyTypeNameMapper, $propertyNameMapper, $tableDesc): start")
    val primaryKeys = tableDesc.primaryDescs.map { key =>
      val column           = tableDesc.columnDescs.find(_.columnName == key.columnName).get
      val propertyName     = propertyNameMapper(column.columnName)
      val propertyTypeName = propertyTypeNameMapper(column.typeName)
      Map[String, Any](
        "name"                      -> key.columnName, // deprecated
        "columnName"                -> key.columnName,
        "columnType"                -> column.typeName, // deprecated
        "columnTypeName"            -> column.typeName,
        "propertyName"              -> propertyName,
        "propertyType"              -> propertyTypeName, // deprecated
        "propertyTypeName"          -> propertyTypeName,
        "camelizeName"              -> StringUtil.camelize(key.columnName), // deprecated
        "camelizedColumnName"       -> StringUtil.camelize(key.columnName),
        "capitalizedColumnName"     -> StringUtil.capitalize(key.columnName),
        "capitalizedPropertyName"   -> StringUtil.capitalize(propertyName),
        "decamelizedPropertyName"   -> StringUtil.decamelize(propertyName),
        "decapitalizedPropertyName" -> StringUtil.decapitalize(propertyName),
        "autoIncrement"             -> key.autoIncrement,
        "nullable"                  -> column.nullable
      )
    }
    logger.debug(s"createPrimaryKeysContext: finished = $primaryKeys")
    primaryKeys
  }

  /**
    * カラムのためのコンテキストを生成する。
    *
    * @param propertyTypeNameMapper     タイプマッパー
    * @param propertyNameMapper プロパティマッパー
    * @param tableDesc          テーブルディスクリプション
    * @return コンテキスト
    */
  private[generator] def createColumnsContext(propertyTypeNameMapper: String => String,
                                              propertyNameMapper: String => String,
                                              tableDesc: TableDesc)(implicit logger: Logger): Seq[Map[String, Any]] = {
    logger.debug(s"createColumnsContext($propertyTypeNameMapper, $propertyNameMapper, $tableDesc): start")
    val columns = tableDesc.columnDescs
      .filterNot { e =>
        tableDesc.primaryDescs.map(_.columnName).contains(e.columnName)
      }
      .map { column =>
        val propertyName     = propertyNameMapper(column.columnName)
        val propertyTypeName = propertyTypeNameMapper(column.typeName)
        Map[String, Any](
          "name"                      -> column.columnName, // deprecated
          "columnName"                -> column.columnName,
          "columnType"                -> column.typeName, // deprecated
          "columnTypeName"            -> column.typeName,
          "propertyName"              -> propertyName,
          "propertyType"              -> propertyTypeName, // deprecated
          "propertyTypeName"          -> propertyTypeName,
          "camelizedColumnName"       -> StringUtil.camelize(column.columnName),
          "capitalizedColumnName"     -> StringUtil.capitalize(column.columnName),
          "capitalizedPropertyName"   -> StringUtil.capitalize(propertyName),
          "decamelizedPropertyName"   -> StringUtil.decamelize(propertyName),
          "decapitalizedPropertyName" -> StringUtil.decapitalize(propertyName),
          "capitalizedPropertyName"   -> StringUtil.capitalize(propertyName), // deprecated
          "nullable"                  -> column.nullable
        )
      }
    logger.debug(s"createColumnsContext: finished = $columns")
    columns
  }

  /**
    * コンテキストを生成する。
    *
    * @param logger      ロガー
    * @param primaryKeys プライマリーキー
    * @param columns     カラム
    * @param className   クラス名
    * @return コンテキスト
    */
  private[generator] def createContext(primaryKeys: Seq[Map[String, Any]],
                                       columns: Seq[Map[String, Any]],
                                       tableName: String,
                                       className: String)(implicit logger: Logger): java.util.Map[String, Any] = {
    logger.debug(s"createContext($primaryKeys, $columns, $className): start")
    val context = Map[String, Any](
      "name"                   -> className, // deprecated
      "lowerCamelName"         -> (className.substring(0, 1).toLowerCase + className.substring(1)), // deprecated
      "className"              -> className,
      "tableName"              -> tableName,
      "decapitalizedClassName" -> StringUtil.decapitalize(className),
      "primaryKeys"            -> primaryKeys.map(_.asJava).asJava,
      "columns"                -> columns.map(_.asJava).asJava,
      "primaryKeysWithColumns" -> (primaryKeys ++ columns).map(_.asJava).asJava, // deprecated
      "allColumns"             -> (primaryKeys ++ columns).map(_.asJava).asJava
    ).asJava
    logger.debug(s"createContext: finished = $context")
    context
  }

  /**
    * 出力先のファイルを生成する。
    *
    * @param outputDirectory 出力先ディレクトリ
    * @param className       クラス名
    * @return [[File]]
    */
  private[generator] def createFile(outputDirectory: File, className: String)(implicit logger: Logger): File = {
    logger.debug(s"createFile($outputDirectory, $className): start")
    val file = outputDirectory / (className + ".scala")
    logger.debug(s"createFile: finished = $file")
    file
  }

  /**
    * テンプレートコンフィグレーションを生成する。
    *
    * @param templateDirectory テンプレートディレクトリ
    * @param logger            ロガー
    * @return テンプレートコンフィグレーション
    */
  private[generator] def createTemplateConfiguration(
      templateDirectory: File
  )(implicit logger: Logger): Try[freemarker.template.Configuration] = Try {
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
    * [[generateAll]]のためのタスク。
    *
    * @return タスク定義
    */
  def generateAllTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    implicit val logger = streams.value.log
    logger.info("sbt-dao-generator: generateAll task")
    logger.info("driverClassName = " + (driverClassName in generator).value.toString)
    logger.info("jdbcUrl = " + (jdbcUrl in generator).value.toString)
    logger.info("jdbcUser = " + (jdbcUser in generator).value.toString)
    logger.info("schemaName = " + (schemaName in generator).value.getOrElse(""))

    val classLoader =
      if ((enableManagedClassPath in generator).value)
        ClasspathUtilities.toLoader(
          (managedClasspath in Compile).value.map(_.data),
          ClasspathUtilities.xsbtiLoader
        )
      else
        ClasspathUtilities.xsbtiLoader
    using(
      getJdbcConnection(
        classLoader,
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
        (propertyTypeNameMapper in generator).value,
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

  /**
    * すべてのテーブルを指定してファイルを生成する。
    *
    * @param ctx [[GeneratorContext]]
    * @return 生成されたSeq[File]
    */
  private[generator] def generateAll(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateAll: start")
    val result = for {
      cfg        <- createTemplateConfiguration(ctx.templateDirectory)
      tableDescs <- getTableDescs(ctx.connection, ctx.schemaName)
      files <- tableDescs
        .filter { tableDesc =>
          ctx.tableNameFilter(tableDesc.tableName)
        }
        .foldLeft(Try(Seq.empty[File])) { (result, tableDesc) =>
          for {
            r1 <- result
            r2 <- generateFiles(cfg, tableDesc)
          } yield r1 ++ r2
        }
    } yield files
    logger.debug(s"generateAll: finished = $result")
    result
  }

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

}

object SbtDaoGenerator extends SbtDaoGenerator
