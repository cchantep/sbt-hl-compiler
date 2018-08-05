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

This plugin requires SBT 0.13+.

You need to update the `project/plugins.sbt`.

```scala
resolvers ++= Seq(
  "Tatami Releases" at "https://raw.github.com/cchantep/tatami/master/releases")

addSbtPlugin("cchantep" % "sbt-hl-compiler" % "0.6")
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
// Default: "*.md"

excludeFilter in doc := "_excludes"
// Default: undefined
```

This plugin also append the `highlightDirectory` to the `watchSources` settings, so a documentation build is triggered each time a source document is updated.

*See a SBT build [using this plugin](https://github.com/ReactiveMongo/reactivemongo-site/blob/gh-pages/build.sbt).*

## Extraction behaviour

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

All the code samples without a `package` in the same documentation files are also gather in a same generated package, `samplesY` in the previous example.

It allows to share some definitions accross the code samples of a documentation file.

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
