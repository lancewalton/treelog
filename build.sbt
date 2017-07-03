import com.typesafe.sbt.SbtGhPages._
import com.typesafe.sbt.SbtGit.{git, _}
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtSite._
import sbt.Keys._
import sbt._

val buildSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq[Setting[_]](
  organization := "com.casualmiracles",
  name := "treelog",
  version := "1.4.1-SNAPSHOT",
  scalaVersion := "2.12.2",
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
    "-Ywarn-unused-import"),
  incOptions := incOptions.value.withNameHashing(true)
)

site.includeScaladoc()
val websiteSettings = site.settings ++ ghpages.settings ++ Seq[Setting[_]](
  git.remoteRepo := "git@github.com:lancewalton/treelog.git",
  siteMappings <++= (mappings in packageDoc in Compile, version) map { (m, v) ⇒
    for((f, d) ← m) yield (f, if (v.trim.endsWith("SNAPSHOT")) "api/master/" + d else "api/treelog-" + v + "/" + d)
  }
)

val allDependencies = Seq(
  "org.scalaz"    %% "scalaz-core"     % "7.3.0-M8",
  "org.scalatest" %% "scalatest"       % "3.0.0"   % "test",
  "io.argonaut"   %% "argonaut"        % "6.2-RC1" % "test",
  "io.argonaut"   %% "argonaut-scalaz" % "6.2-RC1" % "test")

/* see http://www.scala-sbt.org/using_sonatype.html and http://www.cakesolutions.net/teamblogs/2012/01/28/publishing-sbt-projects-to-nexus/
 * Instructions from sonatype: https://issues.sonatype.org/browse/OSSRH-2841?focusedCommentId=150049#comment-150049
 * Deploy snapshot artifacts into repository https://oss.sonatype.org/content/repositories/snapshots
 * Deploy release artifacts into the staging repository https://oss.sonatype.org/service/local/staging/deploy/maven2
 * Promote staged artifacts into repository 'Releases'
 * Download snapshot and release artifacts from group https://oss.sonatype.org/content/groups/public
 * Download snapshot, release and staged artifacts from staging group https://oss.sonatype.org/content/groups/staging
 */
def publishSettings: Seq[Setting[_]] = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ ⇒ false },
  publishTo <<= version { v: String ⇒
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
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
    <scm>
      <url>https://github.com/lancewalton/treelog.git</url>
      <connection>scm:https://github.com/lancewalton/treelog.git</connection>
    </scm>
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

lazy val treeLog = Project(
  "treeLog",
  file("."),
  settings =
    buildSettings ++
    publishSettings ++
    websiteSettings ++
    Seq(resolvers := Seq(Classpaths.typesafeReleases),
        libraryDependencies ++= allDependencies))
