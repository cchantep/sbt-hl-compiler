package cchantep

import sbt._
import sbt.Keys._

import sbt.plugins.JvmPlugin

object HighlightExtractorPlugin extends AutoPlugin {
  import scala.collection.JavaConverters._
  import org.apache.commons.io.FileUtils
  import org.apache.commons.io.filefilter.{ IOFileFilter, TrueFileFilter }

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    sealed trait HLActivation

    case object HLEnabledByDefault extends HLActivation {
      override val toString = "<hl-enabled-by-default>"
    }

    case class HLEnabledBySysProp(propName: String) extends HLActivation {
      override val toString = s"<hl-enabled-by-system-property: $propName>"
    }

    /** Enabled by default, but disabled in case of system property. */
    case class HLDisabledBySysProp(propName: String) extends HLActivation {
      override val toString = s"<hl-disabled-by-system-property: $propName>"
    }

    val highlightStartToken = SettingKey[String]("highlightStartToken",
      """Token indicating a highlight starts; default: "```scala"""")

    val highlightEndToken = SettingKey[String]("highlightEndToken",
      """Token indicating a highlight is ended; default: "```"""")

    val highlightDirectory = SettingKey[File]("highlightDirectory",
      """Directory to be scanned; default: baseDirectory""")

    val highlightActivation = SettingKey[HLActivation]("highlightActivation",
      """Activation of the highlight compiler; default: activated""")
  }

  import autoImport._

  import SbtCompat.{ WatchSource => Src }

  val markdownSources = SettingKey[Seq[Src]]("highlightMarkdownSources")

  override def projectSettings = Seq(
    highlightStartToken := "```scala",
    highlightEndToken := "```",
    highlightDirectory := baseDirectory.value,
    highlightActivation := HLEnabledByDefault,
    includeFilter in doc := "*.md",
    mappings in (Test, packageBin) ~= {
      (_: Seq[(File, String)]).filter {
        case (_, target) => !target.startsWith("highlightextractor/")
      }
    },
    scalacOptions in (Test, doc) ++= activated(highlightActivation.value,
      Seq.empty[String]) {
      if (scalaBinaryVersion.value startsWith "2.") {
        Seq("-skip-packages", "highlightextractor")
      } else {
        Seq("-skip-by-id:highlightextractor")
      }
    },
    markdownSources := activated(highlightActivation.value, Seq.empty[Src]) {
      SbtCompat.markdownSources.value
    },
    watchSources in Test := activated(
      highlightActivation.value, watchSources.value) {
      markdownSources.value
    },
    sourceGenerators in Test += (Def.task {
      val log = streams.value.log
      val src = SbtCompat.markdownFiles.value
      val cacheDir = streams.value.cacheDirectory

      val st = (highlightStartToken in ThisBuild).
        or(highlightStartToken).value

      val et = (highlightEndToken in ThisBuild).
        or(highlightEndToken).value

      if (!autoScalaLibrary.value) {
        log.warn(s"Skip highlight extraction on non-Scala project: ${thisProject.value.id}")
        Seq.empty
      } else activated(highlightActivation.value, Seq.empty[File]) {
        val out = (sourceManaged in Test).value

        // Track token changes to invalidate cache when configuration changes
        val tokenFile = cacheDir / "highlight-extractor" / "tokens"
        val currentTokens = s"$st\n$et"
        val tokensChanged = !tokenFile.exists() ||
          IO.read(tokenFile) != currentTokens

        if (tokensChanged) {
          log.info("Token configuration changed, regenerating all files...")
          // Clean the cache FIRST to force regeneration
          IO.delete(cacheDir / "highlight-extractor")
          // THEN write the tokens file (after directory is recreated by IO.write)
          IO.write(tokenFile, currentTokens)
        }

        // Use Tracked.diffInputs for true per-file incremental compilation
        val srcSet = src.toSet
        
        Tracked.diffInputs(
          cacheDir / "highlight-extractor" / "inputs",
          FilesInfo.lastModified
        )(srcSet) { changeReport =>
          // Determine which files actually changed
          val changedFiles = changeReport.modified ++ changeReport.added

          if (changedFiles.nonEmpty) {
            log.info(s"Processing snippets from ${changedFiles.size} changed documentation file(s)")

            new HighlightExtractor(changedFiles.toSeq, out, st, et, log).apply()
          } else {
            // Return existing generated files
            (out ** "*.scala").get
          }
        }
      }
    }).taskValue
  )

  // ---

  private def activated[T](setting: HLActivation, default: T)(f: => T): T =
    setting match {
      case HLEnabledBySysProp(p) if sys.props.get(p).isDefined => f
      case HLDisabledBySysProp(p) if !sys.props.get(p).isDefined => f
      case HLEnabledByDefault => f
      case _ => default
    }

  private[cchantep] def listFiles(
    dir: File,
    includeFilter: FileFilter,
    excludeFilter: FileFilter
  ): Seq[File] = {
    val excludes = excludeFilter.accept(_)
    val iofilter = new IOFileFilter {
      def accept(f: File) = includeFilter.accept(f)
      def accept(d: File, n: String) = !excludes(d) && accept(d / n)
    }
    val dirfilter = new IOFileFilter {
      def accept(f: File) = !excludes(f)
      def accept(d: File, n: String) = accept(d / n)
    }

    FileUtils.listFiles(dir, iofilter, dirfilter).
      asScala.filterNot(excludes).toSeq
  }
}

final class HighlightExtractor(
  sources: Seq[File],
  out: File,
  startToken: String,
  endToken: String,
  log: Logger) {

  import java.io.PrintWriter

  private def generateFile(lines: Iterator[String], out: PrintWriter, ln: Long): (Iterator[String], Long) = if (!lines.hasNext) (lines -> ln) else {
    val line = lines.next()

    if (line == endToken) (lines -> (ln + 1)) else {
      out.println(line)
      out.flush()

      generateFile(lines, out, ln + 1)
    }
  }

  private def generate(out: File)(input: File, generated: Seq[File], samples: Seq[String], lines: Iterator[String], ln: Long, pkgi: Long): (Seq[File], Seq[String]) =
    if (!lines.hasNext) (generated -> samples) else {
      val line = lines.next()

      if (line contains startToken) {
        val n = generated.size
        val in = input.getName.replaceAll("\\.", "-")
        val sn = s"$in-$ln-$n.scala"
        val f = out / sn
        lazy val p = new PrintWriter(new java.io.FileOutputStream(f))
        val first = lines.next()
        val pkg = first startsWith "package "

        log.debug(s"Generating the sample #$n ($sn) ...")

        try {
          if (!pkg) p.println(
            s"package highlightextractor.samples$pkgi\n\ntrait Sample$n {")

          p.println(s"// File '${input.getAbsolutePath}', line ${ln + 1}\n")

          val (rem, no) = generateFile(Iterator(first) ++ lines, p, ln + 1L)

          if (!pkg) p.println("\n}")
          p.println(s"// end of sample #$n")

          p.flush()

          val sa = if (pkg) samples else samples :+ s"Sample$n"

          generate(out)(input, generated :+ f, sa, rem, no, pkgi)
        } finally { p.close() }
      } else generate(out)(input, generated, samples, lines, ln + 1, pkgi)
    }

  private def genPkg(out: File, i: Long, samples: Seq[String]): File = {
    val pkgf = out / s"package$i.scala"
    lazy val pkgout = new PrintWriter(new java.io.FileOutputStream(pkgf))

    try {
      pkgout.print(s"package highlightextractor\r\npackage object samples$i")

      samples.headOption.foreach { n =>
        pkgout.print(s"\n  extends $n")
      }

      samples.drop(1).foreach { n =>
        pkgout.print(s"\n  with $n")
      }

      pkgout.println(" { }")
      pkgout.flush()
    } finally {
      pkgout.close()
    }

    pkgf
  }

  def apply(): Seq[File] = {
    out.mkdirs()

    // Process each source file independently for better incremental compilation
    sources.flatMap { sourceFile =>
      log.info(s"Processing $sourceFile ...")

      // Use file-specific hash for stable package names across rebuilds
      val pi = Math.abs(sourceFile.hashCode)
      val lines = scala.io.Source.fromFile(sourceFile).getLines

      val (generated, samples) = generate(out)(
        sourceFile,
        Seq.empty[File],
        Nil,
        lines,
        1L,
        pi
      )

      // Only generate package object if there are samples
      val pkgFile = if (samples.nonEmpty) {
        Seq(genPkg(out, pi, samples))
      } else {
        Seq.empty[File]
      }

      generated ++ pkgFile
    }
  }
}
