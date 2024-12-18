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

package com.teixeira.vcspace.ui.filetree

import android.os.Environment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.teixeira.vcspace.activities.base.LocalLifecycleScope
import com.teixeira.vcspace.databinding.LayoutFileTreeBinding
import com.teixeira.vcspace.file.File
import com.teixeira.vcspace.file.newFile
import com.teixeira.vcspace.file.toFile
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeView
import kotlinx.coroutines.launch
import java.io.File as JFile

@Composable
fun FileTree(
  modifier: Modifier = Modifier,
  path: File = JFile(Environment.getExternalStorageDirectory().absolutePath).toFile(),
  onFileLongClick: (File) -> Unit = {},
  onFileClick: (File) -> Unit
) {
  val fileListLoader = rememberSaveable { FileListLoader() }
  val tree = remember { createTree(fileListLoader, path) }

  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  val lifecycleScope = LocalLifecycleScope.current
  AndroidViewBinding(LayoutFileTreeBinding::inflate, modifier) {
    @Suppress("UNCHECKED_CAST")
    (treeview as TreeView<File>).apply {
      supportHorizontalScroll = true
      bindCoroutineScope(lifecycleScope)
      this.tree = tree
      binder = FileViewBinder(
        fileTreeBinding = this@AndroidViewBinding,
        fileListLoader = fileListLoader,
        onFileLongClick = onFileLongClick,
        onFileClick = onFileClick,
        onSurfaceColor = onSurfaceColor
      )
      nodeEventListener = binder as FileViewBinder
      selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
    }

    lifecycleScope.launch {
      fileListLoader.loadFileList(path)
      treeview.refresh()
    }
  }
}

private fun createTree(
  fileListLoader: FileListLoader,
  rootPath: File
): Tree<File> {
  val tree = Tree.createTree<File>()

  tree.apply {
    this.generator = FileNodeGenerator(
      rootPath,
      fileListLoader
    )

    initTree()
  }

  return tree
}