name := "simple"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.187"
)

seq(flywaySettings: _*)

flywayUrl := "jdbc:h2:file:./target/test"

flywayUser := "sa"

tableNameFilter in generator := { tableName: String => tableName.toUpperCase != "SCHEMA_VERSION"}

driverClassName in generator := "org.h2.Driver"

jdbcUrl in generator := "jdbc:h2:file:./target/test"

jdbcUser in generator := "sa"

jdbcPassword in generator := ""

typeNameMapper in generator := {
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

sourceGenerators in Compile <+= generateAll in generator