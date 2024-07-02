import scala.sys.process.Process
import jp.co.septeni_original.sbt.dao.generator.model.ColumnDesc

enablePlugins(FlywayPlugin)

name := "mysql"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "com.mysql" % "mysql-connector-j" % "9.0.0"
)

def portNumber = 3310
def databaseName = "sbt_dao_gen"

flywayDriver := "com.mysql.cj.jdbc.Driver"

flywayUrl := s"jdbc:mysql://localhost:${portNumber}/${databaseName}?useSSL=false"

flywayUser := "sbt_dao_gen"

flywayPassword := "passwd"

generator / tableNameFilter := { tableName =>
  tableName.toUpperCase != "SCHEMA_VERSION" && tableName.toUpperCase != "FLYWAY_SCHEMA_HISTORY"
}

generator / driverClassName := flywayDriver.value

generator / jdbcUrl := flywayUrl.value

generator / jdbcUser := flywayUser.value

generator / jdbcPassword := flywayPassword.value

val TypeExtractor = ".*?/TYPE:(.*?)/.*".r

generator / advancedPropertyTypeNameMapper := {
  case (_, _, ColumnDesc(_, _, _, _, _, Some(TypeExtractor(t)))) => t.trim
  case (s, _, _) if s.toUpperCase() == "BIGINT" => "Long"
  case (s, _, _) if s.toUpperCase() == "INT" => "Int"
  case (s, _, _) if s.toUpperCase() == "VARCHAR" => "String"
  case (s, _, _) if s.toUpperCase() == "BOOLEAN" => "Boolean"
  case (s, _, _) if s.toUpperCase() == "DATE" | s.toUpperCase() == "TIMESTAMP" => "java.util.Date"
  case (s, _, _) if s.toUpperCase() == "DECIMAL" => "BigDecimal"
}

generator / classNameMapper := {
  case s if s.toUpperCase() == "DEPT" => Seq("Dept", "DeptSpec")
  case s if s.toUpperCase() == "EMP" => Seq("Emp", "EmpSpec")
}

generator / templateNameMapper := {
  case "Dept" | "DeptSpec" => "template_a.ftl"
  case "Emp" | "EmpSpec" => "template_b.ftl"
}

generator / outputDirectoryMapper := {
  case (className: String) if className.endsWith("Spec") => (Test / sourceManaged).value
  case (className: String) => (Compile / sourceManaged).value
}

Compile / sourceGenerators += generator / generateAll

def dockerName = "sbt-dao-generator-test-1"

TaskKey[Unit]("startMySQL") := {
  Process(
    List(
      "docker",
      "run",
      "--name",
      dockerName,
      "-e",
      s"MYSQL_USER=${flywayUser.value}",
      "-e",
      s"MYSQL_PASSWORD=${flywayPassword.value}",
      "-e",
      s"MYSQL_ROOT_PASSWORD=${flywayPassword.value}",
      "-e",
      s"MYSQL_DATABASE=${databaseName}",
      "-p",
      s"${portNumber}:3306",
      "-d",
      "mysql:5.7.37",
      "--character-set-server=utf8",
      "--collation-server=utf8_unicode_ci"
    )
  ).!
}

TaskKey[Unit]("stopMySQL") := {
  Process(s"docker rm -f ${dockerName}").!
}
