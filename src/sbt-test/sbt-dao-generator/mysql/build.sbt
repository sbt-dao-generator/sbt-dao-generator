import scala.sys.process.Process

enablePlugins(FlywayPlugin)

name := "mysql"

scalaVersion := "2.12.15"

libraryDependencies ++= Seq(
  "mysql" % "mysql-connector-java" % "8.0.25"
)

logLevel := Level.Debug

def portNumber = 3310
def databaseName = "sbt_dao_gen"

flywayDriver := "com.mysql.jdbc.Driver"

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

generator / propertyTypeNameMapper := {
  case s if s.toUpperCase() == "BIGINT" => "Long"
  case s if s.toUpperCase() == "INT" => "Int"
  case s if s.toUpperCase() == "VARCHAR" => "String"
  case s if s.toUpperCase() == "BOOLEAN" => "Boolean"
  case s if s.toUpperCase() == "DATE" | s.toUpperCase() == "TIMESTAMP" => "java.util.Date"
  case s if s.toUpperCase() == "DECIMAL" => "BigDecimal"
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
  Process(List(
    "docker", "run",
    "--name", dockerName,
    "-e", s"MYSQL_USER=${flywayUser.value}",
    "-e", s"MYSQL_PASSWORD=${flywayPassword.value}",
    "-e", s"MYSQL_ROOT_PASSWORD=${flywayPassword.value}",
    "-e", s"MYSQL_DATABASE=${databaseName}",
    "-p", s"${portNumber}:3306",
    "-d", "mysql:5.7.37",
    "--character-set-server=utf8",
    "--collation-server=utf8_unicode_ci",
  )).!
}

TaskKey[Unit]("stopMySQL") := {
  Process(s"docker rm -f ${dockerName}").!
}
