addSbtPlugin("io.github.sbt-dao-generator" % "sbt-dao-generator" % sys.props("project.version"))

addSbtPlugin("com.github.sbt" % "flyway-sbt" % "9.22.0")

libraryDependencies += "org.flywaydb" % "flyway-mysql" % "9.22.3"
