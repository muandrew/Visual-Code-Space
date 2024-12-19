/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.file

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.blankj.utilcode.util.FileIOUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.File as JFile

fun JFile.toFile(): File = InternalJFile(this)

@Parcelize
private data class InternalJFile(private val raw: JFile) : File {
  override val absolutePath: String
    get() = raw.absolutePath
  override val canonicalPath: String
    get() = raw.canonicalPath
  override val isDirectory: Boolean
    get() = raw.isDirectory
  override val isFile: Boolean
    get() = raw.isFile
  override val isValidText: Boolean
    get() = true
  override val name: String
    get() = raw.name
  //TODO: consider pulling this out
  override val mimeType: String?
    get() = null
  override val parent: String?
    get() = raw.parent
  override val parentFile: File?
    get() = raw.parentFile?.let { InternalJFile(it) }
  override val path: String
    get() = raw.path

  override fun newFile(child: String): File
    = JFile(raw, child).toFile()

  override fun listFiles(): Array<out File>?
    = raw.listFiles()?.map { InternalJFile(it) }?.toTypedArray()

  override fun mkdirs(): Boolean = raw.mkdirs()

  override fun renameTo(newName: String): File? {
    val dest = JFile(raw.parentFile, newName)
    return if (raw.renameTo(dest)) InternalJFile(dest) else null
  }

  override fun uri(context: Context): Uri =
    FileProvider.getUriForFile(
      context,
      "$context.packageName.provider",
      raw
    )

  override suspend fun readFile2String(): String?
    = FileIOUtils.readFile2String(raw)

  override suspend fun write(
    content: String,
    ioDispatcher: CoroutineDispatcher,
  ): Boolean = withContext(ioDispatcher) {
    FileIOUtils.writeFileFromString(raw, content)
  }


  override fun asRawFile(): JFile = raw
  override fun createNewFile(): Boolean = raw.createNewFile()
  override fun delete(): Boolean = raw.delete()
  override fun exists(): Boolean = raw.exists()
  override fun lastModified(): Long =
    raw.lastModified()
}

data class InternalDFile(
  private val context: Context,
  private val raw: DocumentFile,
): File {
  override val absolutePath: String
    get() = raw.uri.toString()
  // TODO: difference between this and absolute
  override val canonicalPath: String
    get() = raw.uri.toString()
  // TODO: not a good idea since this can change underneath
  private val _isDirectory = lazy { raw.isDirectory }
  override val isDirectory: Boolean
    get() = _isDirectory.value
  override val isFile: Boolean
    get() = raw.isFile
  override val isValidText: Boolean
    get() = true
  // TODO: not a good idea since this can change underneath
  private val _name = lazy { raw.name ?: "(unknown)" }
  override val name: String
    get() = _name.value
  override val mimeType: String?
    get() = raw.type
  override val parent: String?
    get() = raw.parentFile?.uri?.toString()
  override val parentFile: File?
    get() = raw.parentFile?.let { InternalDFile(context, it) }
  // TODO: make sure this is correct
  override val path: String
    get() = raw.uri.path ?: "UNKNOWN"

  override fun newFile(child: String): File {
    TODO("Not yet implemented")
  }

  override fun listFiles(): Array<out File>
    = raw.listFiles().map { InternalDFile(context, it) }.toTypedArray()

  override fun mkdirs(): Boolean {
    TODO("Not yet implemented")
  }

  override fun renameTo(newName: String): File? =
    if (raw.renameTo(newName)) {
      this
    } else null

  override fun uri(context: Context): Uri = raw.uri

  override suspend fun readFile2String(): String {
    val inputStream = context.contentResolver.openInputStream(raw.uri)
    val reader = BufferedReader(InputStreamReader(inputStream))
    return reader.readLines().joinToString("\n")
  }

  override suspend fun write(
    content: String,
    ioDispatcher: CoroutineDispatcher,
  ): Boolean = withContext(ioDispatcher) {
    var outputStream: OutputStream? = null
    try {
      outputStream = context.contentResolver.openOutputStream(raw.uri,"wt")
      val checkedOutputStream = outputStream ?: return@withContext false
      checkedOutputStream.write(content.toByteArray())
    } catch (t : Throwable) {
      return@withContext false
    } finally {
      outputStream?.close()
    }
    true
  }

  override fun asRawFile(): JFile? = null

  // TODO: audit use of create new file as semantics are different
  override fun createNewFile(): Boolean {
    TODO("Not yet implemented")
  }

  override fun delete(): Boolean = raw.delete()
  override fun exists(): Boolean = raw.exists()
  override fun lastModified(): Long = raw.lastModified()

  override fun writeToParcel(parcel: Parcel, flags: Int) {
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<InternalDFile> {

    override fun createFromParcel(parcel: Parcel): InternalDFile {
      return TODO("not implemented yet")
    }

    override fun newArray(size: Int): Array<InternalDFile?> {
      return arrayOfNulls(size)
    }
  }
}