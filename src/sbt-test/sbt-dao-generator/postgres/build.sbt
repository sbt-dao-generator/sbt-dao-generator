import sbt_dao_generator.model.ColumnDesc
import scala.sys.process.Process

enablePlugins(FlywayPlugin)

scalaVersion := "2.13.17"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.7.4"
)

def portNumber = 3311
def databaseName = "sbt_dao_gen"

flywayDriver := "org.postgresql.Driver"

flywayUrl := s"jdbc:postgresql://localhost:${portNumber}/${databaseName}"

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
  case (s, _, _) if s.toUpperCase() == "INT4" => "Int"
  case (s, _, _) if s.toUpperCase() == "NUMERIC" => "Int"
  case (s, _, _) if s.toUpperCase() == "VARCHAR" => "String"
  case (s, _, _) if s.toUpperCase() == "BOOLEAN" => "Boolean"
  case (s, _, _) if s.toUpperCase() == "DATE" | s.toUpperCase() == "TIMESTAMP" => "java.util.Date"
  case (s, _, _) if s.toUpperCase() == "DECIMAL" => "BigDecimal"
  case (s, _, _) => s
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

def dockerName = "sbt-dao-generator-test-2"

TaskKey[Unit]("startPostgres") := {
  Process(
    List(
      "docker",
      "run",
      "--name",
      dockerName,
      "-e",
      s"POSTGRES_USER=${flywayUser.value}",
      "-e",
      s"POSTGRES_PASSWORD=${flywayPassword.value}",
      "-e",
      s"POSTGRES_DB=${databaseName}",
      "-p",
      s"${portNumber}:5432",
      "-d",
      "postgres:11.4"
    )
  ).!
}

TaskKey[Unit]("stopPostgres") := {
  Process(s"docker rm -f ${dockerName}").!
}

InputKey[Unit]("checkGeneratedSources") := {
  val actual = ((Compile / sourceManaged).value ** "*.scala").get().map(_.getName).toSet
  val expect = Def.spaceDelimited("expect files").parsed.toSet
  assert(actual == expect, s"${actual} != ${expect}")
}
