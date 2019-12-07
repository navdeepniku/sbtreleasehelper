# sbt-release-helper
This sbt plugin adds functionality to sbt-release plugin to create release notes from Jira.

The following environment variables should be configured

`JIRA_ENDPOINT` jira search endpoint eg. _https://jira.your-org.com/rest/api/2/search_

`JIRA_TOKEN` Base64 encoded string eg. _Basic \<base64 encoded string value for username:password>_

`JIRA_PROJECT` Jira project ID

`PROJECT_VERSION_PREFIX` Prefix for jira Fix Version tag
