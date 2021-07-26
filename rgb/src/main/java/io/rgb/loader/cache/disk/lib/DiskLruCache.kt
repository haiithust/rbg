package io.rgb.loader.cache.disk.lib

import okhttp3.internal.closeQuietly
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * A cache that uses a bounded amount of space on a filesystem. Each cache
 * entry has a string key and a fixed number of values. Each key must match
 * the regex **[a-z0-9_-]{1,64}**. Values are byte sequences,
 * accessible as streams or files. Each value must be between `0` and
 * `Integer.MAX_VALUE` bytes in length.
 *
 *
 * The cache stores its data in a directory on the filesystem. This
 * directory must be exclusive to the cache; the cache may delete or overwrite
 * files from its directory. It is an error for multiple processes to use the
 * same cache directory at the same time.
 *
 *
 * This cache limits the number of bytes that it will store on the
 * filesystem. When the number of stored bytes exceeds the limit, the cache will
 * remove entries in the background until the limit is satisfied. The limit is
 * not strict: the cache may temporarily exceed it while waiting for files to be
 * deleted. The limit does not include filesystem overhead or the cache
 * journal so space-sensitive applications should set a conservative limit.
 *
 *
 * Clients call [.edit] to create or update the values of an entry. An
 * entry may have only one editor at one time; if a value is not available to be
 * edited then [.edit] will return null.
 *
 *  * When an entry is being **created** it is necessary to
 * supply a full set of values; the empty value should be used as a
 * placeholder if necessary.
 *  * When an entry is being **edited**, it is not necessary
 * to supply data for every value; values default to their previous
 * value.
 *
 * Every [.edit] call must be matched by a call to [DiskLruCache.Editor.commit]
 * or [DiskLruCache.Editor.abort]. Committing is atomic: a read observes the full set
 * of values as they were before or after the commit, but never a mix of values.
 *
 *
 * Clients call [.get] to read a snapshot of an entry. The read will
 * observe the value at the time that [.get] was called. Updates and
 * removals after the call do not impact ongoing reads.
 *
 *
 * This class is tolerant of some I/O errors. If files are missing from the
 * filesystem, the corresponding entries will be dropped from the cache. If
 * an error occurs while writing a cache value, the edit will fail silently.
 * Callers should handle other problems by catching `IOException` and
 * responding appropriately.
 */
class DiskLruCache private constructor(
    /**
     * Returns the directory where this cache stores its data.
     */
    /*
          * This cache uses a journal file named "journal". A typical journal file
          * looks like this:
          *     libcore.io.DiskLruCache
          *     1
          *     100
          *     2
          *
          *     CLEAN 3400330d1dfc7f3f7f4b8d4d803dfcf6 832 21054
          *     DIRTY 335c4c6028171cfddfbaae1a9c313c52
          *     CLEAN 335c4c6028171cfddfbaae1a9c313c52 3934 2342
          *     REMOVE 335c4c6028171cfddfbaae1a9c313c52
          *     DIRTY 1ab96a171faeeee38496d8b330771a7a
          *     CLEAN 1ab96a171faeeee38496d8b330771a7a 1600 234
          *     READ 335c4c6028171cfddfbaae1a9c313c52
          *     READ 3400330d1dfc7f3f7f4b8d4d803dfcf6
          *
          * The first five lines of the journal form its header. They are the
          * constant string "libcore.io.DiskLruCache", the disk cache's version,
          * the application's version, the value count, and a blank line.
          *
          * Each of the subsequent lines in the file is a record of the state of a
          * cache entry. Each line contains space-separated values: a state, a key,
          * and optional state-specific values.
          *   o DIRTY lines track that an entry is actively being created or updated.
          *     Every successful DIRTY action should be followed by a CLEAN or REMOVE
          *     action. DIRTY lines without a matching CLEAN or REMOVE indicate that
          *     temporary files may need to be deleted.
          *   o CLEAN lines track a cache entry that has been successfully published
          *     and may be read. A publish line is followed by the lengths of each of
          *     its values.
          *   o READ lines track accesses for LRU.
          *   o REMOVE lines track entries that have been deleted.
          *
          * The journal file is appended to as cache operations occur. The journal may
          * occasionally be compacted by dropping redundant lines. A temporary file named
          * "journal.tmp" will be used during compaction; that file should be deleted if
          * it exists when the cache is opened.
          */
    val directory: File, private val appVersion: Int, max: Long
) : Closeable {
    private val journalFile: File = File(directory, JOURNAL_FILE)
    private val journalFileTmp: File = File(directory, JOURNAL_FILE_TEMP)
    private val journalFileBackup: File = File(directory, JOURNAL_FILE_BACKUP)
    private val maxSize: Long = max
    private var size: Long = 0
    private var journalWriter: Writer? = null
    private val lruEntries = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private var redundantOpCount = 0
    /**
     * This cache uses a single background thread to evict entries.
     */
    private val executorService =
        ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val cleanupCallable: Callable<Void> = Callable {
        synchronized(this@DiskLruCache) {
            if (journalWriter == null) {
                return@Callable null // Closed.
            }
            trimToSize()
            if (journalRebuildRequired()) {
                rebuildJournal()
                redundantOpCount = 0
            }
        }
        null
    }

    @Throws(IOException::class)
    private fun readJournal() {
        val reader = FileInputStream(journalFile).bufferedReader(StandardCharsets.US_ASCII)
        redundantOpCount = try {
            val magic = reader.readLine()
            val version = reader.readLine()
            val appVersionString = reader.readLine()
            val blank = reader.readLine()
            if (MAGIC != magic
                || VERSION_1 != version
                || appVersion.toString() != appVersionString
                || "" != blank
            ) {
                throw IOException(
                    "unexpected journal header: [" + magic + ", " + version + ", "
                            + ", " + blank + "]"
                )
            }
            var lineCount = 0
            reader.forEachLine {
                readJournalLine(it)
                lineCount++
            }
            lineCount - lruEntries.size
        } finally {
            reader.closeQuietly()
        }
    }

    @Throws(IOException::class)
    private fun readJournalLine(line: String) {
        val firstSpace = line.indexOf(' ')
        if (firstSpace == -1) {
            throw IOException("unexpected journal line: $line")
        }
        val keyBegin = firstSpace + 1
        val secondSpace = line.indexOf(' ', keyBegin)
        val key: String
        if (secondSpace == -1) {
            key = line.substring(keyBegin)
            if (firstSpace == REMOVE.length && line.startsWith(REMOVE)) {
                lruEntries.remove(key)
                return
            }
        } else {
            key = line.substring(keyBegin, secondSpace)
        }
        val entry = lruEntries.getOrPut(key) { Entry(key) }
        if (secondSpace != -1 && firstSpace == CLEAN.length && line.startsWith(CLEAN)) {
            val parts = line.substring(secondSpace + 1).split(" ".toRegex()).toTypedArray()
            entry.readable = true
            entry.currentEditor = null
            entry.setLengths(parts)
        } else if (secondSpace == -1 && firstSpace == DIRTY.length && line.startsWith(DIRTY)) {
            entry.currentEditor = Editor(entry)
        } else if (secondSpace == -1 && firstSpace == READ.length && line.startsWith(READ)) {
            // This work was already done by calling lruEntries.get().
        } else {
            throw IOException("unexpected journal line: $line")
        }
    }

    /**
     * Computes the initial size and collects garbage as a part of opening the
     * cache. Dirty entries are assumed to be inconsistent and will be deleted.
     */
    @Throws(IOException::class)
    private fun processJournal() {
        deleteIfExists(journalFileTmp)
        val i = lruEntries.values.iterator()
        while (i.hasNext()) {
            val entry = i.next()
            if (entry.currentEditor == null) {
                size += entry.length
            } else {
                entry.currentEditor = null
                deleteIfExists(entry.file)
                deleteIfExists(entry.dirtyFile)
                i.remove()
            }
        }
    }

    /**
     * Creates a new journal that omits redundant information. This replaces the
     * current journal if it exists.
     */
    @Synchronized
    @Throws(IOException::class)
    private fun rebuildJournal() {
        journalWriter?.close()
        val writer: Writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(journalFileTmp), StandardCharsets.US_ASCII)
        )
        writer.use {
            writer.write(MAGIC)
            writer.write("\n")
            writer.write(VERSION_1)
            writer.write("\n")
            writer.write("$appVersion")
            writer.write("\n")
            writer.write("\n")
            for (entry in lruEntries.values) {
                if (entry.currentEditor != null) {
                    writer.write("$DIRTY ${entry.key}")
                    writer.write("\n")
                } else {
                    writer.write("$CLEAN ${entry.key}${entry.lengths}")
                    writer.write("\n")
                }
            }
        }
        if (journalFile.exists()) {
            renameTo(journalFile, journalFileBackup, true)
        }
        renameTo(journalFileTmp, journalFile, false)
        journalFileBackup.delete()
        journalWriter = BufferedWriter(
            OutputStreamWriter(FileOutputStream(journalFile, true), StandardCharsets.US_ASCII)
        )
    }

    /**
     * Returns a snapshot of the entry named `key`, or null if it doesn't
     * exist is not currently readable. If a value is returned, it is moved to
     * the head of the LRU queue.
     */
    @Synchronized
    @Throws(IOException::class)
    operator fun get(key: String): Snapshot? {
        checkNotClosed()
        validateKey(key)
        val entry = lruEntries[key] ?: return null
        if (!entry.readable) {
            return null
        }

        // Open all streams eagerly to guarantee that we see a single published
        // snapshot. If we opened streams lazily then the streams could come
        // from different edits.
        val inputStream: InputStream = try {
            FileInputStream(entry.file)
        } catch (e: FileNotFoundException) {
            return null
        }
        redundantOpCount++
        journalWriter!!.append("$READ $key")
        journalWriter!!.appendLine()
        if (journalRebuildRequired()) {
            executorService.submit(cleanupCallable)
        }
        return Snapshot(inputStream)
    }

    /**
     * Returns an editor for the entry named `key`, or null if another
     * edit is in progress.
     */
    @Throws(IOException::class)
    fun edit(key: String): Editor? {
        checkNotClosed()
        validateKey(key)
        val entry = lruEntries.getOrPut(key) { Entry(key) }
        if (entry.currentEditor != null) return null

        // Flush the journal before creating files to prevent file leaks.
        journalWriter?.apply {
            write("$DIRTY $key")
            write("\n")
            flush()
        }
        return Editor(entry).also {
            entry.currentEditor = it
        }
    }

    /**
     * Returns the number of bytes currently being used to store the values in
     * this cache. This may be greater than the max size if a background
     * deletion is pending.
     */
    @Synchronized
    fun size(): Long {
        return size
    }

    @Synchronized
    @Throws(IOException::class)
    private fun completeEdit(editor: Editor, success: Boolean) {
        val entry = editor.entry
        check(entry.currentEditor == editor)

        // If this edit is creating the entry for the first time, every index must have a value.
        if (success && !entry.readable) {
            if (!editor.written) {
                editor.abort()
                throw IllegalStateException("Newly created entry didn't create value")
            }
            if (!entry.dirtyFile.exists()) {
                editor.abort()
                return
            }
        }
        val dirty = entry.dirtyFile
        if (success) {
            if (dirty.exists()) {
                val clean = entry.file
                dirty.renameTo(clean)
                val oldLength = entry.length
                val newLength = clean.length()
                entry.length = newLength
                size = size - oldLength + newLength
            }
        } else {
            deleteIfExists(dirty)
        }
        redundantOpCount++
        entry.currentEditor = null
        if (entry.readable or success) {
            entry.readable = true
            journalWriter!!.write("$CLEAN ${entry.key}${entry.lengths}")
            journalWriter!!.write("\n")
        } else {
            lruEntries.remove(entry.key)
            journalWriter!!.write("$REMOVE ${entry.key}")
            journalWriter!!.write("\n")
        }
        journalWriter!!.flush()
        if (size > maxSize || journalRebuildRequired()) {
            executorService.submit(cleanupCallable)
        }
    }

    /**
     * We only rebuild the journal when it will halve the size of the journal
     * and eliminate at least 2000 ops.
     */
    private fun journalRebuildRequired(): Boolean {
        val redundantOpCompactThreshold = 2000
        return (redundantOpCount >= redundantOpCompactThreshold //
                && redundantOpCount >= lruEntries.size)
    }

    /**
     * Drops the entry for `key` if it exists and can be removed. Entries
     * actively being edited cannot be removed.
     *
     * @return true if an entry was removed.
     */
    @Synchronized
    @Throws(IOException::class)
    fun remove(key: String): Boolean {
        checkNotClosed()
        validateKey(key)
        val entry = lruEntries[key]
        if (entry == null || entry.currentEditor != null) {
            return false
        }
        val file = entry.file
        if (file.exists() && !file.delete()) {
            throw IOException("failed to delete $file")
        }
        size -= entry.length
        entry.length = 0
        redundantOpCount++
        journalWriter!!.append("$REMOVE $key")
        journalWriter!!.write("\n")
        lruEntries.remove(key)
        if (journalRebuildRequired()) {
            executorService.submit(cleanupCallable)
        }
        return true
    }

    /**
     * Returns true if this cache has been closed.
     */
    @get:Synchronized
    val isClosed: Boolean
        get() = journalWriter == null

    private fun checkNotClosed() {
        checkNotNull(journalWriter) { "cache is closed" }
    }

    /**
     * Force buffered operations to the filesystem.
     */
    @Synchronized
    @Throws(IOException::class)
    fun flush() {
        checkNotClosed()
        trimToSize()
        journalWriter!!.flush()
    }

    /**
     * Closes this cache. Stored values will remain on the filesystem.
     */
    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        journalWriter ?: return
        for (entry in lruEntries.values) {
            entry.currentEditor?.abort()
        }
        trimToSize()
        journalWriter?.close()
        journalWriter = null
    }

    @Throws(IOException::class)
    private fun trimToSize() {
        while (size > maxSize) {
            val toEvict: Map.Entry<String, Entry> = lruEntries.entries.iterator().next()
            remove(toEvict.key)
        }
    }

    /**
     * Closes the cache and deletes all of its stored values. This will delete
     * all files in the cache directory including files that weren't created by
     * the cache.
     */
    @Throws(IOException::class)
    fun delete() {
        close()
        directory.deleteRecursively()
    }

    private fun validateKey(key: String) {
        val matcher = LEGAL_KEY_PATTERN.matcher(key)
        require(matcher.matches()) { "keys must match regex [a-z0-9_-]{1,64}: \"$key\"" }
    }

    /**
     * A snapshot of the values for an entry.
     */
    class Snapshot(
        val inputStream: InputStream,
    ) : Closeable {

        override fun close() {
            inputStream.closeQuietly()
        }
    }

    /**
     * Edits the values for an entry.
     */
    inner class Editor(val entry: Entry) {
        var written = false
        private var hasErrors = false
        private var committed = false

        /**
         * Returns a new unbuffered output stream to write the value at
         * `index`. If the underlying output stream encounters errors
         * when writing to the filesystem, this edit will be aborted when
         * [.commit] is called. The returned output stream does not throw
         * IOExceptions.
         */
        @Throws(IOException::class)
        fun newOutputStream(): OutputStream {
            synchronized(this@DiskLruCache) {
                check(entry.currentEditor == this)
                if (!entry.readable) {
                    written = true
                }
                val dirtyFile = entry.dirtyFile
                val outputStream: FileOutputStream = try {
                    directory.mkdirs()
                    FileOutputStream(dirtyFile)
                } catch (e: FileNotFoundException) {
                    return NULL_OUTPUT_STREAM
                }
                return FaultHidingOutputStream(outputStream)
            }
        }

        /**
         * Commits this edit so it is visible to readers.  This releases the
         * edit lock so another edit may be started on the same key.
         */
        @Throws(IOException::class)
        fun commit() {
            if (hasErrors) {
                completeEdit(this, false)
                remove(entry.key) // The previous entry is stale.
            } else {
                completeEdit(this, true)
            }
            committed = true
        }

        /**
         * Aborts this edit. This releases the edit lock so another edit may be
         * started on the same key.
         */
        @Throws(IOException::class)
        fun abort() {
            completeEdit(this, false)
        }

        private inner class FaultHidingOutputStream(out: OutputStream) : FilterOutputStream(out) {
            override fun write(oneByte: Int) {
                try {
                    out.write(oneByte)
                } catch (e: IOException) {
                    hasErrors = true
                }
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                try {
                    out.write(buffer, offset, length)
                } catch (e: IOException) {
                    hasErrors = true
                }
            }

            override fun close() {
                try {
                    out.close()
                } catch (e: IOException) {
                    hasErrors = true
                }
            }

            override fun flush() {
                try {
                    out.flush()
                } catch (e: IOException) {
                    hasErrors = true
                }
            }
        }
    }

    inner class Entry(val key: String) {
        /**
         * Lengths of this entry's files.
         */
        var length: Long = 0

        /**
         * True if this entry has ever been published.
         */
        var readable = false

        /**
         * The ongoing edit or null if this entry is not being edited.
         */
        var currentEditor: Editor? = null

        @get:Throws(IOException::class)
        val lengths: String
            get() = " $length"

        /**
         * Set lengths using decimal numbers like "10123".
         */
        @Throws(IOException::class)
        fun setLengths(strings: Array<String>) {
            if (strings.size != 1) {
                throw invalidLengths(strings)
            }
            length = try {
                strings[0].toLong()
            } catch (e: NumberFormatException) {
                throw invalidLengths(strings)
            }
        }

        @Throws(IOException::class)
        private fun invalidLengths(strings: Array<String>): IOException {
            throw IOException("unexpected journal line: " + Arrays.toString(strings))
        }

        val file: File
            get() = File(directory, key)
        val dirtyFile: File
            get() = File(directory, "$key.tmp")
    }

    companion object {
        const val JOURNAL_FILE = "journal"
        const val JOURNAL_FILE_TEMP = "journal.tmp"
        const val JOURNAL_FILE_BACKUP = "journal.bkp"
        const val MAGIC = "libcore.io.DiskLruCache"
        const val VERSION_1 = "1"
        private val LEGAL_KEY_PATTERN: Pattern = Pattern.compile("[a-z0-9_-]{1,64}")
        private const val CLEAN = "CLEAN"
        private const val DIRTY = "DIRTY"
        private const val REMOVE = "REMOVE"
        private const val READ = "READ"

        /**
         * Opens the cache in `directory`, creating a cache if none exists
         * there.
         *
         * @param directory  a writable directory
         * @param maxSize    the maximum number of bytes this cache should use to store
         * @throws IOException if reading or writing the cache directory fails
         */
        @Throws(IOException::class)
        fun open(directory: File, appVersion: Int, maxSize: Long): DiskLruCache {
            require(maxSize > 0) { "maxSize <= 0" }

            // If a bkp file exists, use it instead.
            val backupFile = File(directory, JOURNAL_FILE_BACKUP)
            if (backupFile.exists()) {
                val journalFile = File(directory, JOURNAL_FILE)
                // If journal file also exists just delete backup file.
                if (journalFile.exists()) {
                    backupFile.delete()
                } else {
                    renameTo(backupFile, journalFile, false)
                }
            }

            // Prefer to pick up where we left off.
            var cache = DiskLruCache(directory, appVersion, maxSize)
            if (cache.journalFile.exists()) {
                try {
                    cache.readJournal()
                    cache.processJournal()
                    cache.journalWriter = BufferedWriter(
                        OutputStreamWriter(
                            FileOutputStream(cache.journalFile, true),
                            StandardCharsets.US_ASCII
                        )
                    )
                    return cache
                } catch (journalIsCorrupt: IOException) {
                    println(
                        "DiskLruCache "
                                + directory
                                + " is corrupt: "
                                + journalIsCorrupt.message
                                + ", removing"
                    )
                    cache.delete()
                }
            }

            // Create a new empty cache.
            directory.mkdirs()
            cache = DiskLruCache(directory, appVersion, maxSize)
            cache.rebuildJournal()
            return cache
        }

        @Throws(IOException::class)
        private fun deleteIfExists(file: File) {
            if (file.exists() && !file.delete()) {
                throw IOException()
            }
        }

        @Throws(IOException::class)
        private fun renameTo(from: File, to: File, deleteDestination: Boolean) {
            if (deleteDestination) {
                deleteIfExists(to)
            }
            if (!from.renameTo(to)) {
                throw IOException()
            }
        }

        private val NULL_OUTPUT_STREAM: OutputStream = object : OutputStream() {
            @Throws(IOException::class)
            override fun write(b: Int) {
                // Eat all writes silently. Nom nom.
            }
        }
    }

}