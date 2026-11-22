plugins {
    id("com.android.library")
    `maven-publish`
}

android {
    namespace = "com.github.projectenyo.unifiedinbox"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.Project-Enyo"
            artifactId = "UnifiedInbox"
            version = providers.gradleProperty("libraryVersion").getOrElse("1.0.0")
            afterEvaluate { from(components["release"]) }
        }
    }
}
