import sbt._

object util {

def scalacOptions(scalaVersion: String): Seq[String] = {

  Seq(
    "-language:_",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ywarn-unused:imports"
  ) ++ (if (priorTo2_13(scalaVersion)) Seq("-Yno-adapted-args", "-Ypartial-unification", "-Xfuture") else Nil)
}

def priorTo2_13(scalaVersion: String): Boolean =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, minor)) if minor < 13 => true
    case _                              => false
  }

}
