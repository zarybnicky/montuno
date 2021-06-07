import org.gradle.api.internal.HasConvention
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = project.properties["group"].toString()
version = project.properties["version"].toString()

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61")
    }
}

repositories {
    jcenter()
    mavenCentral()
}

plugins {
    application
    java
    idea
    antlr
    kotlin("jvm") version "1.5.10"
    kotlin("kapt") version "1.5.10"
    id("me.champeau.gradle.jmh") version "0.5.3"
}

val compiler: Configuration by configurations.creating
val graalVersion = "21.1.0"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk7"))
    implementation(kotlin("reflect"))

    jmh("org.openjdk.jmh:jmh-core:1.32")
    kaptJmh("org.openjdk.jmh:jmh-generator-annprocess:1.32")

    antlr("org.antlr:antlr4:4.7.2")
    api("org.antlr:antlr4-runtime:4.7.2")

    implementation("org.jline:jline-builtins:3.19.0")
    implementation("org.jline:jline-console:3.19.0")
    implementation("org.jline:jline-reader:3.19.0")
    implementation("org.jline:jline-terminal:3.19.0")
    implementation("org.jline:jline-terminal-jna:3.19.0")
    implementation("net.java.dev.jna:jna:5.5.0")

    testImplementation(platform("org.junit:junit-bom:5.7.0"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.graalvm.compiler:compiler:$graalVersion")

    compiler("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.sdk:graal-sdk:$graalVersion")
    implementation("org.graalvm.sdk:launcher-common:$graalVersion")
    implementation("org.graalvm.truffle:truffle-api:$graalVersion")
    kapt("org.graalvm.truffle:truffle-api:$graalVersion")
    kapt("org.graalvm.truffle:truffle-dsl-processor:$graalVersion")
    compileOnly("org.graalvm.truffle:truffle-tck:${graalVersion}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
    modularity.inferModulePath.set(true)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "9"
    kotlinOptions.apiVersion = "1.5"
    kotlinOptions.languageVersion = "1.5"
}

fun <R> SourceSet.kotlin(f: KotlinSourceSet.() -> R): R =
    ((this as HasConvention).convention.getPlugin(KotlinSourceSet::class.java)).f()

val SourceSet.kotlin: SourceDirectorySet get() = kotlin { kotlin }

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages")
}

sourceSets {
    main {
        java.srcDir("build/generated-src/antlr/main")
        kotlin.srcDir("build/generated-src/antlr/main")
    }
}

application {
    mainClassName = "montuno.Launcher"
    applicationDefaultJvmArgs = listOf(
        "-Xss32m",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "--module-path=${compiler.asPath}",
        "--upgrade-module-path=${compiler.asPath}",
        "-Dtruffle.class.path.append=@APP_HOME@/lib/montuno.jar"
    )
}
tasks.getByName<CreateStartScripts>("startScripts") {
    appendParallelSafeAction {
        unixScript.writeText(unixScript.readText().replace("@APP_HOME@", "\$APP_HOME"))
        windowsScript.writeText(windowsScript.readText().replace("@APP_HOME@", "%~dp0.."))
    }
}

val graalArgs = listOf(
    "-Xss32m",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "--module-path=${compiler.asPath}",
    "--upgrade-module-path=${compiler.asPath}",
    "-Dtruffle.class.path.append=libs/montuno.jar",
    "--add-opens=jdk.internal.vm.compiler/org.graalvm.compiler.truffle.runtime=ALL-UNNAMED",
    "--add-opens=org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED",

    "-Dgraal.Dump=Truffle",
    "-Dgraal.PrintGraph=Network",
    "-Dgraal.CompilationFailureAction=ExitVM",
    "-Dpolyglot.engine.TraceCompilation=true",
    "-Dpolyglot.engine.TraceSplitting=true",
    //"-Dpolyglot.engine.TraceSplittingSummary=true",
    "-Dpolyglot.engine.TraceAssumptions=true"
    //"-Dpolyglot.engine.CompileImmediately=true",
    //"-Dpolyglot.engine.TraceTransferToInterpreter=true"
    // limit size of graphs for easier visualization
    //"-Dpolyglot.engine.InliningRecursionDepth=0",
    //"-Dgraal.LoopPeeling=false",
)

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    dependsOn("jar")
    jvmArgs = graalArgs
}

jmh {
    jvmArgsPrepend = graalArgs
}

tasks.replace("run", JavaExec::class.java).run {
//  enableAssertions = true
    dependsOn("jar")
    standardInput = System.`in`
    standardOutput = System.out
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs = graalArgs
    main = "montuno.Launcher"
}

tasks.getByName<Jar>("jar") {
    exclude("jre/**")
    exclude("META-INF/symlinks")
    exclude("META-INF/permissions")
    archiveBaseName.set("montuno")
    manifest {
        attributes["Main-Class"] = "montuno.Launcher"
        attributes["Class-Path"] =
            configurations.runtimeClasspath.get().files.joinToString(separator = " ") { it.absolutePath }
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
}