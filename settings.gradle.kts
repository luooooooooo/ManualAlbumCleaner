// GitHub Actions 等境外构建机访问阿里云镜像经常超时，这里让 CI 优先走官方仓库，
// 本地（国内）仍然优先走阿里云镜像。
pluginManagement {
    val isCi = System.getenv("CI") == "true"
    repositories {
        if (isCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        if (!isCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val isCi = System.getenv("CI") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCi) {
            google()
            mavenCentral()
        }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        if (!isCi) {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "ManualAlbumCleaner"
include(":app")
