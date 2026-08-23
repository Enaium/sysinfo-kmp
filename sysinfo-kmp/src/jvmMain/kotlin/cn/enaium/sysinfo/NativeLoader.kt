package cn.enaium.sysinfo

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Resolves and loads the JNI shared library bundled in the matching
 * `sysinfo-kmp-jni-jvm-{os}-{arch}` artifact's resources.
 *
 * Falls back to `java.lang.System.loadLibrary("syskmp")` if no bundled binary matches the
 * host (a developer workflow places the lib on `java.library.path`).
 */
internal object NativeLoader {
    private const val LIB_NAME = "syskmp"
    private const val RESOURCE_BASE = "/cn/enaium/sysinfo/native"

    fun load() {
        val classifier = detectClassifier()
        val isWindows = classifier.startsWith("windows")
        val prefix = if (isWindows) "" else "lib"
        val ext = when {
            isWindows -> "dll"
            classifier.startsWith("darwin") -> "dylib"
            else -> "so"
        }
        val libFile = "$prefix$LIB_NAME.$ext"
        val resourcePath = "$RESOURCE_BASE/$classifier/$libFile"

        val stream = NativeLoader::class.java.getResourceAsStream(resourcePath)
            ?: throw UnsatisfiedLinkError(
                "Resource $resourcePath not found. os=${java.lang.System.getProperty("os.name")}, " +
                    "arch=${java.lang.System.getProperty("os.arch")}, classifier=$classifier. " +
                    "Add the matching sysinfo-kmp-jni-jvm-$classifier dependency to the runtime classpath.",
            )
        val bytes = stream.use { it.readBytes() }
        val target = extractToTemp(bytes, libFile)
        try {
            java.lang.System.load(target.absolutePath)
        } catch (e: UnsatisfiedLinkError) {
            throw UnsatisfiedLinkError(
                "Failed to load $libFile from $resourcePath " +
                    "(os=${java.lang.System.getProperty("os.name")}, " +
                    "arch=${java.lang.System.getProperty("os.arch")}): ${e.message}",
            )
        }
    }

    private fun extractToTemp(bytes: ByteArray, libFile: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
        val tempDir = File(java.lang.System.getProperty("java.io.tmpdir"), "sysinfo-kmp-$hex")
        tempDir.mkdirs()
        val target = File(tempDir, libFile)
        if (!target.isFile || target.length().toInt() != bytes.size) {
            val tmp = File.createTempFile(libFile, ".part", tempDir)
            tmp.writeBytes(bytes)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    private fun detectClassifier(): String {
        val osName = java.lang.System.getProperty("os.name").orEmpty().lowercase()
        val osArch = java.lang.System.getProperty("os.arch").orEmpty().lowercase()
        val os = when {
            osName.contains("linux") -> "linux"
            osName.contains("mac") || osName.contains("darwin") || osName.contains("osx") -> "darwin"
            osName.contains("win") -> "windows"
            else -> error("Unsupported OS for sysinfo-kmp JVM artifact: $osName")
        }
        val arch = when (osArch) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> error("Unsupported CPU architecture for sysinfo-kmp JVM artifact: $osArch")
        }
        return "$os-$arch"
    }
}
