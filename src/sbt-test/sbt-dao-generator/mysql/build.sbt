name := "mysql"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "mysql" % "mysql-connector-java" % "5.1.42"
)

logLevel := Level.Debug

flywayDriver := "com.mysql.jdbc.Driver"

flywayUrl := "jdbc:mysql://localhost:3310/sbt_dao_gen?useSSL=false"

flywayUser := "sbt_dao_gen"

flywayPassword := "passwd"

generator / tableNameFilter := { tableName: String => tableName.toUpperCase != "SCHEMA_VERSION"}

generator / driverClassName := flywayDriver.value

generator / jdbcUrl := flywayUrl.value

generator / jdbcUser := flywayUser.value

generator / jdbcPassword := flywayPassword.value

wixMySQLVersion := com.wix.mysql.distribution.Version.v5_6_latest

wixMySQLSchemaName := "sbt_dao_gen"

wixMySQLUserName := Some(flywayUser.value)

wixMySQLPassword := Some(flywayPassword.value)

wixMySQLDownloadPath := Some(sys.env("HOME") + "/.wixMySQL/downloads")

generator / propertyTypeNameMapper := {
  case s if s.toUpperCase() == "BIGINT" => "Long"
  case s if s.toUpperCase() == "INT" => "Int"
  case s if s.toUpperCase() == "VARCHAR" => "String"
  case s if s.toUpperCase() == "BOOLEAN" => "Boolean"
  case s if (s.toUpperCase() == "DATE" | s.toUpperCase() == "TIMESTAMP") => "java.util.Date"
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

