plugins { `java-library` }
dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("org.yaml:snakeyaml:2.4")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("aetherscript-build.properties") { expand("version" to project.version) }
}
