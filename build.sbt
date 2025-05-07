import org.typelevel.scalacoptions.ScalacOptions
import xerial.sbt.Sonatype.sonatypeCentralHost

// We use the oldest minor and latest patch version of each scala major version to ensuire
// binary compatibility with the latest patch version of each scala major version
val Scala33  = "3.3.5"
val Scala213 = "2.13.16"
val Scala212 = "2.12.20"

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / name := "treelog-cats"
ThisBuild / organization := "com.casualmiracles"
ThisBuild / homepage := Some(url("https://github.com/lancewalton/treelog/"))
ThisBuild / licenses := List("MIT License" -> url("http://www.opensource.org/licenses/mit-license/"))
ThisBuild / developers := List(
  Developer(
    "lcw",
    "Lance Walton",
    "lancewalton@mac.com",
    url("http://underscore.io/")
  ),
  Developer(
    "cjw",
    "Channing Walton",
    "channingwalton@mac.com",
    url("http://underscore.io/")
  ),
  Developer(
    "dpg",
    "Dave Pereira-Gurnell",
    "dave.gurnell@underscore.io",
    url("http://underscore.io/")
  ),
    Developer(
        "everpeace",
        "Shingo Omura",
        "",
        url("https://github.com/lancewalton/treelog/")
    ),
    Developer(
        "stremlenye",
        "Yury Ankudinov",
        "",
        url("https://github.com/lancewalton/treelog/")
    ),
    Developer(
        "0xdevalias",
        "Glenn Grant",
        "",
        url("https://github.com/lancewalton/treelog/")
    ),
    Developer(
        "ahjohannessen",
        "Alex Henning Johannessen",
        "",
        url("https://github.com/lancewalton/treelog/")
    )
)
ThisBuild / versionScheme := Some("early-semver")


lazy val buildSettings: Seq[Setting[_]] =
  Defaults.coreDefaultSettings ++ Seq[Setting[_]](
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

lazy val treeLog = (project in file("."))
  .settings(
    buildSettings ++
      Seq(
        resolvers := Seq(Resolver.typesafeRepo("releases")),
        libraryDependencies ++= allDependencies(scalaVersion.value)
      )
  )

addCommandAlias("testAll", "clean;+test")
