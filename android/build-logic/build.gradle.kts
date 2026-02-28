plugins {
    `kotlin-dsl`
}

group = "com.pcfutbol.buildlogic"

dependencies {
    compileOnly(libs.plugins.android.application.get().let { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })
    compileOnly(libs.plugins.android.library.get().let { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })
    compileOnly(libs.plugins.kotlin.android.get().let { "org.jetbrains.kotlin:kotlin-gradle-plugin:${it.version}" })
    compileOnly(libs.plugins.kotlin.jvm.get().let { "org.jetbrains.kotlin:kotlin-gradle-plugin:${it.version}" })
}
