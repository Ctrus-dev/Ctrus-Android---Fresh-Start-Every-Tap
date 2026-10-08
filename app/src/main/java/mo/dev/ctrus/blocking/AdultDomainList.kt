package mo.dev.ctrus.blocking

import android.content.Context
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteOrder
import java.nio.LongBuffer
import java.nio.channels.FileChannel

/**
 * Built-in adult-website list behind "Block Adult Websites". iOS gets this from Apple's own
 * system content filter; Android offers apps nothing equivalent, so Ctrus ships a list (The Block
 * List Project, ~940k domains; see assets/blocklists/NOTICE.txt and tools/build_adult_blocklist.py)
 * and checks it with the same browser address-bar reading as per-site blocking. That means it only
 * covers supported browsers, and only the sites on the list.
 *
 * The asset holds sorted 64-bit FNV-1a hashes of each domain. It's stored uncompressed in the APK
 * and memory-mapped, so a lookup is a binary search over the file with nothing loaded into the
 * heap (the Accessibility service process stays small). It's only opened on first use.
 */
object AdultDomainList {
    private const val ASSET = "blocklists/adult_domains.bin"
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xCBF29CE484222325
    private const val FNV_PRIME = 0x100000001B3L

    @Volatile private var appContext: Context? = null
    @Volatile private var hashes: LongBuffer? = null
    @Volatile private var loadFailed = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** True if [host] or any parent domain of it (m.example.com → example.com) is on the list. */
    fun contains(host: String): Boolean {
        val buffer = buffer() ?: return false
        var candidate = host.lowercase().removePrefix("www.").trimEnd('.')
        while ('.' in candidate) {
            if (binarySearch(buffer, fnv1a64(candidate))) return true
            candidate = candidate.substringAfter('.')
        }
        return false
    }

    private fun buffer(): LongBuffer? {
        hashes?.let { return it }
        if (loadFailed) return null
        synchronized(this) {
            hashes?.let { return it }
            val context = appContext ?: return null
            return runCatching {
                context.assets.openFd(ASSET).use { fd ->
                    FileInputStream(fd.fileDescriptor).channel.use { channel ->
                        channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
                            .order(ByteOrder.BIG_ENDIAN)
                            .asLongBuffer()
                    }
                }
            }.onFailure {
                loadFailed = true
                Log.e("AdultDomainList", "Couldn't open $ASSET", it)
            }.getOrNull().also { hashes = it }
        }
    }

    private fun binarySearch(buffer: LongBuffer, target: Long): Boolean {
        var low = 0
        var high = buffer.limit() - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = buffer.get(mid)
            when {
                value < target -> low = mid + 1
                value > target -> high = mid - 1
                else -> return true
            }
        }
        return false
    }

    /** Same hash as tools/build_adult_blocklist.py: FNV-1a 64 over the UTF-8 bytes. */
    internal fun fnv1a64(text: String): Long {
        var hash = FNV_OFFSET
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }
}
