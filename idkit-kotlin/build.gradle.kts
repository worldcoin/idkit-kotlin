plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    kotlin("plugin.serialization") version "2.0.0"
    alias(libs.plugins.mavenPublish)
}

val libraryGroup = "com.worldcoin"
val libraryArtifactId = "idkit-kotlin"
val libraryVersion = "4.0.0-SNAPSHOT"

android {
    namespace = "com.worldcoin.idkit_kotlin"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlincrypto.sha3)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(libraryGroup, libraryArtifactId, libraryVersion)

    pom {
        name.set("IDKit (Kotlin)")
        description.set("The IDKit library provides a simple Kotlin interface for prompting users for World ID proofs.")
        inceptionYear.set("2024")
        url.set("https://github.com/worldcoin/idkit-kotlin/")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://mit-license.org/")
                distribution.set("https://mit-license.org/")
            }
        }
        developers {
            developer {
                id.set("worldcoin")
                name.set("Worldcoin")
                url.set("https://github.com/worldcoin/")
            }
        }
        scm {
            url.set("https://github.com/worldcoin/idkit-kotlin/")
            connection.set("scm:git:git://github.com/worldcoin/idkit-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/worldcoin/idkit-kotlin.git")
        }
    }
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/worldcoin/idkit-kotlin")
                credentials {
                    username = System.getenv("GITHUB_USER")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}