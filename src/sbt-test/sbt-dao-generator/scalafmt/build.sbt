enablePlugins(FlywayPlugin)

name := "dao-generator-test-scalafmt"

Compile / sourceGenerators += generator / generateAll

scalaVersion := "3.7.3"

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.200"
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
}

generator / classNameMapper := {
  case a => Seq(a)
}

generator / templateNameMapper := {
  case _ => "template_a.ftl"
}

InputKey[Unit]("checkFormat") := {
  val Seq(f) = ((Compile / sourceManaged).value ** "*.scala").get()
  assert(f.getName == "DEPT.scala")
  sbtBinaryVersion.value match {
    case "2" =>
      streams.value.log.warn("pending sbt 2 test")
    case _ =>
      assert(IO.read(f) == IO.read(file("expect_format")))
  }
}

InputKey[Unit]("checkNoFormat") := {
  val Seq(f) = ((Compile / sourceManaged).value ** "*.scala").get()
  assert(f.getName == "DEPT.scala")
  assert(IO.read(f) == IO.read(file("expect_no_format")))
}
