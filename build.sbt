import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import ReleaseTransformations._

def scala213 = "2.13.0"
def scala212 = "2.12.9"

lazy val commonSettings = Seq(
  organization := "io.bullet",
  homepage := Some(new URL("https://github.com/sirthias/borer/")),
  description := "CBOR and JSON (de)serialization in Scala",
  startYear := Some(2019),
  licenses := Seq("MPLv2" → new URL("https://www.mozilla.org/en-US/MPL/2.0/")),
  unmanagedResources in Compile += baseDirectory.value.getParentFile.getParentFile / "LICENSE",
  scmInfo := Some(ScmInfo(url("https://github.com/sirthias/borer/"), "scm:git:git@github.com:sirthias/borer.git")),

  scalaVersion := scala213,
  crossScalaVersions := Seq(scala212, scala213),

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:_",
    "-unchecked",
    "-target:jvm-1.8",
    "-Xlint:_,-missing-interpolator",
    "-Xfatal-warnings",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ybackend-parallelism", "8",
    "-Ywarn-unused:imports,-patvars,-privates,-locals,-implicits,-explicits",
    "-Ycache-macro-class-loader:last-modified",
  ),// ++ (if (sys.props("java.version") startsWith "1." /* i.e. Java version < 9 */) Nil else Seq("-release", "8")),
  
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => Seq(
        "-Yno-adapted-args",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Xfuture",
        "-Xsource:2.13",
      )
      case Some((2, 13)) => Nil
      case x => sys.error(s"unsupported scala version: $x")
    }
  },

  scalacOptions in (Compile, console) ~= (_ filterNot(o => o.contains("warn") || o.contains("Xlint"))),
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
  scalacOptions in (Compile, doc) += "-no-link-warnings",
  sourcesInBase := false,

  // file headers
  headerLicense := Some(HeaderLicense.MPLv2("2019", "Mathias Doenitz")),

  // reformat main and test sources on compile
  scalafmtOnCompile := true,

  testFrameworks += new TestFramework("utest.runner.Framework"),
  initialCommands in console := """import io.bullet.borer._""",

  // publishing
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ ⇒
    false
  },
  publishTo := sonatypePublishTo.value,
  developers := List(
    Developer("sirthias", "Mathias Doenitz", "devnull@bullet.io", url("https://github.com/sirthias/"))
  ),

  // test coverage
  coverageMinimum := 90,
  coverageFailOnMinimum := false,
  coverageExcludedPackages := """ui\.bullet\.borer\.benchmarks\..*""",

  commands += Command.command("openCoverageReport") { state =>
    val uri = s"file://${Project.extract(state).get(crossTarget)}/scoverage-report/index.html"
    state.log.info(s"Opening browser at $uri ...")
    java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
    state
  },

  commands += Command.command("testCoverage") { state =>
    val cmds = List("clean", "coverage", "test", "coverageReport", "openCoverageReport").map(Exec(_, None))
    state.copy(remainingCommands = cmds ::: state.remainingCommands)
  }
)

lazy val crossSettings = Seq(
  sourceDirectories in (Compile, scalafmt) := (unmanagedSourceDirectories in Compile).value,
  sourceDirectories in (Test, scalafmt) := (unmanagedSourceDirectories in Test).value
)

lazy val scalajsSettings = Seq(
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule).withSourceMap(false)),
  scalaJSStage in Global := FastOptStage,
  scalacOptions ~= { _.filterNot(_ == "-Ywarn-dead-code") :+ "-P:scalajs:sjsDefinedByDefault" }
)

lazy val releaseSettings = {
  val runCompile = ReleaseStep(action = { st: State ⇒
    val extracted = Project.extract(st)
    val ref       = extracted.get(thisProjectRef)
    extracted.runAggregated(compile in Compile in ref, st)
  })

  Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runCompile,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    )
  )
}

lazy val macroParadise =
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch))
      case _ => Nil
    }
  }

/////////////////////// DEPENDENCIES /////////////////////////

val `collection-compat` = Def.setting("org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.1")
val `akka-actor`        = Def.setting("com.typesafe.akka"      %%% "akka-actor"              % "2.5.23")
val `scodec-bits`       = Def.setting("org.scodec"             %%% "scodec-bits"             % "1.1.12")
val utest               = Def.setting("com.lihaoyi"            %%% "utest"                   % "0.7.1"            % "test")
val `scala-compiler`    = Def.setting("org.scala-lang"         %  "scala-compiler"           % scalaVersion.value % "provided")
val `scala-reflect`     = Def.setting("org.scala-lang"         %  "scala-reflect"            % scalaVersion.value % "provided")

/////////////////////// PROJECTS /////////////////////////

lazy val borer = project.in(file("."))
  .aggregate(coreJVM, coreJS)
  .aggregate(akka)
  .aggregate(scodecJVM, scodecJS)
  .aggregate(derivationJVM, derivationJS)
  .aggregate(benchmarks)
  .aggregate(site)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(publish / skip := true)

lazy val coreJVM = core.jvm.enablePlugins(SpecializeJsonParserPlugin)
lazy val coreJS  = core.js
lazy val core = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .enablePlugins(AutomateHeaderPlugin, BoilerplatePlugin)
  .settings(crossSettings)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "borer-core",
    macroParadise,
    libraryDependencies ++= Seq(`collection-compat`.value, `scala-reflect`.value, utest.value),

    // point sbt-boilerplate to the common "project"
    Compile / boilerplateSource := baseDirectory.value.getParentFile / "src" / "main" / "boilerplate",
    Compile / sourceManaged := baseDirectory.value.getParentFile / "target" / "scala" / "src_managed" / "main"
  )
  .jvmSettings(
    Compile / specializeJsonParser / sourceDirectory := baseDirectory.value.getParentFile / "src" / "main",
    Compile / specializeJsonParser / sourceManaged := baseDirectory.value / "target" / "scala" / "src_managed" / "main",
    Compile / managedSourceDirectories += (Compile / specializeJsonParser / sourceManaged).value
  )
  .jsSettings(scalajsSettings: _*)

lazy val akka = project
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(coreJVM % "compile->compile;test->test")
  .dependsOn(derivationJVM % "test->compile")
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "borer-compat-akka",
    libraryDependencies ++= Seq(`akka-actor`.value, utest.value)
  )

lazy val scodecJVM = scodec.jvm
  .dependsOn(coreJVM % "compile->compile;test->test")
  .dependsOn(derivationJVM % "test->compile")
lazy val scodecJS  = scodec.js
  .dependsOn(coreJS   % "compile->compile;test->test")
  .dependsOn(derivationJS % "test->compile")
lazy val scodec = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(crossSettings)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "borer-compat-scodec",
    libraryDependencies ++= Seq(`scodec-bits`.value, utest.value)
  )
  .jsSettings(scalajsSettings: _*)

lazy val derivationJVM = derivation.jvm.dependsOn(coreJVM % "compile->compile;test->test")
lazy val derivationJS  = derivation.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val derivation = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(crossSettings)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "borer-derivation",
    libraryDependencies ++= Seq(`scala-compiler`.value, `scala-reflect`.value, utest.value),
  )
  .jsSettings(scalajsSettings: _*)

lazy val benchmarks = project
  .enablePlugins(AutomateHeaderPlugin, JmhPlugin, BenchmarkResultsPlugin)
  .dependsOn(coreJVM, derivationJVM)
  .settings(commonSettings)
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "0.52.2",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "0.52.2" % Provided,
      "com.fasterxml.jackson.module"          %% "jackson-module-scala"  % "2.9.9",
      "com.lihaoyi"                           %% "upickle"               % "0.7.5",
      "io.circe"                              %% "circe-core"            % "0.12.0-M4",
      "io.circe"                              %% "circe-derivation"      % "0.12.0-M4",
      "io.circe"                              %% "circe-jawn"            % "0.12.0-M4",
      "io.spray"                              %% "spray-json"            % "1.3.5",
    )
  )

lazy val site = project
  .in(file("site"))
  .dependsOn(coreJVM % "compile->compile;test->test", derivationJVM, akka, scodecJVM)
  .enablePlugins(
    ParadoxPlugin,
    ParadoxMaterialThemePlugin,
    ParadoxSitePlugin,
    GhpagesPlugin
  )
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(utest.value),

    com.typesafe.sbt.SbtGit.GitKeys.gitRemoteRepo := scmInfo.value.get.connection.drop("scm:git:".length),
    ghpagesNoJekyll := true,

    ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Compile),
    Compile / paradoxMaterialTheme := {
      ParadoxMaterialTheme()
        .withFavicon("assets/images/favicon.ico")
        .withColor("indigo", "orange")
        .withLogo("assets/images/borer-logo-white.svg")
        .withCustomStylesheet("assets/stylesheets/borer.css")
        .withCopyright("Copyright (C) 2019 Mathias Doenitz")
        .withRepository(scmInfo.value.get.browseUrl.toURI)
        .withSocial(uri("https://github.com/sirthias"), uri("https://twitter.com/sirthias"))
        .withSearch()
    },

    commands += Command.command("openSite") { state =>
      val uri = s"file://${Project.extract(state).get(Compile / paradox / target)}/index.html"
      state.log.info(s"Opening browser at $uri ...")
      java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
      state
    },

    Compile / paradox / version := "0.11.1",

    paradoxProperties ++= Map(
      "image.base_url" -> ".../assets/images",
      "github.base_url" -> {
        val v = version.value
        s"https://github.com/sirthias/borer/tree/${if (v.endsWith("SNAPSHOT")) "master" else "v" + v}"
      },
      "extref.rfc.base_url" -> "http://tools.ietf.org/html/rfc%s",
      "snip.test.base_dir" -> s"${(Test / sourceDirectory).value}/scala/io/bullet/borer/site",
      "snip.core.base_dir" -> s"${baseDirectory.value}/../core/src/main/scala/io/bullet/borer",
    )
  )