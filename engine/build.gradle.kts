plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    systemProperty("harness.failureProbe", providers.gradleProperty("harnessFailureProbe").orElse("false").get())
    testLogging {
        events("failed", "skipped")
    }
}
