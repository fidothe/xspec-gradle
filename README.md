# xspec-gradle
A Gradle plugin for executing XSpec tests

This is very much Alpha software.

I'm trying to get my head around the process of developing a gradle plugin 
outside of `buildSrc`, and how that's built and deployed, as well as how to 
actually run XSpec tests and report the results.

There's a lot to do, for goodness sake don't use this in a real project yet.

## Usage

The idea is to make it trivial to include an XSpec runner in your project. Your 
`build.gradle.kts` would look like this:

```kotlin
repositories {
  mavenCentral()
}
plugins {
  id("io.werkstatt.xspec.gradle-plugin")
}

dependencies {
  xspec("net.sf.saxon:Saxon-HE:12.9") // Could be PE or EE if you want
}

xspec {
  xspecDir = layout.projectDirectory.dir("xspec")
  srcDir = layout.projectDirectory.dir("src")
}
```

```shell
$ ./gradlew xspec
```

It will also, eventually, be wired up so that the `check` task runs it too.

## TODO
Too much to list at this point. This big questions are philosophical: How do 
people structure their projects, and how should that be reflected in configuring
the plugin? How should we deal with configuring XSLT / XQuery / Schematron XSpec
specs? There are many mundane questions like test reporting and CI integration 
approaches too.