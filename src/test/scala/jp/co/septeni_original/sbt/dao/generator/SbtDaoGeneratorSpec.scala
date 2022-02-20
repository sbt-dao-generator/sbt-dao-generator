package jp.co.septeni_original.sbt.dao.generator

import sbt.ConsoleLogger
import org.scalatest.funspec.AnyFunSpec
import com.dimafeng.testcontainers.MySQLContainer
import jp.co.septeni_original.sbt.dao.generator.util.Loan
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName
import scala.util.Try

class SbtDaoGeneratorSpec extends AnyFunSpec with BeforeAndAfterAll {
  private[this] implicit val logger: ConsoleLogger = ConsoleLogger()

  override def afterAll(): Unit = {
    container.stop()
    super.afterAll()
  }

  override def beforeAll(): Unit = {
    List(
      """
create table DEPT (
  DEPT_ID integer not null primary key,
  DEPT_NAME varchar(20),
  VERSION_NO integer
);
""",
      """
create table EMP (
  EMP_ID integer not null primary key,
  DEPT_ID integer not null,
  EMP_NAME varchar(20),
  HIREDATE date,
  SALARY numeric(7,2),
  VERSION_NO integer,
  FOREIGN KEY (DEPT_ID) REFERENCES DEPT(DEPT_ID)
);""",
      "insert into DEPT values(1, '技術部', 1);",
      "insert into DEPT values(2, '総務部', 1);",
      "insert into EMP values(1, 1, '山田太郎', '1980-12-17', 800, 1);",
      "insert into EMP values(2, 2, '山田花子', '1981-02-20', 1600, 1);"
    ).foreach { sql =>
      Loan
        .using(getConnection()) { c =>
          Loan.using(c.createStatement()) { s =>
            Try(s.execute(sql))
          }
        }
        .get
    }
    super.beforeAll()
  }

  import SbtDaoGenerator._

  private val container: MySQLContainer = {
    val c = MySQLContainer(
      mysqlImageVersion = DockerImageName.parse("mysql:5.7.37")
    )
    c.start()
    c
  }

  private def getConnection() = {
    getJdbcConnection(
      classLoader = Thread.currentThread().getContextClassLoader,
      driverClassName = "com.mysql.cj.jdbc.Driver",
      jdbcUrl = container.jdbcUrl,
      jdbcUser = container.username,
      jdbcPassword = container.password
    ).get
  }

  describe("SbtDaoGeneratorSpec") {
    lazy val conn = getConnection()

    it("should getTables") {
      val tables = getTables(conn, None).get
      println(tables)
      assert(tables.size == 2)
    }

    it("should getColumnDescs") {
      val columns = getColumnDescs(conn, None, "EMP").get
      println(columns)
      assert(columns.size == 6)
    }

    it("should getPrimaryKeyDescs") {
      val pkeys = getPrimaryKeyDescs(conn, None, "EMP").get
      println(pkeys)
      assert(pkeys.size == 1)
    }

    it("should getTableDescs") {
      val tables = getTableDescs(conn, None).get
      tables.foreach { e =>
        println(e.tableName)
        e.primaryDescs.foreach(println)
        e.columnDescs.foreach(println)
      }
      assert(tables.size == 2)
      assert(tables.find(_.tableName == "DEPT").map(_.columnDescs.size).get == 3)
      assert(tables.find(_.tableName == "EMP").map(_.columnDescs.size).get == 6)
    }

  }
}
