sbtPlugin := true

name := "sbt-hl-compiler"

organization := "cchantep"

version := "0.12"

addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0")

scalaVersion := "2.12.21"

crossScalaVersions := Vector("2.12.21", "3.9.0")

pluginCrossBuild / sbtVersion := {
  if (scalaBinaryVersion.value == "2.12") "1.12.3" else "2.1.0"
}

scalacOptions ++= {
  if (scalaBinaryVersion.value == "2.12") Seq("-Xsource:3", "-release:8") else Nil
}

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.22.0",
  "org.specs2" %% "specs2-core" % "4.23.0" % Test
)

ThisBuild / publishTo := sys.env.get("REPO_PATH").map { path =>
  import Resolver.ivyStylePatterns

  val repoDir = new java.io.File(path)

  Resolver.file("repo", repoDir)
}
