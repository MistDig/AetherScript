plugins { java; id("com.gradleup.shadow") }
dependencies { implementation(project(":core")); compileOnly("io.papermc.paper:paper-api:26.3.build.245-stable") }
tasks.shadowJar {
    // Stable dependency ordering even when Gradle resolves lockfile metadata in a different order.
    val runtime = project.configurations.named("runtimeClasspath")
    includedDependencies.setFrom(runtime.map { it.files.sortedBy { file -> file.name } })
    dependsOn(runtime)

    archiveClassifier.set("")
    relocate("org.yaml.snakeyaml", "dev.aetherscript.internal.yaml")
    relocate("com.fasterxml.jackson", "dev.aetherscript.internal.jackson")
}
tasks.jar { archiveClassifier.set("thin") }
tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
