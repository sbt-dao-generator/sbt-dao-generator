enablePlugins(FlywayPlugin)

name := "simple"

scalaVersion := "2.13.15"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.187"
)

flywayUrl := "jdbc:h2:file:./target/test"

flywayUser := "sa"

generator / tableNameFilter := { tableName =>
  tableName.toUpperCase != "SCHEMA_VERSION" && tableName.toUpperCase != "FLYWAY_SCHEMA_HISTORY"
}

generator / driverClassName := "org.h2.Driver"

generator / jdbcUrl := "jdbc:h2:file:./target/test"

generator / jdbcUser := "sa"

generator / jdbcPassword := ""

generator / propertyTypeNameMapper := {
  case "INTEGER" => "Int"
  case "VARCHAR" => "String"
  case "BOOLEAN" => "Boolean"
  case "DATE" | "TIMESTAMP" => "java.util.Date"
  case "DECIMAL" => "BigDecimal"
}

generator / classNameMapper := {
  case "DEPT" => Seq("Dept", "DeptSpec")
  case "EMP" => Seq("Emp", "EmpSpec")
}

generator / templateNameMapper := {
  case "Dept" | "DeptSpec" => "template_a.ftl"
  case "Emp" | "EmpSpec" => "template_b.ftl"
}

generator / outputDirectoryMapper := {
  case (className: String) if className.endsWith("Spec") => (Test / sourceManaged).value
  case (className: String) => (Compile / sourceManaged).value
}

InputKey[Unit]("checkGeneratedSources") := {
  val actual = ((Compile / sourceManaged).value ** "*.scala").get().map(_.getName).toSet
  val expect = Def.spaceDelimited("expect files").parsed.toSet
  assert(actual == expect, s"${actual} != ${expect}")
}
