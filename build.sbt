lazy val buildSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq[Setting[_]](
  organization       := "com.casualmiracles",
  name               := "treelog-cats",
  scalaVersion       := "2.13.3",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.12"),
  releaseCrossBuild  := true,
  scalacOptions      := util.scalacOptions(scalaVersion.value),
  Compile / unmanagedSourceDirectories += {
    (Compile / sourceDirectory).value / (if(util.priorTo2_13(scalaVersion.value)) "scala-2.12" else "scala-2.13")
  }
)

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

lazy val websiteSettings = Seq[Setting[_]](
  git.remoteRepo := "git@github.com:lancewalton/treelog.git"
)

resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

lazy val allDependencies = Seq(
  "org.typelevel" %% "cats-core"     % "2.2.0",
  "org.typelevel" %% "cats-free"     % "2.2.0",
  "org.scalatest" %% "scalatest"     % "3.2.2" % "test",
  "io.argonaut"   %% "argonaut"      % "6.3.1" % "test",
  "io.argonaut"   %% "argonaut-cats" % "6.3.1" % "test")

/* see http://www.scala-sbt.org/using_sonatype.html and http://www.cakesolutions.net/teamblogs/2012/01/28/publishing-sbt-projects-to-nexus/
 * Instructions from sonatype: https://issues.sonatype.org/browse/OSSRH-2841?focusedCommentId=150049#comment-150049
 * Deploy snapshot artifacts into repository https://oss.sonatype.org/content/repositories/snapshots
 * Deploy release artifacts into the staging repository https://oss.sonatype.org/service/local/staging/deploy/maven2
 * Promote staged artifacts into repository 'Releases'
 * Download snapshot and release artifacts from group https://oss.sonatype.org/content/groups/public
 * Download snapshot, release and staged artifacts from staging group https://oss.sonatype.org/content/groups/staging
 */
def publishSettings: Seq[Setting[_]] = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },

  pomExtra := <licenses>
    <license>
      <name>MIT License</name>
      <url>http://www.opensource.org/licenses/mit-license/</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
    <url>https://github.com/lancewalton/treelog</url>
    <developers>
      <developer>
        <id>lcw</id>
        <name>Lance Walton</name>
        <email>lance [dot] walton [at] underscore [dot] io</email>
        <organization>Underscore LLP</organization>
      </developer>
      <developer>
        <id>cjw</id>
        <name>Channing Walton</name>
        <email>channing [dot] walton [at] underscore [dot] io</email>
        <organization>Underscore LLP</organization>
      </developer>
    </developers>)

lazy val treeLog = (project in file("."))
  .settings(
    buildSettings ++
    publishSettings ++
    websiteSettings ++
    Seq(
      resolvers            := Seq(Resolver.typesafeRepo("releases")),
      libraryDependencies ++= allDependencies)
  )
