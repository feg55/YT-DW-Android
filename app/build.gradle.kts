import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject

abstract class PackageLegalNotices @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val legalFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun packageFiles() {
        fileSystemOperations.sync {
            from(legalFiles)
            into(outputDirectory.get().dir("legal"))
        }
    }
}

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ytDlpVersion = "2026.07.04"
val ytDlpSha256 = "495be29ff4d9d4e9be7eabdfef225221e5d5282e77f2f505abc6dca80349f3fd"
val generatedYtDlpResources = layout.buildDirectory.dir("generated/ytDlpResources")
val bundledYtDlp = generatedYtDlpResources.map { it.file("raw/ytdlp") }
val generatedLegalAssets = layout.buildDirectory.dir("generated/legalAssets")
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use(::load)
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseSigning.getProperty(it).isNullOrBlank() }
val releaseArtifactRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').lowercase() in setOf("assemblerelease", "bundlerelease")
}

check(hasReleaseSigning || !releaseArtifactRequested) {
    "Release signing is not configured. Copy keystore.properties.example to " +
        "keystore.properties and provide a persistent release keystore."
}

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { "%02x".format(it) }

val downloadYtDlp = tasks.register("downloadYtDlp") {
    inputs.property("version", ytDlpVersion)
    inputs.property("sha256", ytDlpSha256)
    outputs.file(bundledYtDlp)
    outputs.upToDateWhen {
        val output = bundledYtDlp.get().asFile
        output.isFile && sha256(output) == ytDlpSha256
    }
    doLast {
        val output = bundledYtDlp.get().asFile
        output.parentFile.mkdirs()
        val temporary = File(output.parentFile, "${output.name}.part")
        URI("https://github.com/yt-dlp/yt-dlp/releases/download/$ytDlpVersion/yt-dlp")
            .toURL().openStream().use { input -> temporary.outputStream().use(input::copyTo) }
        check(sha256(temporary) == ytDlpSha256) { "Downloaded yt-dlp checksum does not match $ytDlpSha256" }
        temporary.copyTo(output, overwrite = true)
        temporary.delete()
    }
}

val packageLegalNotices = tasks.register<PackageLegalNotices>("packageLegalNotices") {
    legalFiles.from(
        rootProject.file("LICENSE"),
        rootProject.file("NOTICE"),
        rootProject.file("THIRD_PARTY_NOTICES.md"),
    )
    outputDirectory.set(generatedLegalAssets)
}

android {
    namespace = "io.github.ytdw.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ytdw.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
        buildConfigField("String", "YT_DLP_VERSION", "\"$ytDlpVersion\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk.abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].res.directories.add(generatedYtDlpResources.get().asFile.absolutePath)
    packaging.jniLibs.useLegacyPackaging = true
    packaging.jniLibs.keepDebugSymbols += setOf(
        "**/libandroidx.graphics.path.so",
        "**/libffmpeg.so",
        "**/libffmpeg.zip.so",
        "**/libffprobe.so",
        "**/libpython.so",
        "**/libpython.zip.so",
        "**/libqjs.so",
    )
    packaging.resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    testOptions.unitTests.isIncludeAndroidResources = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named("preBuild").configure { dependsOn(downloadYtDlp) }

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(packageLegalNotices) {
            it.outputDirectory
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    val lifecycleVersion = "2.10.0"
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")

    androidTestImplementation("androidx.test:core:1.7.0")
    //noinspection GradleDependency -- 1.3.0 metadata exists, but its artifact is absent from Google Maven.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    //noinspection GradleDependency -- 1.7.0 transitively requests unpublished test-services artifacts.
    androidTestImplementation("androidx.test:runner:1.6.2")
}
