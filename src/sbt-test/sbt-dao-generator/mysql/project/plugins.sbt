resolvers += "Flyway" at "https://davidmweber.github.io/flyway-sbt.repo"

addSbtPlugin("io.github.sbt-dao-generator" % "sbt-dao-generator" % sys.props("project.version"))

addSbtPlugin("com.chatwork" % "sbt-wix-embedded-mysql" % "1.0.24")

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")
