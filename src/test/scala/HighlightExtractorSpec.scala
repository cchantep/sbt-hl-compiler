package cchantep

import java.io.File
import java.nio.file.Files

import sbt._
import sbt.io.IO

final class HighlightExtractorPluginSpec extends org.specs2.mutable.Specification {
  "Highlight extractor plugin".title

  import HighlightExtractorPlugin._
  import HighlightExtractorPlugin.autoImport._

  "activated" should {

    "execute block when EnabledByDefault" in {
      val res =
        activated(HLEnabledByDefault, "default") {
          "active"
       }

      res must_=== "active"
    }

    "execute block when sysprop is defined (EnabledBySysProp)" in {
      sys.props += ("test.prop1" -> "true")

      val res =
        activated(HLEnabledBySysProp("test.prop1"), "default") {
          "active"
        }

      sys.props -= "test.prop1"

      res must_=== "active"
    }

    "return default when sysprop is missing (EnabledBySysProp)" in {
      val res =
        activated(HLEnabledBySysProp("missing.prop1"), "default") {
          "active"
        }

      res must_=== "default"
    }

    "return default when HLDisabledBySysProp and sysprop is absent" in {
      val res =
        activated(HLDisabledBySysProp("missing.prop2"), "default") {
          "active"
        }

      res must_=== "active"
    }

    "return default when HLDisabledBySysProp and sysprop exists" in {
      sys.props += ("test.prop2" -> "true")

      val res =
        activated(HLDisabledBySysProp("test.prop2"), "default") {
          "active"
        }

      sys.props -= "test.prop2"

      res must_=== "default"
    }
  }

  "listFiles" should {

    "list only included files and exclude filtered ones" in {
      val dir = Files.createTempDirectory("hl-test").toFile
      dir.deleteOnExit()

      val keep = new File(dir, "keep.txt")
      val drop = new File(dir, "drop.txt")

      IO.write(keep, "a")
      IO.write(drop, "b")

      val include = new FileFilter {
        def accept(f: File) = f.getName.endsWith(".txt")
      }

      val exclude = new FileFilter {
        def accept(f: File) = f.getName == "drop.txt"
      }

      val res = listFiles(dir, include, exclude)

      res.map(_.getName) must contain("keep.txt") and {
        res.map(_.getName) must not contain("drop.txt")
      }
    }
  }

  "HighlightExtractor" should {

    "generate scala file from markdown highlight block" in {
      val dir = Files.createTempDirectory("hl-out").toFile
      val src = Files.createTempFile("hl-src", ".md").toFile

      val content =
        """text
          |```scala
          |package test
          |
          |object A {}
          |```
          |more text
          |""".stripMargin

      IO.write(src, content)

      val extractor =
        new HighlightExtractor(
          sources = Seq(src),
          out = dir,
          startToken = "```scala",
          endToken = "```",
          log = sbt.ConsoleLogger()
        )

      val generated = extractor()

      generated must not(beEmpty) and {
        generated.exists(_.getName.endsWith(".scala")) must beTrue
      }
    }
  }
}
