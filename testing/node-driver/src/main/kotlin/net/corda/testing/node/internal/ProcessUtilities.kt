package net.corda.testing.node.internal

import net.corda.core.internal.div
import java.io.File
import java.nio.file.Path

object ProcessUtilities {
    fun startJavaProcess(
            entry: JavaEntry,
            appArguments: List<String>,
            workingDirectory: Path? = null,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList(),
            maximumHeapSize: String? = null
    ): Process {
        val command = mutableListOf<String>().apply {
            add(javaPath)
            (jdwpPort != null) && add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
            if (maximumHeapSize != null) add("-Xmx$maximumHeapSize")
            add("-XX:+UseG1GC")
            addAll(extraJvmArguments)
            when (entry) {
                is JavaEntry.ClassName -> add(entry.className)
                is JavaEntry.JarFile -> addAll(listOf("-jar", entry.jarFile.toAbsolutePath().toString()))
            }
            addAll(appArguments)
        }
        return ProcessBuilder(command).apply {
            inheritIO()
            if (entry is JavaEntry.ClassName) {
                environment()["CLASSPATH"] = entry.classPath.joinToString(File.pathSeparator)
            }
            if (workingDirectory != null) {
                val prefix = when (entry) {
                    is JavaEntry.ClassName -> entry.className
                    is JavaEntry.JarFile -> entry.jarFile.fileName.toString()
                }
                redirectError((workingDirectory / "$prefix.stderr.log").toFile())
                redirectOutput((workingDirectory / "$prefix.stdout.log").toFile())
                directory(workingDirectory.toFile())
            }
        }.start()
    }

    private val javaPath = (System.getProperty("java.home") / "bin" / "java").toString()
}

sealed class JavaEntry {
    data class ClassName(val className: String, val classPath: List<String> = defaultClassPath) : JavaEntry()
    data class JarFile(val jarFile: Path) : JavaEntry()

    companion object {
        val defaultClassPath: List<String> = System.getProperty("java.class.path").split(File.pathSeparator)

        inline fun <reified C : Any> mainClass(classPath: List<String> = defaultClassPath): ClassName {
            return ClassName(C::class.java.name, classPath)
        }
    }
}
