package io.werkstatt.xspec.xspec_gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*

class XspecGradlePlugin: Plugin<Project> {
    private fun suiteSpecPaths(inputs: FileCollection, xspecDirProvider: Provider<Directory>): Provider<List<String>> {
        return xspecDirProvider.map { xspecDir ->
            inputs.map {
                it.relativeTo(xspecDir.asFile).toString()
            }
        }
    }

    override fun apply(project: Project) {
        val xspec by project.configurations.creating
        val xspecRuntime by project.configurations.resolvable("xspecRuntime") {
            extendsFrom(xspec)
        }

        val extension = project.extensions.create<XspecExtension>("xspec")
        extension.xspecHome.convention(project.layout.buildDirectory.dir(".xspec"))
        extension.xspecDir.convention(project.layout.projectDirectory.dir("xspec"))
        extension.xspecBuildDir.convention(project.layout.buildDirectory.dir("xspec"))

        val version = this::class.java.getResource("xspec-version.txt").readText().trim()
        val xspecHome = extension.xspecHome

        val suiteSpecs = suiteSpecPaths(extension.suiteFiles, extension.xspecDir)

        val zip = project.tasks.register<XspecImplCopyZipTask>("extract-xspec-zip") {
            xspecVersion = version
            xspecUnpackDir = xspecHome
        }

        val impl = project.tasks.register<XspecImplCopyTask>("copy-xspec-impl") {
            xspecVersion = version
            xspecUnpackDir = xspecHome
            xspecZip = zip.map { it.xspecZip.get() }
        }

        val compiler = project.tasks.register<XspecCompilerTask>("xspec-compile") {
            classpath = xspecRuntime
            xspecDir = extension.xspecDir
            xspecBuildDir = extension.xspecBuildDir
            this.xspecHome = impl.map { it.xspecHome.get() }
            suiteSpecPaths = suiteSpecs
        }

        val runner = project.tasks.register<XspecRunnerTask>("xspec") {
            classpath = xspecRuntime
            xspecDir = extension.xspecDir
            xspecBuildDir = extension.xspecBuildDir
            this.xspecHome = impl.map { it.xspecHome.get() }
            compiledSuiteFiles.from(compiler)
        }
    }
}
