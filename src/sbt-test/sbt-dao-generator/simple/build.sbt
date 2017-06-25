name := "simple"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.187"
)

logLevel := Level.Debug

flywayUrl := "jdbc:h2:file:./target/test"

flywayUser := "sa"

tableNameFilter in generator := { tableName: String => tableName.toUpperCase != "SCHEMA_VERSION"}

driverClassName in generator := "org.h2.Driver"

jdbcUrl in generator := "jdbc:h2:file:./target/test"

jdbcUser in generator := "sa"

jdbcPassword in generator := ""

propertyTypeNameMapper in generator := {
  case "INTEGER" => "Int"
  case "VARCHAR" => "String"
  case "BOOLEAN" => "Boolean"
  case "DATE" | "TIMESTAMP" => "java.util.Date"
  case "DECIMAL" => "BigDecimal"
}

classNameMapper in generator := {
  case "DEPT" => Seq("Dept", "DeptSpec")
  case "EMP" => Seq("Emp", "EmpSpec")
}

templateNameMapper in generator := {
  case "Dept" | "DeptSpec" => "template_a.ftl"
  case "Emp" | "EmpSpec" => "template_b.ftl"
}

outputDirectoryMapper in generator := {
  case (className: String) if className.endsWith("Spec") => (sourceManaged in Test).value
  case (className: String) => (sourceManaged in Compile).value
}

sourceGenerators in Compile += generateAll in generator
