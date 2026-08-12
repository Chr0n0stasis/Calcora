@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package java.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager

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
        val data = NSData.dataWithContentsOfFile(path) ?: return ""
        val pointer = data.bytes?.reinterpret<ByteVar>() ?: return ""
        return ByteArray(data.length.toInt()) { pointer[it] }.decodeToString()
    }

    fun writeText(text: String) {
        val bytes = text.encodeToByteArray()
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert())
                .writeToFile(path, atomically = true)
        }
    }

    fun delete(): Boolean = NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}
