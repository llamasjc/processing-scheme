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

dependencies {
    // processing.app.* lives in java/build/libs/java*.jar
    compileOnly(fileTree("$p4/java/build/libs") { include("java*.jar") })
    compileOnly(fileTree("/home/pep/sketchbook/modes/SchemeMode/mode") { include("java-tree-sitter*.jar", "commons-collections4*.jar", "commons-io*.jar", "slf4j*.jar") })
    // app.jar has Base, Editor, Mode etc.
    compileOnly(fileTree("$p4/app/build/libs") { include("app*.jar") })
    // core
    compileOnly(fileTree("$p4/core/library") { include("*.jar") })
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

// `gradle jar` alone leaves mode/ stale; always copy into place after.
tasks.named("jar") { finalizedBy("install") }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
