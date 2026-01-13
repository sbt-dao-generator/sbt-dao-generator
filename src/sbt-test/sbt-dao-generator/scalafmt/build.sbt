enablePlugins(FlywayPlugin)

name := "dao-generator-test-scalafmt"

Compile / sourceGenerators += daoGeneratorGenerateAll

scalaVersion := "3.8.0"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.200"
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
}

daoGeneratorClassNameMapper := { case a =>
  Seq(a)
}

daoGeneratorTemplateNameMapper := { case _ =>
  "template_a.ftl"
}

InputKey[Unit]("checkFormat") := {
  val Seq(f) = ((Compile / sourceManaged).value ** "*.scala").get()
  assert(f.getName == "DEPT.scala")
  assert(IO.read(f) == IO.read(file("expect_format")))
}

InputKey[Unit]("checkNoFormat") := {
  val Seq(f) = ((Compile / sourceManaged).value ** "*.scala").get()
  assert(f.getName == "DEPT.scala")
  assert(IO.read(f) == IO.read(file("expect_no_format")))
}
