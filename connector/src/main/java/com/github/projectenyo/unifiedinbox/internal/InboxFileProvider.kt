package com.github.projectenyo.unifiedinbox.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.github.projectenyo.unifiedinbox.InboxFile
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves the files a connector hands to its host, read only and only to apps granted a URI.
 * Files are known for the life of the process; the host copies them right away.
 */
class InboxFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String? = shared[uri.lastPathSegment]?.mime

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        val file = shared[uri.lastPathSegment]?.file ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val entry = shared[uri.lastPathSegment] ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> entry.name ?: entry.file.name
                    OpenableColumns.SIZE -> entry.file.length()
                    else -> null
                }
            })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    internal companion object {
        private val shared = ConcurrentHashMap<String, InboxFile>()

        fun authority(context: Context) = "${context.packageName}.unifiedinbox.files"

        /** Makes [file] readable under a new URI for [request]. */
        fun share(context: Context, request: String, file: InboxFile): Uri {
            val key = java.util.UUID.randomUUID().toString() + "-" + request.filter { it.isLetterOrDigit() }.take(32)
            shared[key] = file
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath(key).build()
        }
    }
}
