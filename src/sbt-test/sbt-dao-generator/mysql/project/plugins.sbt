addSbtPlugin("io.github.sbt-dao-generator" % "sbt-dao-generator" % sys.props("project.version"))

addSbtPlugin("com.github.sbt" % "flyway-sbt" % "10.21.0")

libraryDependencies += "org.flywaydb" % "flyway-mysql" % "11.0.0"
