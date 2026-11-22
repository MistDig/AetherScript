plugins { java }
group = "example.sky"
version = "1.0.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
val aetherScriptJar = providers.gradleProperty("aetherScriptJar")
dependencies {
    compileOnly(files(aetherScriptJar.get()))
    compileOnly("io.papermc.paper:paper-api:26.3.build.245-stable")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(25) }
tasks.withType<AbstractArchiveTask>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }
