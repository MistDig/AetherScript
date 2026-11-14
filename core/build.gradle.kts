plugins { `java-library` }
dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("org.yaml:snakeyaml:2.4")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("aetherscript-build.properties") { expand("version" to project.version) }
}
