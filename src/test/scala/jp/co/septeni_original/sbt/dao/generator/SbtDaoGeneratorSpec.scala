package jp.co.septeni_original.sbt.dao.generator

import org.scalatest._
import sbt.ConsoleLogger

class SbtDaoGeneratorSpec extends FunSpec {

  import SbtDaoGenerator._

  describe("SbtDaoGeneratorSpec") {
    implicit val logger = ConsoleLogger()
    val conn = getJdbcConnection(
      Thread.currentThread().getContextClassLoader,
      "org.h2.Driver",
      "jdbc:h2:file:./test",
      "sa",
      ""
    ).get

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
