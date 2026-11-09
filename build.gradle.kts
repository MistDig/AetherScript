plugins { base; id("com.gradleup.shadow") version "9.1.0" apply false }
allprojects {
    group = "dev.aetherscript"
    version = providers.gradleProperty("version").get()
    repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
}
subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(25) }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.12.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    dependencyLocking { lockAllConfigurations() }
    tasks.withType<AbstractArchiveTask>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }
}
