plugins { java; id("com.gradleup.shadow") }
dependencies { implementation(project(":core")); compileOnly("io.papermc.paper:paper-api:26.3.build.245-stable") }
tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.yaml.snakeyaml", "dev.aetherscript.internal.yaml")
    relocate("com.fasterxml.jackson", "dev.aetherscript.internal.jackson")
}
tasks.jar { archiveClassifier.set("thin") }
tasks.assemble { dependsOn(tasks.shadowJar) }
