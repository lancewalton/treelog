resolvers += Resolver.typesafeRepo("releases")

resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"

addSbtPlugin("com.github.gseitz"         % "sbt-release"  % "1.0.13")
addSbtPlugin("com.github.sbt"            % "sbt-pgp"      % "2.1.2")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"  % "0.6.1")
addSbtPlugin("com.typesafe.sbt"          % "sbt-ghpages"  % "0.6.3")
addSbtPlugin("com.typesafe.sbt"          % "sbt-site"     % "1.4.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.20")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt" % "2.4.2")
