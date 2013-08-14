import sbt._
import Keys._
import java.io.File
import scala.io.Source
import IO._
import com.typesafe.sbt.SbtGhPages._
import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtSite._

object BuildSettings {

  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ Seq[Setting[_]](
    organization := "com.casualmiracles",
    name := "treelog",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.10.2",
    scalaBinaryVersion := "2.10",
    scalacOptions := Seq("-language:_"),
    unmanagedSourceDirectories in Compile := Seq(file("src/main/scala") ),
    unmanagedSourceDirectories in Test := Seq(file("src/test/scala") )
  )
}

object WebsiteSettings {
  val websiteSettings = site.settings ++ ghpages.settings ++ Seq[Setting[_]](
    git.remoteRepo := "git@github.com:lancewalton/treelog.git"
  )
  site.includeScaladoc()
}

object Dependencies {
  val allDependencies = Seq("org.scalaz" %% "scalaz-core" % "7.0.3", "org.scalatest" %% "scalatest" % "2.0.M5b")
}

/* see http://www.scala-sbt.org/using_sonatype.html and http://www.cakesolutions.net/teamblogs/2012/01/28/publishing-sbt-projects-to-nexus/
 * Instructions from sonatype: https://issues.sonatype.org/browse/OSSRH-2841?focusedCommentId=150049#comment-150049
 * Deploy snapshot artifacts into repository https://oss.sonatype.org/content/repositories/snapshots
 * Deploy release artifacts into the staging repository https://oss.sonatype.org/service/local/staging/deploy/maven2
 * Promote staged artifacts into repository 'Releases'
 * Download snapshot and release artifacts from group https://oss.sonatype.org/content/groups/public
 * Download snapshot, release and staged artifacts from staging group https://oss.sonatype.org/content/groups/staging
 */
object Publishing {

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

    pomExtra := (
      <licenses>
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
      <description>
        Allows logging in a tree structure so that comprehensive logging does not become incomprehensible.
      </description>
      <url>https://github.com/lancewalton/treelog</url>
      <developers>
        <developer>
          <id>lcw</id>
          <name>Lance Walton</name>
          <email>lance [dot] walton [at] underscoreconsulting [dot] com</email>
          <organization>Underscore Consulting Ltd</organization>
        </developer>
        <developer>
          <id>cjw</id>
          <name>Channing Walton</name>
          <email>channing [dot] walton [at] underscoreconsulting [dot] com</email>
          <organization>Underscore Consulting Ltd</organization>
        </developer>
      </developers>))
}

object TreeLogBuild extends Build {

  import BuildSettings._
  import Dependencies._
  import Publishing._
  import WebsiteSettings._

  lazy val treeLog = Project(
    "treeLog",
    file("."),
    settings = buildSettings ++ publishSettings ++ websiteSettings ++ Seq(resolvers := Seq(Classpaths.typesafeResolver), libraryDependencies ++= allDependencies))
}
