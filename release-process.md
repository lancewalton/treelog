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

Add your sonatype credentials to ~/.sbt/sonatype_credentials

    host=oss.sonatype.org
    user=username
    password=password

1. run: sbt release release-version <release version> next-version <next release, usually XXX-SNAPSHOT>
2. (See section 8a https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-7b.StageExistingArtifacts)
3. Login to the Nexus UI at https://oss.sonatype.org/
4. Under 'Build Promotion' select Staging Repositories.
5. Find our release item and click it
6. Click the Close button (next to the Refresh button)
7. Wait ...
8. Keep checking the Activity tab to see if any rules failed.
9. If that succeeds, select the release again and click 'Release'
10. Wait ...
11. Keep checking the Activity tab to see if there are any problems
12. Look for the release in https://oss.sonatype.org/content/repositories/releases/com/casualmiracles/
13. Create a release in github with the version and link to differences.
