plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.postgres.jdbc)
    testImplementation(libs.bundles.testing)
    testImplementation("io.ktor:ktor-server-test-host:3.0.2")
}

application { mainClass.set("org.webservices.legalresearch.MainKt") }
tasks.shadowJar { mergeServiceFiles() }
tasks.withType<Jar> { archiveBaseName.set("legal-research-mcp") }
