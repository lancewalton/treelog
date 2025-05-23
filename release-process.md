# Releasing

## Publishing

Releases are managed by [sbt-ci-release](https://github.com/sbt/sbt-ci-release), new releases are created by releasing on GitHub.

## Publish the Scaladoc

Publish the Scaladoc to gh-pages: sbt "doc makeSite ghpagesPushSite"

   SNAPSHOT versions have docs generated under /api/master, but release versions are under /api/treelog-x.y.z


