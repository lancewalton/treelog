resolvers += Resolver.typesafeRepo("releases")

resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"

addSbtPlugin("com.github.sbt"   % "sbt-release"  % "1.4.0")
addSbtPlugin("com.github.sbt"   % "sbt-pgp"      % "2.2.1")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"  % "0.6.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages"  % "0.6.3")
addSbtPlugin("com.github.sbt"   % "sbt-site"     % "1.6.0")
addSbtPlugin("org.typelevel"    % "sbt-tpolecat" % "0.5.0")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt" % "2.5.2")
