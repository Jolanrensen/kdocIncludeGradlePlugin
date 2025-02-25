rootProject.name = "KoDEx"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include("kodex-common")
include("kodex-gradle-plugin")
include("kodex-intellij-plugin")
include("kodex-example-gradle-project-jvm")

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
        mavenLocal()
    }
}
