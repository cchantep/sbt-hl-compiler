sbtPlugin := true

name := "sbt-hl-compiler"

organization := "cchantep"

version := "0.8"

crossSbtVersions := Vector("0.13.11", "1.3.4")

libraryDependencies += "commons-io" % "commons-io" % "2.19.0"

publishTo in ThisBuild := sys.env.get("REPO_PATH").map { path =>
  import Resolver.ivyStylePatterns

  val repoDir = new java.io.File(path)

  Resolver.file("repo", repoDir)
}
