import org.typelevel.scalacoptions.ScalacOptions
import xerial.sbt.Sonatype.sonatypeCentralHost

// We use the oldest minor and latest patch version of each scala major version to ensuire
// binary compatibility with the latest patch version of each scala major version
val Scala33  = "3.3.5"
val Scala213 = "2.13.16"
val Scala212 = "2.12.20"

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

lazy val buildSettings: Seq[Setting[_]] =
  Defaults.coreDefaultSettings ++ Seq[Setting[_]](
    organization       := "com.casualmiracles",
    name               := "treelog-cats",
    scalaVersion       := Scala33,
    crossScalaVersions := Seq(Scala33, Scala213, Scala212),
    versionScheme      := Some("early-semver"),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, minor)) => Seq("-release:8")
        case _                => Seq("-release:8", "-Xsource:3")
      }
    },
    tpolecatExcludeOptions ++=
      Set(ScalacOptions.lintImplicitRecursion, ScalacOptions.warnUnusedImports),
    Test / tpolecatExcludeOptions ++=
      Set(
        ScalacOptions.warnNonUnitStatement
      ),
    Compile / doc / sources := {
      if (scalaVersion.value.startsWith("3")) (Compile / doc / sources).value
      else Seq.empty
    }
  )

Compile / scalafmtConfig := file(".scalafmt.conf")

def allDependencies(scalaVersion: String) = {

  val deps = Seq(
    "org.typelevel" %% "cats-core"     % "2.13.0",
    "org.typelevel" %% "cats-free"     % "2.13.0",
    "org.scalatest" %% "scalatest"     % "3.2.19" % "test",
    "io.argonaut"   %% "argonaut"      % "6.3.10" % "test",
    "io.argonaut"   %% "argonaut-cats" % "6.3.10" % "test"
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

def publishSettings: Seq[Setting[_]] = Seq(
  Test / publishArtifact := false,
  pomIncludeRepository   := { _ => false },
  pomExtra               := <licenses>
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
      Seq(
        resolvers := Seq(Resolver.typesafeRepo("releases")),
        libraryDependencies ++= allDependencies(scalaVersion.value)
      )
  )

addCommandAlias("testAll", "clean;+test")
