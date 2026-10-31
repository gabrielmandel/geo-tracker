plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    api(project(":domain"))

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
}
