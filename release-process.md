Multiple Scalaz Versions Supported
----------------------------------
Master tracks the latest version of scalaz, branches track previous minor versions. eg. master scalaz 7.3, branches scalaz-7.2 and scalaz-7.1.  

When scalaz's minor version changes, create a new branch for the old version.

Master is the master, merge into the other branches to maintain docs and compatibility.

Publish the Scaladoc
--------------------
Publish the scaladoc to gh-pages: sbt "doc makeSite ghpagesPushSite"

   SNAPSHOT versions have docs generated under /api/master, but release versions are under /api/treelog-x.y.z

Publish Snapshots to Sonatype
-----------------------------
  For snapshots make sure the version number has a -SNAPSHOT suffix. To release to staging, remove the suffix.
  In both cases use 'sbt publishSigned'

Release to Sonatype
-------------------
  1. Add changes to the [release notes](release_notes.md).
  1. (See section 8a https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-7b.StageExistingArtifacts)
  1. Do a staging release by removing the -SNAPSHOT in the version number and publish-signed
  1.	Login to the Nexus UI at https://oss.sonatype.org/
  1. Under 'Build Promotion' select Staging Repositories.
  1.	Find our release item and click it
  1. Click the Close button (next to the Refresh button)
  1. Wait ...
  1. Keep checking the Activity tab to see if any rules failed.
  1. If that succeeds, select the release again and click 'Release'
  1. Wait ...
  1. Keep checking the Activity tab to see if there are any problems
  1. Look for the release in https://oss.sonatype.org/content/repositories/releases/com/casualmiracles/treelog_2.11
  1. Bump the release version up with -SNAPSHOT extension
