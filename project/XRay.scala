import sbt.Configurations.CompilerPlugin
import sbt.Keys._
import sbt._

object XRay extends Build {
  lazy val main = Project("sxr", file(".")) settings(
    name := "sxr",
    organization in ThisBuild := "org.scala-sbt.sxr",
    version in ThisBuild := "0.3.3-SCASTIE-SNAPSHOT",
    scalaVersion in ThisBuild := "2.11.1",
    scalacOptions += "-deprecation",
    ivyConfigurations += js,
    exportJars := true,
    libraryDependencies ++= dependencies,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    jqueryAll := target.value / "jquery-all.js",
    combineJs := combineJquery(update.value, jqueryAll.value, streams.value.log),
    resourceGenerators in Compile <+= combineJs
    )

  lazy val test = project.dependsOn(main % CompilerPlugin).settings(testProjectSettings: _*)

  lazy val testLink = project.dependsOn(main % CompilerPlugin, test).settings(testProjectSettings: _*).settings(
    scalacOptions += {
      val _ = clean.value
      val linkFile = target.value / "links"
      val testLinkFile = classDirectory.in(test, Compile).value.getParentFile / "classes.sxr"
      IO.write(linkFile, testLinkFile.toURI.toURL.toExternalForm)
      s"-P:sxr:link-file:$linkFile"
    }
  )

  def testProjectSettings = Seq(
    autoCompilerPlugins := true,
    libraryDependencies := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 =>
          libraryDependencies.value :+ "org.scala-lang.modules" %% "scala-xml" % "1.0.1"
        case _ =>
          libraryDependencies.value
      }
    },
    Keys.testOnly := {
      import sbt.complete.DefaultParsers._
      val base = baseDirectory.value
      val args: Seq[String] = spaceDelimited("<source file name>").parsed
      val oldFiles = args.map(file => (base / file) -> IO.read(base / file))
      val s = state.value
//      oldFiles.foreach { case (file, _) => IO.append(file, " ")}
//      println("append")
      val analysis = Project.extract(s).runTask(compile in Compile, s)
//      println(analysis)
//      oldFiles.foreach { case (file, content) => IO.write(file, content)}
//      println("revert")
      val out = (classDirectory in Compile).value
      checkOutput(out / "../classes.sxr", base / "expected", streams.value.log, oldFiles.map(_._1.getName).toSet)
    },
    Keys.test := {
      val __ = clean.value
      val _ = (compile in Compile).value
      val out = (classDirectory in Compile).value
      val base = baseDirectory.value
      checkOutput(out / "../classes.sxr", base / "expected", streams.value.log)
    }
  )

  val js = config("js").hide

  val combineJs = TaskKey[Seq[File]]("combine-js")
  val jqueryAll = SettingKey[File]("jquery-all")

  val jquery_version = "1.3.2"
  val jquery_scrollto_version = "1.4.2"
  val jquery_qtip_version = "1.0.0-rc3"

  def dependencies = Seq(
    "jquery" % "jquery" % jquery_version % "js->default" from ("http://jqueryjs.googlecode.com/files/jquery-" + jquery_version + ".min.js"),
    "jquery" % "jquery-scrollto" % jquery_scrollto_version % "js->default" from ("http://flesler-plugins.googlecode.com/files/jquery.scrollTo-" + jquery_scrollto_version + "-min.js"),
    "jquery" % "jquery-qtip" % jquery_qtip_version % "js->default" from ("http://craigsworks.com/projects/qtip/packages/1.0.0-rc3/jquery.qtip-" + jquery_qtip_version + ".min.js")
  )

  def combineJquery(report: UpdateReport, jsOut: File, log: Logger): Seq[File] = {
    IO.delete(jsOut)
    inputs(report) foreach { in => appendJs(in, jsOut)}
    log.info("Wrote combined js to " + jsOut.getAbsolutePath)
    Seq(jsOut)
  }

  def inputs(report: UpdateReport) = report.select(configurationFilter(js.name)) sortBy {
    _.name
  }

  def appendJs(js: File, to: File): Unit =
    Using.fileInputStream(js) { in =>
      Using.fileOutputStream(append = true)(to) { out => IO.transfer(in, out)}
    }


  def checkOutput(sxrDir: File, expectedDir: File, log: Logger, onlyFor: Set[String] = Set()) {
    val actual = filesToCompare(sxrDir)
    val expected = filesToCompare(expectedDir)
    val actualRelative = actual._2s
    val expectedRelative = expected._2s
    if (actualRelative != expectedRelative) {
      val actualOnly = actualRelative -- expectedRelative
      val expectedOnly = expectedRelative -- actualRelative
      def print(n: Iterable[String]): String = n.mkString("\n\t", "\n\t", "\n")
      log.error(s"Actual filenames not expected: ${print(actualOnly)}Expected filenames not present: ${print(expectedOnly)}")
      sys.error("Actual filenames differed from expected filenames.")
    }
    val different = actualRelative filter(actual => onlyFor.isEmpty || onlyFor(actual.replace(".html", ""))) filterNot { relativePath =>
      val actualFile = actual.reverse(relativePath).head
      val expectedFile = expected.reverse(relativePath).head
      val same = sameFile(actualFile, expectedFile)
      if (!same) log.error(s"$relativePath\n\t$actualFile\n\t$expectedFile")
      same
    }
    if (different.nonEmpty)
      sys.error("Actual content differed from expected content")
  }

  def filesToCompare(dir: File): Relation[File, String] = {
    val mappings = dir ** ("*.html" | "*.index") pair relativeTo(dir)
    Relation.empty ++ mappings
  }

  def sameFile(actualFile: File, expectedFile: File): Boolean =
    IO.read(actualFile) == IO.read(expectedFile)
}
