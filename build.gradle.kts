import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins { java }
group   = "processing.mode.scheme"
version = "0.1.0"

val p4 = System.getenv("PROCESSING4_DIR")
    ?: "${System.getProperty("user.home")}/Projects/processing4"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

// A Processing release works too (CI uses 4.3): PROCESSING_DIR is the folder
// with lib/pde.jar and core/library/core.jar in it.
val processingDir = System.getenv("PROCESSING_DIR")

// java-tree-sitter: the version of the jar in mode/, or -PtsVersion=... (CI)
val tsVersion: String = (project.findProperty("tsVersion") as String?)
    ?: file("${System.getProperty("user.home")}/sketchbook/modes/SchemeMode/mode").listFiles()
        ?.map { it.name }?.firstOrNull { it.matches(Regex("java-tree-sitter-[0-9.]+\\.jar")) }
        ?.removePrefix("java-tree-sitter-")?.removeSuffix(".jar")
    ?: error("java-tree-sitter version unknown: pass -PtsVersion=...")

val modeLibs by configurations.creating

dependencies {
    // processing.app.* lives in java/build/libs/java*.jar
    compileOnly(fileTree("$p4/java/build/libs") { include("java*.jar") })
    // app.jar has Base, Editor, Mode etc.
    compileOnly(fileTree("$p4/app/build/libs") { include("app*.jar") })
    // core
    compileOnly(fileTree("$p4/core/library") { include("*.jar") })
    if (processingDir != null) {
        compileOnly(fileTree(processingDir) { include("lib/pde.jar", "lib/app/*.jar", "core/library/*.jar") })
    }
    // java-tree-sitter and what it needs, also copied into mode/ for releases
    compileOnly("ch.usi.si.seart:java-tree-sitter:$tsVersion")
    modeLibs("ch.usi.si.seart:java-tree-sitter:$tsVersion")
}

// mode/ for a release: SchemeMode.jar plus java-tree-sitter and its dependencies
tasks.register<Copy>("modeLibs") {
    from(modeLibs)
    into("build/mode")
}

sourceSets { main { java { srcDirs("src/java") } } }
tasks.jar {
    archiveBaseName = "SchemeMode"
    archiveVersion = ""
}

val modeDir = "${System.getProperty("user.home")}/sketchbook/modes/SchemeMode"

tasks.register("install") {
    dependsOn("jar")
    group = "install"
    doLast {
        val built = tasks.jar.get().archiveFile.get().asFile
        val tmp = file("$modeDir/mode/.SchemeMode.jar.tmp")
        built.copyTo(tmp, overwrite = true)
        Files.move(tmp.toPath(), file("$modeDir/mode/SchemeMode.jar").toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE)
        println("Installed to $modeDir")
    }
}

tasks.named<Jar>("jar") {
    from("resources") { include("queries/**") }
}

// `gradle jar` alone leaves mode/ stale; always copy into place after
// (not in CI, where there's no sketchbook to install into).
if (System.getenv("CI") == null) tasks.named("jar") { finalizedBy("install") }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
