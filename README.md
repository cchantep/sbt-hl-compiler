# SBT highlight compiler

This SBT plugin extracts the Scala examples from a markdown/jekyllrb documentation, and compile the extracted codes, to keep the documentation valid.

## Motivation

Considering a documentation using a markdown format with some Scala code samples as following.

    This is a markdown documentation with a code sample.
    
    ```scala
    val foo = List("Bar", "Lorem", "Ipsum")
    ```

Using this SBT plugin, the code can be extracted in a generated `.scala` file, and then compiled to be sure it's valid.

## Get started

This plugin requires SBT 0.13+ (tested with SBT 0.13.11 and 1.3.4) and supports Scala 2.10 and 2.12.

**Note:** This is an auto-plugin that automatically enables itself for all projects with the JVM plugin. Extracted code is compiled as **Test** sources, so any dependencies needed by your examples should be in the `Test` scope.

You need to update the `project/plugins.sbt`.

```scala
resolvers ++= Seq(
  "Tatami Releases" at "https://raw.github.com/cchantep/tatami/master/releases")

addSbtPlugin("cchantep" % "sbt-hl-compiler" % "0.9")
```

By default, it will scan all the `*.md` files in the base directory.

The following options are available.

```scala
highlightStartToken := "{% highlight scala %}"
// Token to detect the start of a Scala code sample.
// Can be defined in the scope ThisBuild
// Default: "```scala"

highlightEndToken := "{% endhighlight %}"
// Token to detect the end of a Scala code sample.
// Default: "```"

highlightDirectory := baseDirectory.value / "doc"
// The directory scanned for the documentation files.
// Default: project base directory

includeFilter in doc := "*.ext"
// Filter for files to include in the scan.
// Default: "*.md"

excludeFilter in doc := "_excludes"
// Filter for files to exclude from the scan.
// Default: undefined

import cchantep.HighlightExtractorPlugin.autoImport._

highlightActivation := HLEnabledByDefault
// Control when the plugin is active:
// - HLEnabledByDefault: Always enabled (default)
// - HLEnabledBySysProp("propName"): Only enabled if system property is set
// - HLDisabledBySysProp("propName"): Enabled unless system property is set
// Can be defined in the scope ThisBuild
```

### Conditional Activation

You can control plugin activation using system properties:

```scala
// Enable only when a system property is set
highlightActivation := HLEnabledBySysProp("enable.doc.compile")
// Run with: sbt -Denable.doc.compile=true test

// Disable when a system property is set (useful for CI)
highlightActivation := HLDisabledBySysProp("skip.doc.compile")
// Run with: sbt -Dskip.doc.compile=true test
```

This plugin also append the `highlightDirectory` to the `watchSources` settings, so a documentation build is triggered each time a source document is updated.

*See a SBT build [using this plugin](https://github.com/ReactiveMongo/reactivemongo-site/blob/gh-pages/build.sbt).*

**In case of SBT [Multi-Project](https://www.scala-sbt.org/1.x/docs/Multi-Project.html)**

It may be required to disable this plugin on the aggregation if some examples in files of the sub-project require some dependencies specific to the sub-project (not available at the aggregation time).

```scala
lazy val root = Project(id = "...", base = file(".")).
  aggregate(/* ... */).
  disablePlugins(HighlightExtractorPlugin)
```

## Extraction behaviour

### Generated Files

Extracted code samples are generated in `target/scala-<version>/src_managed/test/` and compiled as Test sources. Generated files are named using the pattern `<markdown-filename>-<line-number>-<index>.scala`.

The plugin also:

- Automatically excludes the `highlightextractor` package from Scaladoc generation
- Adds the highlight directory to `watchSources` (Test scope), triggering recompilation when documentation changes
- Filters out generated classes from the Test package binary

### Code Wrapping

If a code sample doesn't start with a `package ...` statement, the `.scala` generated file wraps this code in a `trait` with a unique name.

Considering a documentation as following.

    This is a markdown documentation with a code sample.
        
    ```scala
    val foo = List("Bar", "Lorem", "Ipsum")
    ```

The code in the generated file will be:

```scala
package samplesY

trait SampleX {
// File '/path/to/the/file.md', line N

val foo = List("Bar", "Lorem", "Ipsum")

}
```

All the code samples without a `package` in the same documentation file are gathered in the same generated package (`samplesY` in the previous example). The plugin also generates a package object that extends/mixes in all the sample traits:

```scala
package highlightextractor

package object samplesY
  extends Sample0
  with Sample1 { }
```

This allows sharing definitions across code samples within a documentation file.

    This is how to create a value:
    
    ```scala
    val value = createIt()
    ```
    
    Then you can use it.
    
    ```scala
    value.foo()
    ```

## Build

This is built using SBT.

    sbt '^ publishLocal'

[![Build Status](https://travis-ci.org/cchantep/sbt-hl-compiler.svg?branch=master)](https://travis-ci.org/cchantep/sbt-hl-compiler)
