data class SnippetData(
  val file: File,
  val lineNumber: Int,
  val content: String,
  var tempDir: File? = null
)

fun findSnippets(dirs: List<String>): List<SnippetData> {
  val snippets = mutableListOf<SnippetData>()
  dirs.forEach { dirName ->
    val dir = rootProject
      .projectDir
      .parentFile
      .resolve(dirName)
      .listFiles()?.let {
        it.filter { it.name.endsWith(".md") }
          .forEach { file ->
            val currentSnippet = kotlin.text.StringBuilder()
            var snippetStart = 0
            var lineNumber = 0
            file.forEachLine { line ->
              lineNumber++
              if (line == "```kotlin")
                snippetStart = lineNumber + 1
              else if (line == "```" && snippetStart != 0) {
                snippets.add(SnippetData(file, snippetStart, currentSnippet.toString()))
              snippetStart = 0
              currentSnippet.clear()
            } else {
              if (snippetStart != 0) {
                currentSnippet.appendln(line)
            }
          }
        }
      }
    }
  }
  return snippets
}

fun cloneTemplate(template: String, index: Int, content: String): File {
  val tempDir = file("${project.buildDir.absolutePath}/temp/cloned-$index")
  tempDir.deleteRecursively()
  tempDir.mkdirs()
  file("${projectDir.parentFile.parentFile.absolutePath}/templates/$template").copyRecursively(tempDir)
  // tempDir.deleteOnExit()
  File("$tempDir/src/main/kotlin/main.kt").printWriter().use { out ->
    out.println(content)
  }
  return tempDir
}

@OptIn(ExperimentalStdlibApi::class)
fun checkDirs(dirs: List<String>, template: String, buildCmd: String = "build") {
  val snippets = findSnippets(dirs)
  snippets.forEachIndexed { index, snippet ->
    println("process snippet $index at ${snippet.file}:${snippet.lineNumber} with $template")
    snippet.tempDir = cloneTemplate(template, index, snippet.content)
    val isWin = System.getProperty("os.name").startsWith("Win")
    val args = buildList {
        if (isWin) {
            add("gradlew.bat")
        } else {
            add("bash")
            add("./gradlew")
        }

        add(buildCmd)

        project.findProperty("kotlin.version")?.also {
            add("-Pkotlin.version=$it")
        }
        project.findProperty("compose.version")?.also {
            add("-Pcompose.version=$it")
        }
    }
    val proc = ProcessBuilder(*args.toTypedArray())
      .directory(snippet.tempDir)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .start()
    proc.waitFor(5, TimeUnit.MINUTES)
    if (proc.exitValue() != 0) {
      println(proc.inputStream.bufferedReader().readText())
      println(proc.errorStream.bufferedReader().readText())
      throw GradleException("Error in snippet at ${snippet.file}:${snippet.lineNumber}")
    }
  }
}

// NOTICE: currently we use a bit hacky approach, when "```kotlin" marks code that shall be checked, while "``` kotlin"
// with whitespace marks code that shall not be checked.
tasks.register("check") {
  val checks = CheckSpec.createCheckSpecs(
    checkTargets = (project.property("CHECK_TARGET")?.toString() ?: "all").toLowerCase()
  )

  doLast {
    for (check in checks) {
      val subdirs = project
        .projectDir
        .parentFile
        .resolve(check.dir)
        .listFiles()
        .filter {
          it.isDirectory && it.name[0].isUpperCase()
        }
        .map { it.name }

      checkDirs(
        dirs = subdirs.map { "${check.dir}/$it" },
        template = check.template,
        buildCmd = check.gradleCmd
      )
    }
  }
}

data class CheckSpec(
  val gradleCmd: String,
  val dir: String,
  val template: String
) {

  companion object {
    fun desktop() = CheckSpec(gradleCmd = "build", dir = ".", template = "desktop-template")
    fun web() = CheckSpec(gradleCmd = "compileKotlinJs", dir = "Web", template = "web-template")
    fun all() = listOf(desktop(), web())

    fun createCheckSpecs(checkTargets: String = "all"): List<CheckSpec> {
      return when (checkTargets) {
        "web" -> listOf(web())
        "desktop" -> listOf(desktop())
        else -> all()
      }
    }
  }
}
