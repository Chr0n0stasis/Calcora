import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.2.10"
    id("org.jetbrains.compose") version "1.10.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

val repositoryRoot = layout.projectDirectory.dir("../..").asFile
val androidSources = repositoryRoot.resolve("app/src/main/java")
val generatedResources = layout.buildDirectory.dir("generated/composeResources/commonMain")
val generatedAndroidSources = layout.buildDirectory.dir("generated/androidSources/iosMain")

val iosSourceExcludes = listOf(
    "dev/libchara/calcora/MainActivity.kt",
    "dev/libchara/calcora/ScriptActivity.kt",
    "dev/libchara/calcora/TerminalActivity.kt",
    "dev/libchara/calcora/data/HistoryStore.kt",
    "dev/libchara/calcora/data/SettingsStore.kt",
    "dev/libchara/calcora/data/UpdateChecker.kt",
    "dev/libchara/calcora/engine/GiacEngine.kt",
    "dev/libchara/calcora/engine/HelpParser.kt",
    "dev/libchara/calcora/ui/theme/Theme.kt"
)

// Filter only the reused Android tree. SourceSet.exclude() would also remove
// the iOS replacements because they intentionally use the same package paths.
val syncIosKotlinSources by tasks.registering(Sync::class) {
    from(androidSources) { exclude(iosSourceExcludes) }
    into(generatedAndroidSources)
}

val syncIosResources by tasks.registering(Sync::class) {
    from(repositoryRoot.resolve("app/src/main/res/values/strings.xml")) { into("values") }
    from(repositoryRoot.resolve("app/src/main/res/values-zh/strings.xml")) { into("values-zh") }
    from(repositoryRoot.resolve("app/src/main/res/font/ibm_3270_regular.ttf")) { into("font") }
    from(repositoryRoot.resolve("app/src/main/assets/aide_cas")) { into("files") }
    from(repositoryRoot.resolve("app/src/main/assets/zh/aide_cas")) { into("files/zh") }
    into(generatedResources)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    targets.withType<KotlinNativeTarget>().configureEach {
        val nativeTargetName = name
        binaries.framework {
            baseName = "CalcoraShared"
            isStatic = true
            binaryOption("bundleId", "dev.libchara.calcora.shared")
            linkerOpts(
                "-L${layout.buildDirectory.get().asFile}/native/$nativeTargetName",
                "-lcalcora",
                "-lz",
                "-liconv"
            )
        }
        compilations.getByName("main").cinterops.create("calcoraEngine") {
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/calcoraEngine.def"))
            includeDirs(repositoryRoot.resolve("app/src/main/cpp"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }

        iosMain {
            kotlin.srcDir(generatedAndroidSources)
        }
    }
}

tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
    dependsOn(syncIosKotlinSources)
}

compose.resources {
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = syncIosResources.map { generatedResources.get() }
    )
    packageOfResClass = "dev.libchara.calcora.generated.resources"
    generateResClass = always
}

val nativeTargets = mapOf(
    "IosArm64" to "iosArm64",
    "IosSimulatorArm64" to "iosSimulatorArm64",
    "IosX64" to "iosX64"
)

nativeTargets.forEach { (taskSuffix, targetName) ->
    val nativeTask = tasks.register<Exec>("buildCalcoraNative$taskSuffix") {
        val output = layout.buildDirectory.dir("native/$targetName")
        inputs.files(
            repositoryRoot.resolve("app/src/main/cpp/native-lib.cpp"),
            repositoryRoot.resolve("app/src/main/cpp/calcora_engine.h"),
            repositoryRoot.resolve("iosApp/native/CMakeLists.txt")
        )
        inputs.dir(repositoryRoot.resolve("giac-2.0.0/src"))
        inputs.dir(repositoryRoot.resolve("third_party/libtommath-1.3.0"))
        outputs.file(output.map { it.file("libcalcora.a") })
        commandLine(
            "bash",
            repositoryRoot.resolve("iosApp/native/build-native.sh").absolutePath,
            targetName,
            output.get().asFile.absolutePath
        )
    }
    tasks.matching {
        it.name.startsWith("link") && it.name.contains(taskSuffix)
    }.configureEach { dependsOn(nativeTask) }
}
