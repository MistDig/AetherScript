plugins { application; id("com.gradleup.shadow") }
dependencies { implementation(project(":core")) }
application { mainClass.set("dev.aetherscript.mcp.Main") }
tasks.shadowJar {
    // Stable dependency ordering even when Gradle resolves lockfile metadata in a different order.
    val runtime = project.configurations.named("runtimeClasspath")
    includedDependencies.setFrom(runtime.map { it.files.sortedBy { file -> file.name } })
    dependsOn(runtime)
 archiveClassifier.set("") }
tasks.jar { archiveClassifier.set("thin") }
tasks.assemble { dependsOn(tasks.shadowJar) }
