import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
}

subprojects {
    group = "com.gpstracker"
    version = "0.0.1-SNAPSHOT"

    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }

        // Test-only BOM: the domain stays free of Spring at runtime.
        dependencies {
            "testImplementation"(platform(libs.spring.boot.dependencies))
            "testImplementation"(libs.junit.jupiter)
            "testImplementation"(libs.assertj.core)
            "testRuntimeOnly"(libs.junit.platform.launcher)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
