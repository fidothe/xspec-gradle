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
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo


interface XspecExtension {
  val xspecDir: DirectoryProperty
  val srcDir: DirectoryProperty
  val xspecBuildDir: DirectoryProperty
  val xspecHome: DirectoryProperty
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
  fun relativizedSpecFilePath(input: Path): Path {
    return input.relativeTo(xspecDir.asFile.get().toPath())
  }

  fun addSuffix(input: Path, suffix: String): Path {
    return input.resolveSibling("${input.nameWithoutExtension}-${suffix}")
  }

  fun relativeToXspecBuildDir(input: Path): Path {
    return xspecBuildDir.get().asFile.toPath().resolve(relativizedSpecFilePath(input))
  }

  @get:InputFiles
  abstract val classpath: ConfigurableFileCollection

  @get:Internal
  abstract val xspecDir: DirectoryProperty

  @get:Internal
  abstract val xspecBuildDir: DirectoryProperty

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
  fun compiledFile(spec: File): Path {
    return addSuffix(relativeToXspecBuildDir(spec.toPath()), "compiled.xsl")
  }

  @get:InputFiles
  abstract val suiteFiles: ConfigurableFileCollection

  @get:OutputFiles
  val compiledSuiteFiles: FileCollection get() = project.objects.fileCollection().from(suiteFiles.elements.map { files ->
    files.map { spec ->
      compiledFile(spec.asFile)
    }
  })

  private val compilerXsl = xspecHome.map { xspecHome -> xspecHome.file("src/compiler/compile-xslt-tests.xsl") }

  @TaskAction
  fun compile() {
    val cp = classpath
    suiteFiles.forEach { input ->
      val output = compiledFile(input)
      val coverage = addSuffix(output, "coverage.xml")

      ex.javaexec {
        jvmArgs(baseJavaOptions(input))
        jvmArgs("-Dxspec.coverage.xml=\"${coverage}\"")

        classpath = cp
        mainClass = "net.sf.saxon.Transform"
        args("-o:${output}", "-s:${input}", "-xsl:${compilerXsl.get().asFile}")
      }
    }
  }
}


abstract class XspecRunnerTask @Inject constructor(private val ex: ExecOperations): XspecBaseTask() {
  fun coverageFile(input: File): Path {
    return addSuffix(input.toPath(), "coverage.xml")
  }

  fun resultFile(input: File): Path {
    return addSuffix(input.toPath(), "result.xml")
  }

  @get:InputFiles
  abstract val compiledSuiteFiles: ConfigurableFileCollection

  @get:InputFiles
  abstract val sourceFiles: ConfigurableFileCollection

  @get:OutputFiles
  val resultFiles: FileCollection get() = project.objects.fileCollection().from(compiledSuiteFiles.map { input ->
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