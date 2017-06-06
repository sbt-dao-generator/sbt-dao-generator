logLevel := Level.Warn

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Classpaths.typesafeSnapshots,
  "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
  "Seasar Repository" at "http://maven.seasar.org/maven2/",
  "Flyway" at "http://flywaydb.org/repo"
)

{
  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("jp.co.septeni-original" % "sbt-dao-generator" % pluginVersion)
}

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")
