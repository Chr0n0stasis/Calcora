@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package java.io

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rewind

/** Small java.io.File compatibility surface used by the existing script editor. */
class File(val path: String) {
    constructor(parent: File, child: String) : this(parent.path.trimEnd('/') + "/" + child)

    val name: String get() = path.substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.', name)

    fun mkdirs(): Boolean = NSFileManager.defaultManager.createDirectoryAtPath(
        path, withIntermediateDirectories = true, attributes = null, error = null
    )

    fun listFiles(): Array<File>? = NSFileManager.defaultManager
        .contentsOfDirectoryAtPath(path, error = null)
        ?.mapNotNull { it as? String }
        ?.map { File(this, it) }
        ?.toTypedArray()

    fun exists(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    fun readText(): String {
        val stream = fopen(path, "rb") ?: return ""
        return try {
            if (fseek(stream, 0, SEEK_END) != 0) return ""
            val size = ftell(stream)
            if (size <= 0) return ""
            rewind(stream)
            val bytes = ByteArray(size.toInt())
            bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), stream)
            }
            bytes.decodeToString()
        } finally {
            fclose(stream)
        }
    }

    fun writeText(text: String) {
        val bytes = text.encodeToByteArray()
        val stream = fopen(path, "wb") ?: return
        try {
            if (bytes.isNotEmpty()) bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), stream)
            }
        } finally {
            fclose(stream)
        }
    }

    fun delete(): Boolean = NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}
