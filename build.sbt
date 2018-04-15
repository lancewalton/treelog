val buildSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq[Setting[_]](
  organization := "com.casualmiracles",
  name := "treelog",
  scalaVersion := "2.12.5",
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
  
