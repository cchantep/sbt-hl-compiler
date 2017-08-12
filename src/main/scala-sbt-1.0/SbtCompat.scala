package cchantep

import sbt._
import sbt.Keys._

import sbt.internal.io.Source

private[cchantep] object SbtCompat {
  type WatchSource = Source

  import HighlightExtractorPlugin.autoImport.highlightDirectory

  val markdownSources = Def.setting[Seq[WatchSource]] {
    val base = highlightDirectory.value
    val incFilter = (includeFilter in doc).value
    val excFilter = (excludeFilter in doc).value

    Seq(new Source(base, incFilter, excFilter))
  }

  val markdownFiles = Def.setting[Seq[File]] {
    val base = highlightDirectory.value
    val incFilter = (includeFilter in doc).value
    val excFilter = (excludeFilter in doc).value

    HighlightExtractorPlugin.listFiles(base, incFilter, excFilter)
  }
}
