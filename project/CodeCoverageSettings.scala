import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {
  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    ".*.Module",
    ".*.model.*",
    ".*.config.*",
    "uk.gov.hmrc.BuildInfo",
    "app.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*",
    ".*handlers.*",
    ".*components.*",
    ".*viewmodels.govuk.*",
    "controllers.LanguageSwitchController",
    "models.UserAnswers",
    "pages.*",
    "queries.*",
    "repositories.*",
    "views.ViewUtils",
    "models.*",
    "forms.mappings.Formatters",
    "views.html.*",
    "views.*"
  )

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
