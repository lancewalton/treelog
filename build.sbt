val buildSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq[Setting[_]](
  organization := "com.casualmiracles",
  name := "treelog",
  scalaVersion := "2.12.4",
  scalaBinaryVersion := "2.12",
  scalacOptions := Seq(
    "-language:_",
   // "-Xfatal-warnings",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-Ywarn-unused-import")
)

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

val websiteSettings = Seq[Setting[_]](
  git.remoteRepo := "git@github.com:lancewalton/treelog.git"
)

resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")

val allDependencies = Seq(
  "org.scalaz"    %% "scalaz-core"               % "7.3.0-M18",
  "org.scalaz"    %% "scalaz-scalacheck-binding" % "7.3.0-M18" % "test",
  "org.scalaz"    %% "scalaz-effect"             % "7.3.0-M18" % "test",
  "org.scalatest" %% "scalatest"                 % "3.0.0"    % "test",
  "io.argonaut"   %% "argonaut"                  % "6.2-RC1"  % "test",
  "io.argonaut"   %% "argonaut-scalaz"           % "6.2-RC1"  % "test")

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
  pomIncludeRepository := { _ ⇒ false },
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
        <email>lance [dot] walton [at] casualmiracles [dot] com</email>
        <organization>Casual Miracles Ltd</organization>
      </developer>
      <developer>
        <id>cjw</id>
        <name>Channing Walton</name>
        <email>channing [dot] walton [at] casualmiracles [dot] com</email>
        <organization>Casual Miracles Ltd</organization>
      </developer>
    </developers>)

lazy val treeLog = (project in file("."))
  .settings(
    buildSettings ++
    publishSettings ++
    websiteSettings ++
    Seq(resolvers := Seq(Classpaths.typesafeReleases),
        libraryDependencies ++= allDependencies))
  
