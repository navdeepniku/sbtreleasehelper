package sbtreleasehelper

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.{DefaultHttpRequestRetryHandler, HttpClients}
import org.apache.http.util.EntityUtils
import sbt.Keys.{baseDirectory, version}
import sbt.{AutoPlugin, File, IO, Project, State}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseVcs, releaseVcsSign, releaseVcsSignOff}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.sys.process.ProcessLogger

object ReleaseHelper extends AutoPlugin {

  object autoImport extends scala.AnyRef {

    // Release notes step
    lazy val writeReleaseNotes: ReleaseStep = { st: State =>
      val releaseNotesObj = new ReleaseNotesFromJira(st,
        sys.env.getOrElse("PROJECT_VERSION_PREFIX", throw new IllegalStateException("Missing PROJECT_VERSION_PREFIX in ENV")))
      val changelog = releaseNotesObj.changelog
      // append to changelog file
      releaseNotesObj.writeReleaseNotes(changelog)
      // commit to vcs
      releaseNotesObj.commitReleaseNotes()
      // continue
      st.continue
    }

    class ReleaseNotesFromJira(st: State, versionPrefix: String) {

      val log: ProcessLogger = toProcessLogger(st)
      val baseDir: File = Project.extract(st).get(baseDirectory)
      val file: File = new File(baseDir, "CHANGELOG.md")
      val jiraAuthToken: String = sys.env.getOrElse("JIRA_TOKEN", throw new IllegalStateException("Missing JIRA_TOKEN in ENV"))
      // jira search endpoint eg. "https://jira.<org>.com/rest/api/2/search"
      val jiraEndpoint: String = sys.env.getOrElse("JIRA_ENDPOINT", throw new IllegalStateException("Missing JIRA_ENDPOINT in ENV"))
      val jiraProject: String = sys.env.getOrElse("JIRA_PROJECT", throw new IllegalStateException("Missing JIRA_PROJECT in ENV"))
      val runningVersion: String = Project.extract(st).get(version)
      val changelog: String = generateReleaseNotesFromJira

      /**
        * Gets list of issues from Jira with Fix-version (release tag) set as the current release version in this build
        */
      def generateReleaseNotesFromJira: String = {
        log.out(s"Getting issues from Jira for release version $runningVersion")

        val httpclient = HttpClients.custom().setRetryHandler(new DefaultHttpRequestRetryHandler()).build()
        try {

          val builder: URIBuilder = new URIBuilder(jiraEndpoint)
            .setParameter("jql", s"project = $jiraProject AND (status = DONE OR status = 'IN DEPLOYMENT' OR status = CLOSED) AND fixVersion = $versionPrefix-$runningVersion")
            .setParameter("fields", "summary,status")

          val httpGet: HttpGet = new HttpGet(builder.build())
          httpGet.setHeader("Authorization", jiraAuthToken)

          val jiraResponse: CloseableHttpResponse = httpclient.execute(httpGet)

          try {
            // check for 200 status else abort release
            if (jiraResponse.getStatusLine.getStatusCode == 200) {
              val jiraIssues = EntityUtils.toString(jiraResponse.getEntity)

              val mapper: ObjectMapper = new ObjectMapper()
              val jsonObj: JsonNode = mapper.readTree(jiraIssues)

              // validate if jira has atleast one issue for release notes
              if (jsonObj.get("issues").size().equals(0)) {
                sys.error(s"Abort release! Jira returned no issues for release version $runningVersion")
              }
              val notesHeader =
                s"""|## [$runningVersion] - ${java.time.LocalDate.now}""".stripMargin
              val changesHeader = """### Changed/Added"""
              val fixedHeader = """### Fixed"""
              val changes = new ListBuffer[String]()
              val fixes = new ListBuffer[String]()
              val releaseNotes = new ListBuffer[String]()

              jsonObj.get("issues").asScala.foreach(i => {
                val issueKey = i.get("key").toString.dropRight(1).drop(1)
                val issueSummary = i.get("fields").get("summary").toString.dropRight(1).drop(1)
                val issueStatus = i.get("fields").get("status").get("name").toString.dropRight(1).drop(1).toUpperCase()
                val notesTemplate = s"- [Ref. [$issueKey](https://${builder.getHost}/browse/$issueKey)] $issueSummary"
                if (issueStatus.toUpperCase equals "IN DEPLOYMENT") {
                  fixes += notesTemplate
                }
                else {
                  changes += notesTemplate
                }
              })

              releaseNotes.append(notesHeader)
              if (changes.nonEmpty) {
                releaseNotes.append(changesHeader + "\n" + changes.mkString("\n"))
              }
              if (fixes.nonEmpty) {
                releaseNotes.append(fixedHeader + "\n" + fixes.mkString("\n"))
              }

              releaseNotes.mkString("\n") + "\n"
            }
            else {
              sys.error(s"Abort release! Jira returned bad status. " +
                s"${jiraResponse.getStatusLine} ${EntityUtils.toString(jiraResponse.getEntity)}")
            }

          } finally {
            jiraResponse.close()
          }
        } finally httpclient.close()
      }

      def writeReleaseNotes(changeLines: String) {
        IO.writeLines(file, Seq(changeLines), append = true)
      }

      // commit to vcs
      def commitReleaseNotes() {
        val vcs = (st: State) => {
          Project.extract(st).get(releaseVcs).getOrElse(
            sys.error("Aborting release. Working directory is not a repository of a recognized VCS."))
        }

        val base = vcs(st).baseDir.getCanonicalFile
        val sign = Project.extract(st).get(releaseVcsSign)
        val signOff = Project.extract(st).get(releaseVcsSignOff)
        val relativePath = IO.relativize(base, file.getCanonicalFile).getOrElse(
          "Version file [%s] is outside of this VCS repository with base directory [%s]!" format(file, base))

        vcs(st).add(relativePath) !! log
        val status = (vcs(st).status !!) trim

        if (!status.isEmpty) {
          vcs(st).commit(s"[skip ci] Adding release notes for version $runningVersion", sign, signOff) ! log
        } else {
          // nothing to commit. this happens if the version.sbt file hasn't changed.
        }
      }

      def toProcessLogger(st: State): ProcessLogger = new ProcessLogger {
        override def err(s: => String): Unit = st.log.info(s)

        override def out(s: => String): Unit = st.log.info(s)

        override def buffer[T](f: => T): T = st.log.buffer(f)
      }
    }
  }
}
