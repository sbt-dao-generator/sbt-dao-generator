import sbt_dao_generator.model.ColumnDesc
import scala.sys.process.Process

enablePlugins(FlywayPlugin)

name := "mysql"

scalaVersion := "2.13.17"

libraryDependencies ++= Seq(
  "com.mysql" % "mysql-connector-j" % "9.6.0" exclude ("com.google.protobuf", "protobuf-java")
)

def portNumber = 3310
def databaseName = "sbt_dao_gen"

flywayDriver := "com.mysql.cj.jdbc.Driver"

flywayUrl := s"jdbc:mysql://localhost:${portNumber}/${databaseName}?useSSL=false&allowPublicKeyRetrieval=true"

flywayUser := "sbt_dao_gen"

flywayPassword := "passwd"

flywayCleanDisabled := false

daoGeneratorTableNameFilter := { tableName =>
  tableName.toUpperCase != "SCHEMA_VERSION" && tableName.toUpperCase != "FLYWAY_SCHEMA_HISTORY"
}

daoGeneratorDriverClassName := flywayDriver.value

daoGeneratorJdbcUrl := flywayUrl.value

daoGeneratorJdbcUser := flywayUser.value

daoGeneratorJdbcPassword := flywayPassword.value

val TypeExtractor = ".*?/TYPE:(.*?)/.*".r

daoGeneratorAdvancedPropertyTypeNameMapper := {
  case (_, _, ColumnDesc(_, _, _, _, _, Some(TypeExtractor(t)), _)) => t.trim
  case (s, _, _) if s.toUpperCase() == "BIGINT" => "Long"
  case (s, _, _) if s.toUpperCase() == "INT" => "Int"
  case (s, _, _) if s.toUpperCase() == "VARCHAR" => "String"
  case (s, _, _) if s.toUpperCase() == "BOOLEAN" => "Boolean"
  case (s, _, _) if s.toUpperCase() == "DATE" | s.toUpperCase() == "TIMESTAMP" => "java.util.Date"
  case (s, _, _) if s.toUpperCase() == "DECIMAL" => "BigDecimal"
}

daoGeneratorClassNameMapper := {
  case s if s.toUpperCase() == "DEPT" => Seq("Dept", "DeptSpec")
  case s if s.toUpperCase() == "EMP" => Seq("Emp", "EmpSpec")
}

daoGeneratorTemplateNameMapper := {
  case "Dept" | "DeptSpec" => "template_a.ftl"
  case "Emp" | "EmpSpec" => "template_b.ftl"
}

daoGeneratorOutputDirectoryMapper := {
  case className: String if className.endsWith("Spec") => (Test / sourceManaged).value
  case className: String => (Compile / sourceManaged).value
}

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
      "mysql:8.0.39",
      "--character-set-server=utf8",
      "--collation-server=utf8_unicode_ci"
    )
  ).!
}

TaskKey[Unit]("stopMySQL") := {
  Process(s"docker rm -f ${dockerName}").!
}

InputKey[Unit]("checkGeneratedSources") := {
  val actual = ((Compile / sourceManaged).value ** "*.scala").get().map(_.getName).toSet
  val expect = Def.spaceDelimited("expect files").parsed.toSet
  assert(actual == expect, s"${actual} != ${expect}")
}
