resolvers += "Flyway" at "https://davidmweber.github.io/flyway-sbt.repo"

resolvers += "Seasar Repository" at "https://maven.seasar.org/maven2/"

addSbtPlugin("jp.co.septeni-original" % "sbt-dao-generator" % sys.props("project.version"))

addSbtPlugin("com.chatwork" % "sbt-wix-embedded-mysql" % "1.0.9")

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")
