import sbt._

object util {

  def isScala3(scalaVersion: String): Boolean =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) => true
      case _            => false
    }

}
