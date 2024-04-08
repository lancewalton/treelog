import org.typelevel.scalacoptions.ScalacOptions

val Scala34  = "3.4.1"
val Scala33  = "3.3.3"
val Scala213 = "2.13.13"
val Scala212 = "2.12.19"

lazy val buildSettings: Seq[Setting[_]] =
  Defaults.coreDefaultSettings ++ Seq[Setting[_]](
    organization       := "com.casualmiracles",
    name               := "treelog-cats",
    scalaVersion       := Scala33,
    crossScalaVersions := Seq(Scala34, Scala33, Scala213, Scala212),
    releaseCrossBuild  := true,
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, minor)) => Seq("-release:8")
        case _                => Seq("-release:8", "-Xsource:3")
      }
    },
    tpolecatExcludeOptions ++= {
      Set(ScalacOptions.lintImplicitRecursion, ScalacOptions.warnUnusedImports)
    },
    Test / tpolecatExcludeOptions ++= {
      Set(
        ScalacOptions.warnNonUnitStatement
      )
    }
  )

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

Compile / scalafmtConfig := file(".scalafmt.conf")

lazy val websiteSettings = Seq[Setting[_]](
  git.remoteRepo := "git@github.com:lancewalton/treelog.git"
)

resolvers += Resolver.sonatypeRepo("releases")
credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")

def allDependencies(scalaVersion: String) = {

  val deps = Seq(
    "org.typelevel" %% "cats-core"     % "2.10.0",
    "org.typelevel" %% "cats-free"     % "2.10.0",
    "org.scalatest" %% "scalatest"     % "3.2.18" % "test",
    "io.argonaut"   %% "argonaut"      % "6.3.9"  % "test",
    "io.argonaut"   %% "argonaut-cats" % "6.3.9"  % "test"
  )

  if (util.isScala3(scalaVersion))
    deps.map(
      _.exclude("org.typelevel", "cats-effect_2.13")
        .exclude("org.typelevel", "cats-effect_2.13")
        .exclude("org.typelevel", "cats-core_2.13")
    )
  else
    deps ++ Seq(
      compilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.3" cross CrossVersion.full),
      compilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
    )

}

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
  publishMavenStyle             := true,
  Test / publishArtifact        := false,
  pomIncludeRepository          := { _ => false },
  publishTo                     := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra                      := <licenses>
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
      <developer>
        <id>dpg</id>
        <name>Dave Pereira-Gurnell</name>
        <email>dave [dot] gurnell [at] underscore [dot] io</email>
        <organization>Underscore LLP</organization>
      </developer>
      <developer>
        <id>everpeace</id>
        <name>Shingo Omura</name>
      </developer>
      <developer>
        <id>stremlenye</id>
        <name>Yury Ankudinov</name>
      </developer>
      <developer>
        <id>0xdevalias</id>
        <name>Glenn Grant</name>
      </developer>
      <developer>
        <id>ahjohannessen</id>
        <name>Alex Henning Johannessen</name>
      </developer>
    </developers>
)

lazy val treeLog = (project in file("."))
  .settings(
    buildSettings ++
      publishSettings ++
      websiteSettings ++
      Seq(
        resolvers := Seq(Resolver.typesafeRepo("releases")),
        libraryDependencies ++= allDependencies(scalaVersion.value)
      )
  )

addCommandAlias("testAll", "clean;+test")
