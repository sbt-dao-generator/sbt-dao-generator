name := "mysql"

libraryDependencies ++= Seq(
  "mysql" % "mysql-connector-java" % "5.1.42"
)

logLevel := Level.Debug

flywayDriver := "com.mysql.jdbc.Driver"

flywayUrl := "jdbc:mysql://localhost:3310/sbt_dao_gen?useSSL=false"

flywayUser := "sbt_dao_gen"

flywayPassword := "passwd"

tableNameFilter in generator := { tableName: String => tableName.toUpperCase != "SCHEMA_VERSION"}

driverClassName in generator := flywayDriver.value

jdbcUrl in generator := flywayUrl.value

jdbcUser in generator := flywayUser.value

jdbcPassword in generator := flywayPassword.value

wixMySQLVersion := com.wix.mysql.distribution.Version.v5_6_latest

wixMySQLSchemaName := "sbt_dao_gen"

wixMySQLUserName := Some(flywayUser.value)

wixMySQLPassword := Some(flywayPassword.value)

wixMySQLDownloadPath := Some(System.getProperty("java.io.tmpdir"))

propertyTypeNameMapper in generator := {
  case s if s.toUpperCase() == "BIGINT" => "Long"
  case s if s.toUpperCase() == "INT" => "Int"
  case s if s.toUpperCase() == "VARCHAR" => "String"
  case s if s.toUpperCase() == "BOOLEAN" => "Boolean"
  case s if (s.toUpperCase() == "DATE" | s.toUpperCase() == "TIMESTAMP") => "java.util.Date"
  case s if s.toUpperCase() == "DECIMAL" => "BigDecimal"
}

classNameMapper in generator := {
  case s if s.toUpperCase() == "DEPT" => Seq("Dept", "DeptSpec")
  case s if s.toUpperCase() == "EMP" => Seq("Emp", "EmpSpec")
}

templateNameMapper in generator := {
  case "Dept" | "DeptSpec" => "template_a.ftl"
  case "Emp" | "EmpSpec" => "template_b.ftl"
}

outputDirectoryMapper in generator := {
  case (className: String) if className.endsWith("Spec") => (sourceManaged in Test).value
  case (className: String) => (sourceManaged in Compile).value
}

//sourceGenerators in Compile <+= generateAll in generator

