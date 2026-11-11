pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "CyhaiAI"
include(":app", ":llama-runtime")
project(":llama-runtime").projectDir = file("llama.cpp/examples/llama.android/lib")
