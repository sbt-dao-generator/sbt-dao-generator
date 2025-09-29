addSbtPlugin("io.github.sbt-dao-generator" % "sbt-dao-generator" % sys.props("project.version"))

addSbtPlugin("com.github.sbt" % "flyway-sbt" % "11.11.0")

libraryDependencies ++= {
  if (sbtBinaryVersion.value == "1.0") {
    Seq(
      Defaults.sbtPluginExtra(
        "org.scalameta" % "sbt-scalafmt" % "2.5.5",
        sbtBinaryVersion.value,
        scalaBinaryVersion.value
      )
    )
  } else {
    Nil
  }
}
