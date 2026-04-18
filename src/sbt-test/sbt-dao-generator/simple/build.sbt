enablePlugins(FlywayPlugin)

name := "simple"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.187"
)

flywayUrl := "jdbc:h2:file:./target/test"

flywayUser := "sa"

daoGeneratorTableNameFilter := { tableName =>
  tableName.toUpperCase != "SCHEMA_VERSION" && tableName.toUpperCase != "FLYWAY_SCHEMA_HISTORY"
}

daoGeneratorDriverClassName := "org.h2.Driver"

daoGeneratorJdbcUrl := "jdbc:h2:file:./target/test"

daoGeneratorJdbcUser := "sa"

daoGeneratorJdbcPassword := ""

daoGeneratorPropertyTypeNameMapper := {
  case "INTEGER" => "Int"
  case "VARCHAR" => "String"
  case "BOOLEAN" => "Boolean"
  case "DATE" | "TIMESTAMP" => "java.util.Date"
  case "DECIMAL" => "BigDecimal"
}

daoGeneratorClassNameMapper := {
  case "DEPT" => Seq("Dept", "DeptSpec")
  case "EMP" => Seq("Emp", "EmpSpec")
}

daoGeneratorTemplateNameMapper := {
  case "Dept" | "DeptSpec" => "template_a.ftl"
  case "Emp" | "EmpSpec" => "template_b.ftl"
}

daoGeneratorOutputDirectoryMapper := {
  case className: String if className.endsWith("Spec") => (Test / sourceManaged).value
  case className: String => (Compile / sourceManaged).value
}

InputKey[Unit]("checkGeneratedSources") := {
  val actual = ((Compile / sourceManaged).value ** "*.scala").get().map(_.getName).toSet
  val expect = Def.spaceDelimited("expect files").parsed.toSet
  assert(actual == expect, s"${actual} != ${expect}")
}
