import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseCrossBuild := true

crossScalaVersions += "3.7.4"

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" => sbtVersion.value
    case _ => "2.0.0-RC8"
  }
}

releaseTagName := {
  (ThisBuild / version).value
}

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+ publishSigned"),
  releaseStepCommandAndRemaining("sonaRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

organization := "io.github.sbt-dao-generator"

publishMavenStyle := true

(Test / publishArtifact) := false

publishTo := (if (isSnapshot.value) None else localStaging.value)

pomIncludeRepository := { _ =>
  false
}

pomExtra := {
  <url>https://github.com/sbt-dao-generator/sbt-dao-generator</url>
    <licenses>
      <license>
        <name>The MIT License</name>
        <url>http://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:sbt-dao-generator/sbt-dao-generator.git</url>
      <connection>scm:git:github.com/sbt-dao-generator/sbt-dao-generator</connection>
      <developerConnection>scm:git:git@github.com:sbt-dao-generator/sbt-dao-generator.git</developerConnection>
    </scm>
    <developers>
      <developer>
        <id>kimutyam</id>
        <name>Akihiro Kimura</name>
      </developer>
      <developer>
        <id>j5ik2o</id>
        <name>Junichi Kato</name>
      </developer>
    </developers>
}

name := "sbt-dao-generator"

enablePlugins(SbtPlugin)

libraryDependencies ++= Seq(
  "org.scalameta"      % "scalafmt-interfaces"  % "3.10.6",
  "ch.qos.logback"     % "logback-classic"      % "1.2.13",
  "org.slf4j"          % "slf4j-api"            % "2.0.17",
  "org.freemarker"     % "freemarker"           % "2.3.34",
  "org.scalatest"     %% "scalatest-funspec"    % "3.2.19" % Test,
  "com.mysql"          % "mysql-connector-j"    % "9.6.0"  % Test exclude ("com.google.protobuf", "protobuf-java"),
  "org.testcontainers" % "testcontainers-mysql" % "2.0.3"  % Test
)

Test / fork := true

scriptedBufferLog := false

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-Dproject.version=" + version.value)
}
