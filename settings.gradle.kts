rootProject.name = "KoDEx"

include("kodex-common")
include("kodex-gradle-plugin")
include("kodex-intellij-plugin")

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
        mavenLocal()
    }
}
