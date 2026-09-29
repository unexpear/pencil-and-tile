plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

val lexiconDb = layout.buildDirectory.file("lexicon/lexicon.db")
val buildLexiconDb = tasks.register<Exec>("buildLexiconDb") {
    group = "build"
    description = "Download Open English WordNet, ENABLE, and Tatoeba CC0 and build lexicon.db."
    val script = rootProject.file("tools/lexicon/build_lexicon_db.py")
    inputs.file(script)
    inputs.file(rootProject.file("tools/words/blocked.txt"))
    inputs.file(file("src/main/resources/words/all.txt.gz"))
    outputs.file(lexiconDb)
    outputs.file(layout.buildDirectory.file("lexicon/lexicon.sha256"))
    outputs.file(layout.buildDirectory.file("lexicon/lexicon.stats.txt"))
    environment("PYTHONUNBUFFERED", "1")
    commandLine("python3", script.absolutePath, "--out", lexiconDb.get().asFile.absolutePath)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.sqlite.jdbc)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    dependsOn(buildLexiconDb)
    systemProperty("lexicon.db", lexiconDb.get().asFile.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
