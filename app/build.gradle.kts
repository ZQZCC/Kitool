import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
}

fun readIntLe(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        ((bytes[offset + 3].toInt() and 0xff) shl 24)

fun writeIntLe(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value shr 8).toByte()
    bytes[offset + 2] = (value shr 16).toByte()
    bytes[offset + 3] = (value shr 24).toByte()
}

fun readUleb128(bytes: ByteArray, cursor: IntArray): Int {
    var result = 0
    var shift = 0
    repeat(5) {
        if (cursor[0] >= bytes.size) throw GradleException("Truncated DEX ULEB128 value")
        val value = bytes[cursor[0]++].toInt() and 0xff
        result = result or ((value and 0x7f) shl shift)
        if ((value and 0x80) == 0) return result
        shift += 7
    }
    throw GradleException("Invalid DEX ULEB128 value")
}

fun visitDex(
    bytes: ByteArray,
    visitClassDef: (Int) -> Unit,
    visitCodeItem: (Int) -> Unit,
) {
    if (
        bytes.size < 112 ||
            bytes[0] != 'd'.code.toByte() ||
            bytes[1] != 'e'.code.toByte() ||
            bytes[2] != 'x'.code.toByte() ||
            bytes[3] != '\n'.code.toByte()
    ) {
        throw GradleException("Invalid DEX input")
    }
    val classDefsSize = readIntLe(bytes, 0x60)
    val classDefsOffset = readIntLe(bytes, 0x64)
    repeat(classDefsSize) { classIndex ->
        val classDefOffset = classDefsOffset + classIndex * 32
        visitClassDef(classDefOffset)
        val classDataOffset = readIntLe(bytes, classDefOffset + 24)
        if (classDataOffset == 0) return@repeat

        val cursor = intArrayOf(classDataOffset)
        val staticFields = readUleb128(bytes, cursor)
        val instanceFields = readUleb128(bytes, cursor)
        val directMethods = readUleb128(bytes, cursor)
        val virtualMethods = readUleb128(bytes, cursor)
        repeat(staticFields + instanceFields) {
            readUleb128(bytes, cursor)
            readUleb128(bytes, cursor)
        }
        repeat(directMethods + virtualMethods) {
            readUleb128(bytes, cursor)
            readUleb128(bytes, cursor)
            val codeOffset = readUleb128(bytes, cursor)
            if (codeOffset != 0) visitCodeItem(codeOffset)
        }
    }
}

fun updateDexHashes(bytes: ByteArray) {
    val sha1 = MessageDigest.getInstance("SHA-1")
    sha1.update(bytes, 32, bytes.size - 32)
    System.arraycopy(sha1.digest(), 0, bytes, 12, 20)

    val adler32 = Adler32()
    adler32.update(bytes, 12, bytes.size - 12)
    writeIntLe(bytes, 8, adler32.value.toInt())
}

fun stripDexDebugInfo(input: ByteArray): ByteArray =
    input.clone().also { bytes ->
        visitDex(
            bytes,
            visitClassDef = { writeIntLe(bytes, it + 16, -1) },
            visitCodeItem = { writeIntLe(bytes, it + 8, 0) },
        )
        updateDexHashes(bytes)
    }

fun verifyDexDebugInfoRemoved(bytes: ByteArray) {
    visitDex(
        bytes,
        visitClassDef = {
            if (readIntLe(bytes, it + 16) != -1) {
                throw GradleException("DEX still contains a source-file reference")
            }
        },
        visitCodeItem = {
            if (readIntLe(bytes, it + 8) != 0) {
                throw GradleException("DEX still contains a method debug reference")
            }
        },
    )

    val mapOffset = readIntLe(bytes, 0x34)
    val mapSize = readIntLe(bytes, mapOffset)
    repeat(mapSize) { index ->
        val itemOffset = mapOffset + 4 + index * 12
        val type =
            (bytes[itemOffset].toInt() and 0xff) or
                ((bytes[itemOffset + 1].toInt() and 0xff) shl 8)
        if (type == 0x2003) throw GradleException("DEX still contains debug_info_item data")
    }
}

fun dexEntryOrder(name: String): Int? {
    if (!name.startsWith("classes") || !name.endsWith(".dex")) return null
    val suffix = name.substring(7, name.length - 4)
    return if (suffix.isEmpty()) 1 else suffix.toIntOrNull()
}

fun ZipOutputStream.writeApkEntry(
    name: String,
    contents: ByteArray,
    method: Int,
    timestamp: Long,
    comment: String? = null,
) {
    val entry =
        ZipEntry(name).apply {
            this.method = method
            time = timestamp
            this.comment = comment
            if (method == ZipEntry.STORED) {
                val crc32 = CRC32().apply { update(contents) }
                size = contents.size.toLong()
                compressedSize = contents.size.toLong()
                crc = crc32.value
            }
        }
    putNextEntry(entry)
    write(contents)
    closeEntry()
}

val kitoolMinSdk = 29
val kitoolBuildToolsVersion = "37.0.0"

android {
    namespace = "ka.kitool"
    compileSdk = 37
    buildToolsVersion = kitoolBuildToolsVersion

    defaultConfig {
        applicationId = "ka.kitool"
        minSdk = kitoolMinSdk
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo {
                include = false
            }
            proguardFiles("proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/**",
            "/kotlin/**",
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    testImplementation(libs.junit)
}

val rawReleaseApk =
    layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk")
val unalignedMinimalReleaseApk =
    layout.buildDirectory.file("outputs/apk/release/app-release-minimal-unaligned.apk")
val optimizedUnalignedMinimalReleaseApk =
    layout.buildDirectory.file("outputs/apk/release/app-release-minimal-optimized-unaligned.apk")
val minimalReleaseApk =
    layout.buildDirectory.file("outputs/apk/release/app-release-minimal-unsigned.apk")
val sdkBootClasspath = androidComponents.sdkComponents.bootClasspath
val sdkDirectory = androidComponents.sdkComponents.sdkDirectory
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val executableSuffix = if (isWindows) ".exe" else ""
val javaExecutable =
    file("${System.getProperty("java.home")}/bin/java$executableSuffix")
val zipalign =
    sdkDirectory.map { directory ->
        directory.file(
            "build-tools/$kitoolBuildToolsVersion/zipalign$executableSuffix"
        )
    }
val d8Jar =
    sdkDirectory.map { directory ->
        directory.file("build-tools/$kitoolBuildToolsVersion/lib/d8.jar")
    }
val aapt2 =
    sdkDirectory.map { directory ->
        directory.file("build-tools/$kitoolBuildToolsVersion/aapt2$executableSuffix")
    }

val minimizeReleaseApk =
    tasks.register("minimizeReleaseApk") {
        group = "build"
        description = "Removes release DEX debug data and packaging metadata."
        dependsOn("assembleRelease")
        inputs.file(rawReleaseApk)
        inputs.file(d8Jar)
        inputs.files(sdkBootClasspath)
        outputs.file(unalignedMinimalReleaseApk)

        doLast {
            val inputApk = rawReleaseApk.get().asFile
            val outputApk = unalignedMinimalReleaseApk.get().asFile
            val temporaryDirectory =
                layout.buildDirectory.dir("tmp/minimizeReleaseApk").get().asFile
            project.delete(temporaryDirectory)
            temporaryDirectory.mkdirs()
            outputApk.delete()

            try {
                val patchedDexDirectory = temporaryDirectory.resolve("patched-dex")
                val compactedDexDirectory = temporaryDirectory.resolve("compacted-dex")
                patchedDexDirectory.mkdirs()
                compactedDexDirectory.mkdirs()

                val patchedDexFiles = mutableListOf<File>()
                var dexTimestamp = 0L
                ZipFile(inputApk).use { zip ->
                    val dexEntries =
                        zip.entries()
                            .asSequence()
                            .filter { dexEntryOrder(it.name) != null }
                            .sortedBy { dexEntryOrder(it.name) ?: Int.MAX_VALUE }
                            .toList()
                    if (dexEntries.isEmpty()) throw GradleException("Release APK has no DEX")
                    dexTimestamp = dexEntries.first().time
                    dexEntries.forEachIndexed { index, entry ->
                        val patchedDex = patchedDexDirectory.resolve("classes-$index.dex")
                        val contents =
                            zip.getInputStream(entry).use { stripDexDebugInfo(it.readBytes()) }
                        patchedDex.writeBytes(contents)
                        patchedDexFiles += patchedDex
                    }
                }

                val androidJar =
                    sdkBootClasspath
                        .get()
                        .asSequence()
                        .map { it.asFile }
                        .firstOrNull { it.name == "android.jar" }
                        ?: error("Android boot classpath has no android.jar")
                providers.exec {
                    commandLine(
                        listOf(
                            javaExecutable.absolutePath,
                            "-cp",
                            d8Jar.get().asFile.absolutePath,
                            "com.android.tools.r8.D8",
                            "--release",
                            "--min-api",
                            kitoolMinSdk.toString(),
                            "--lib",
                            androidJar.absolutePath,
                            "--output",
                            compactedDexDirectory.absolutePath,
                        ) + patchedDexFiles.map { it.absolutePath }
                    )
                }.result.get().assertNormalExitValue()

                val compactedDexFiles =
                    compactedDexDirectory
                        .listFiles { file ->
                            file.isFile && dexEntryOrder(file.name) != null
                        }
                        ?.sortedBy { dexEntryOrder(it.name) ?: Int.MAX_VALUE }
                        .orEmpty()
                if (compactedDexFiles.isEmpty()) {
                    throw GradleException("D8 produced no compacted DEX")
                }
                compactedDexFiles.forEach {
                    verifyDexDebugInfoRemoved(it.readBytes())
                }

                val rewrittenApk = temporaryDirectory.resolve("minimal.apk")
                ZipOutputStream(rewrittenApk.outputStream().buffered()).use { output ->
                    output.setLevel(Deflater.BEST_COMPRESSION)
                    ZipFile(inputApk).use { input ->
                        var wroteDex = false
                        input.entries().asSequence().forEach { entry ->
                            val name = entry.name
                            when {
                                name.startsWith("META-INF/") ||
                                    name.startsWith("kotlin/") -> Unit
                                dexEntryOrder(name) != null -> {
                                    if (!wroteDex) {
                                        compactedDexFiles.forEach { dex ->
                                            output.writeApkEntry(
                                                name = dex.name,
                                                contents = dex.readBytes(),
                                                method = ZipEntry.STORED,
                                                timestamp = dexTimestamp,
                                            )
                                        }
                                        wroteDex = true
                                    }
                                }
                                else -> {
                                    val contents =
                                        input.getInputStream(entry).use { it.readBytes() }
                                    output.writeApkEntry(
                                        name = name,
                                        contents = contents,
                                        method = entry.method,
                                        timestamp = entry.time,
                                        comment = entry.comment,
                                    )
                                }
                            }
                        }
                        if (!wroteDex) throw GradleException("Release APK has no DEX")
                    }
                }
                Files.move(
                    rewrittenApk.toPath(),
                    outputApk.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                temporaryDirectory.deleteRecursively()
            }
        }
    }

val optimizeMinimalReleaseApk =
    tasks.register<Exec>("optimizeMinimalReleaseApk") {
        group = "build"
        description = "Optimizes resources in the minimized unsigned release APK."
        dependsOn(minimizeReleaseApk)
        inputs.file(unalignedMinimalReleaseApk)
        inputs.file(aapt2)
        outputs.file(optimizedUnalignedMinimalReleaseApk)

        doFirst {
            commandLine(
                aapt2.get().asFile,
                "optimize",
                "--collapse-resource-names",
                "-o",
                optimizedUnalignedMinimalReleaseApk.get().asFile,
                unalignedMinimalReleaseApk.get().asFile,
            )
        }
    }

val alignMinimalReleaseApk =
    tasks.register<Exec>("alignMinimalReleaseApk") {
        group = "build"
        description = "Aligns the minimized and resource-optimized unsigned release APK."
        dependsOn(optimizeMinimalReleaseApk)
        inputs.file(optimizedUnalignedMinimalReleaseApk)
        inputs.file(zipalign)
        outputs.file(minimalReleaseApk)

        doFirst {
            commandLine(
                zipalign.get().asFile,
                "-f",
                "-p",
                "4",
                optimizedUnalignedMinimalReleaseApk.get().asFile,
                minimalReleaseApk.get().asFile,
            )
        }
    }

val verifyMinimalReleaseApk =
    tasks.register<Exec>("verifyMinimalReleaseApk") {
        group = "verification"
        description = "Verifies alignment of the minimized release APK."
        dependsOn(alignMinimalReleaseApk)
        inputs.file(minimalReleaseApk)
        inputs.file(zipalign)

        doFirst {
            commandLine(
                zipalign.get().asFile,
                "-c",
                "-p",
                "4",
                minimalReleaseApk.get().asFile,
            )
        }
    }

tasks.register("assembleMinimalRelease") {
    group = "build"
    description = "Builds, minimizes, and verifies the aligned unsigned release APK."
    dependsOn(verifyMinimalReleaseApk)
}
