import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import xerial.sbt.Sonatype.autoImport._

releaseCrossBuild := true

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
  releaseStepCommandAndRemaining("^ publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

sonatypeProfileName := "jp.co.septeni-original"

organization := "jp.co.septeni-original"

publishMavenStyle := true

(Test / publishArtifact) := false

publishTo := sonatypePublishTo.value

pomIncludeRepository := { _ =>
  false
}

pomExtra := {
  <url>https://github.com/septeni-original/sbt-dao-generator</url>
    <licenses>
      <license>
        <name>The MIT License</name>
        <url>http://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:septeni-original/sbt-dao-generator.git</url>
      <connection>scm:git:github.com/septeni-original/sbt-dao-generator</connection>
      <developerConnection>scm:git:git@github.com:septeni-original/sbt-dao-generator.git</developerConnection>
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

val sbtCrossVersion = (pluginCrossBuild / sbtVersion)

scalaVersion := (CrossVersion partialVersion sbtCrossVersion.value match {
  case Some((1, _)) => "2.12.4"
  case _            => sys error s"Unhandled sbt version ${sbtCrossVersion.value}"
})

crossSbtVersions := Seq("1.3.13")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  "Seasar Repository" at "https://maven.seasar.org/maven2/"
)

libraryDependencies ++= Seq(
  "com.spotify"     % "docker-client"   % "2.7.26",
  "ch.qos.logback"  % "logback-classic" % "1.2.8",
  "org.slf4j"       % "slf4j-api"       % "1.7.30",
  "org.freemarker"  % "freemarker"      % "2.3.30",
  "org.seasar.util" % "s2util"          % "0.0.1",
  "org.scalatest"   %% "scalatest"      % "3.0.9" % Test,
  "com.h2database"  % "h2"              % "1.4.187" % Test
)

credentials += Credentials((LocalRootProject / baseDirectory).value / ".credentials")

scriptedBufferLog := false

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dproject.version=" + version.value)
}
