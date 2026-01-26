plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Enforce logging best practices and migrate between logging frameworks. Automatically."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()

recipeDependencies {
    parserClasspath("org.slf4j:slf4j-api:2.+")
    parserClasspath("org.slf4j:slf4j-api:1.7.+")
    parserClasspath("log4j:log4j:1.+")
    parserClasspath("org.apache.logging.log4j:log4j-core:2.+")
    parserClasspath("org.apache.logging.log4j:log4j-api:2.+")
    parserClasspath("commons-logging:commons-logging:1.+")
    parserClasspath("ch.qos.logback:logback-classic:1.3.+")
    parserClasspath("org.projectlombok:lombok:1.18.+")
    parserClasspath("org.jboss.logging:jboss-logging:3.+")
}

dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation("org.kohsuke:wordnet-random-name:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-groovy")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:${rewriteVersion}")
    runtimeOnly("org.openrewrite:rewrite-java-21")

    compileOnly("org.apache.logging.log4j:log4j-core:2.+")
    compileOnly("org.slf4j:slf4j-api:2.+")
    compileOnly("org.jboss.logging:jboss-logging:3.+")

    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    compileOnly("log4j:log4j:1.+") {
        because("log4j 1 has critical vulnerabilities but we need the type for the refaster recipe during compilation")
    }
    testRuntimeOnly("ch.qos.logback:logback-classic:1.3.+")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.14.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.2")

    testImplementation("org.openrewrite:rewrite-kotlin")
    testImplementation("org.openrewrite:rewrite-maven")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")
    testImplementation("org.openrewrite:rewrite-test")

    testImplementation("org.assertj:assertj-core:latest.release")
    testRuntimeOnly(gradleApi())
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
