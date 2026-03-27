package io.werkstatt.xspec.xspec_gradle

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.assign
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.nameWithoutExtension


interface XspecExtension {
  val xspecDir: DirectoryProperty
  val xspecBuildDir: DirectoryProperty
  val xspecHome: DirectoryProperty
  val suiteFiles: ConfigurableFileCollection
}

abstract class XspecImplCopyZipTask : DefaultTask() {
  companion object {
    fun xspecZipName(version: Provider<String>): String {
      return "${xspecFolderName(version)}.zip"
    }

    fun xspecFolderName(version: Provider<String>): String {
      return "xspec-${version.get()}"
    }
  }

  @get:Input
  abstract val xspecVersion: Property<String>

  @get:Internal
  abstract val xspecUnpackDir: DirectoryProperty

  @get:OutputFile
  val xspecZip: Provider<RegularFile> = xspecUnpackDir.map { it.file(xspecZipName(xspecVersion)) }

  @TaskAction
  fun extractXspecZip() {
    val zipStream = this::class.java.getResourceAsStream(xspecZipName(xspecVersion))

    Files.copy(zipStream, xspecZip.get().asFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
  }
}

abstract class XspecImplCopyTask @Inject constructor(private val archive: ArchiveOperations, private val fs: FileSystemOperations) : DefaultTask() {
  @get:Input
  abstract val xspecVersion: Property<String>

  @get:InputFile
  abstract val xspecZip: RegularFileProperty

  @get:Internal
  abstract val xspecUnpackDir: DirectoryProperty

  @get:OutputDirectory
  val xspecHome = xspecUnpackDir.map { it.dir(XspecImplCopyZipTask.xspecFolderName(xspecVersion)) }

  @TaskAction
  fun copyXspecImpl() {
    val xspecFolderName = XspecImplCopyZipTask.xspecFolderName(xspecVersion)
    val inclusions = listOf("java", "src", "graphics")

    fs.copy {
      into(xspecUnpackDir)
      from(archive.zipTree(xspecZip)) {
        inclusions.forEach {
          include("${xspecFolderName}/${it}/**/*")
        }
      }
    }
  }
}

abstract class XspecBaseTask : DefaultTask() {
  fun suffixedFile(dir: Directory, specPath: String, suffix: String): File {
    val specFullPath = dir.file(specPath).asFile.toPath()
    val newName = "${specFullPath.nameWithoutExtension}-${suffix}"
    return specFullPath.resolveSibling(newName).toFile()
  }

  fun compiledFile(dir: Directory, specPath: String): File {
    return suffixedFile(dir, specPath, "compiled.xsl")
  }

  fun compiledFileTo(compiledFile: File, suffix: String): File {
    return compiledFile.parentFile.toPath().resolve("${compiledFile.nameWithoutExtension}-suffix").toFile()
  }

  @get:InputFiles
  abstract val classpath: ConfigurableFileCollection

  @get:Internal
  abstract val xspecDir: DirectoryProperty

  @get:Internal
  abstract val xspecBuildDir: DirectoryProperty

  @get:Input
  abstract val suiteSpecPaths: ListProperty<String>

  @get:InputDirectory
  abstract val xspecHome: DirectoryProperty

  fun baseJavaOptions(xspecFile: File): List<String> {
    return listOf(
      "-Dxspec.coverage.ignore=\"${xspecDir.get().asFile}\"",
      "-Dxspec.home=\"${xspecHome.get().asFile}\"",
      "-Dxspec.xspecfile=\"${xspecFile}\""
    )
  }
}

abstract class XspecCompilerTask @Inject constructor(private val ex: ExecOperations) : XspecBaseTask() {
  @get:InputFiles
  val suiteFiles: FileCollection = project.objects.fileCollection().from(xspecDir.map { dir ->
    suiteSpecPaths.get().map { p -> dir.file(p) }
  })

  @get:OutputFiles
  val compiledSuiteFiles: ConfigurableFileCollection = project.objects.fileCollection().from(xspecBuildDir.map { dir ->
    suiteSpecPaths.get().map { p -> compiledFile(dir, p) }
  })

  private val compilerXsl = xspecHome.map { xspecHome -> xspecHome.file("src/compiler/compile-xslt-tests.xsl") }

  @TaskAction
  fun compile() {
    val cp = classpath
    suiteSpecPaths.get().forEach { suiteSpecPath ->
      val input = xspecDir.get().file(suiteSpecPath)
      val output = compiledFile(xspecBuildDir.get(), suiteSpecPath)
      val coverage = output.toPath().parent.resolve("${output.nameWithoutExtension}-coverage.xml")

      ex.javaexec {
        jvmArgs(baseJavaOptions(input.asFile))
        jvmArgs("-Dxspec.coverage.xml=\"${coverage}\"")

        classpath = cp
        mainClass = "net.sf.saxon.Transform"
        args("-o:${output}", "-s:${input.asFile}", "-xsl:${compilerXsl.get().asFile}")
      }


    }
  }
}


abstract class XspecRunnerTask @Inject constructor(private val ex: ExecOperations): XspecBaseTask() {
  fun coverageFile(input: File): File {
    return compiledFileTo(input, "coverage.xml")
  }

  fun resultFile(input: File): File {
    return compiledFileTo(input, "result.xml")
  }

  @get:InputFiles
  abstract val compiledSuiteFiles: ConfigurableFileCollection

  @get:OutputFiles
  val resultFiles: ConfigurableFileCollection get() = project.objects.fileCollection().from(compiledSuiteFiles.map { input ->
    resultFile(input)
  })

  @TaskAction
  fun execute() {
    val cp = classpath

    compiledSuiteFiles.forEach { input ->
      val output = resultFile(input)
      val coverage = coverageFile(input)

      ex.javaexec {
        jvmArgs(baseJavaOptions(input))
        jvmArgs("-Dxspec.coverage.xml=\"${coverage}\"")

        classpath = cp
        args("-o:${output}", "-xsl:${input}", "-it:{http://www.jenitennison.com/xslt/xspec}main")
        mainClass = "net.sf.saxon.Transform"

        //  TODO: Catalog      -cp "$CP" net.sf.saxon.Transform ${CATALOG:+"$CATALOG"} "$@"
      }
    }
  }
}