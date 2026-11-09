plugins { application; id("com.gradleup.shadow") }
dependencies { implementation(project(":core")) }
application { mainClass.set("dev.aetherscript.mcp.Main") }
tasks.shadowJar { archiveClassifier.set("") }
tasks.jar { archiveClassifier.set("thin") }
tasks.assemble { dependsOn(tasks.shadowJar) }
