resolvers += Resolver.typesafeRepo("releases")

resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"

addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.13")
addSbtPlugin("com.github.sbt"    % "sbt-pgp"      % "2.2.1")
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"  % "0.6.4")
addSbtPlugin("com.typesafe.sbt"  % "sbt-ghpages"  % "0.6.3")
addSbtPlugin("com.typesafe.sbt"  % "sbt-site"     % "1.4.1")
addSbtPlugin("org.typelevel"     % "sbt-tpolecat" % "0.5.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.5.0")
