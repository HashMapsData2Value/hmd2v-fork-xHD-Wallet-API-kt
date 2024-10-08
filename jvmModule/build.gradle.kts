import java.util.Base64

version = project.property("version") as String
group = "io.github.hashmapsdata2value.xhdwalletapi"

plugins {
    kotlin("plugin.serialization") version "1.9.22"
    kotlin("jvm")
    `java-library`
    id("maven-publish")
    id("signing")
    id("tech.yanand.maven-central-publish") version "1.2.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        java.setSrcDirs(setOf(file("../sharedModule/src/main/kotlin")))
    }
}

dependencies {
    api(project(":sharedModule"))
    
    testImplementation("com.algorand:algosdk:2.4.0")
    
    // Use the Kotlin JUnit 5 integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Use the JUnit 5 integration.
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    // api("org.apache.commons:commons-math3:3.6.1")    

    // This dependency is used internally, and not exposed to consumers on their own compile
    // classpath.
    implementation("com.google.guava:guava:31.0.1-jre")
}

// Apply a specific Java toolchain to ease working on different environments.
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc)
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// Create a "fat" JAR containing the runtime dependencies.
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

// Run ./gradlew test to execute tests not requiring an Algorand Sandbox network
tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform{
        excludeTags("sandbox")
    }
    testLogging.showStandardStreams = true
}

tasks.register<Test>("testWithKhovratovichSafetyDepth") {
    useJUnitPlatform{
        excludeTags("sandbox")
    }
    systemProperty("khovratovichSafetyTest", "true")
    testLogging.showStandardStreams = true
}

// Run ./gradlew testWithAlgorandSandbox to run tests that interact with an Algorand Sandbox network (e.g. Algokit Localnet)
tasks.register<Test>("testWithAlgorandSandbox") {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

tasks.jar {
    archiveFileName.set("XHDWalletAPI-JVM.jar")
}

task("copyJarToRoot", type = Copy::class) {
    dependsOn("assemble")

    from("$buildDir/libs")
    into("$rootDir/build")
    include("*.jar")
}

tasks.named("build") {
    finalizedBy("copyJarToRoot")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "xhdwalletapi"
            version = project.version.toString()

            artifact(tasks["fatJar"]) {
                classifier = null
            }
            artifact(tasks["javadocJar"])
            artifact(tasks["sourcesJar"])

            pom {
                name.set("XHDWalletAPI")
                description.set("A library for extended hierarchical deterministic wallets")
                url.set("https://github.com/HashMapsData2Value/hmd2v-fork-xHD-Wallet-API-kt")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("hashmapsdata2value")
                        name.set("HMD2V")
                        email.set("yared@679labs.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/HashMapsData2Value/hmd2v-fork-xHD-Wallet-API-kt.git")
                    developerConnection.set("scm:git:ssh://github.com/HashMapsData2Value/hmd2v-fork-xHD-Wallet-API-kt.git")
                    url.set("https://github.com/HashMapsData2Value/hmd2v-fork-xHD-Wallet-API-kt.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("repos/bundles").get().asFile.toURI())
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY") ?: ""
    val signingPassword = System.getenv("GPG_PASSPHRASE") ?: ""
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

val username = System.getenv("OSSRH_USERNAME") ?: ""
val password = System.getenv("OSSRH_PASSWORD") ?: ""

mavenCentral {
    repoDir = layout.buildDirectory.dir("repos/bundles")
    // Token for Publisher API calls obtained from Sonatype official,
    // it should be Base64 encoded of "username:password".
    authToken = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    // Whether the upload should be automatically published or not. Use 'USER_MANAGED' if you wish to do this manually.
    // This property is optional and defaults to 'AUTOMATIC'.
    publishingType = "AUTOMATIC"
    // Max wait time for status API to get 'PUBLISHING' or 'PUBLISHED' status when the publishing type is 'AUTOMATIC',
    // or additionally 'VALIDATED' when the publishing type is 'USER_MANAGED'.
    // This property is optional and defaults to 60 seconds.
    maxWait = 500
}