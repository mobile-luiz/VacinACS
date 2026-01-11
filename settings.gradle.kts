pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ⭐️ CORREÇÃO: Usando a sintaxe Kotlin DSL (String no parâmetro da função)
        maven("https://jitpack.io")
    }
}

rootProject.name = "vacinas"
include(":app")
 