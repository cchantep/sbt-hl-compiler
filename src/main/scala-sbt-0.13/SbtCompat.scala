package cchantep

import sbt._
import sbt.Keys._

private[cchantep] object SbtCompat {
  type WatchSource = File

  import HighlightExtractorPlugin.autoImport.highlightDirectory

  val markdownFiles = Def.setting[Seq[File]] {
    val base = highlightDirectory.value
    val incFilter = (includeFilter in doc).value
    val excFilter = (excludeFilter in doc).value

    HighlightExtractorPlugin.listFiles(base, incFilter, excFilter)
  }

  val markdownSources = markdownFiles
}
