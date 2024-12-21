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

package com.teixeira.vcspace.activities

import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.UriUtils
import com.google.ai.client.generativeai.type.asTextOrNull
import com.teixeira.vcspace.BuildConfig
import com.teixeira.vcspace.activities.Editor.LocalEditorDrawerNavController
import com.teixeira.vcspace.activities.Editor.LocalEditorDrawerState
import com.teixeira.vcspace.activities.Editor.LocalEditorSnackbarHostState
import com.teixeira.vcspace.activities.base.BaseComposeActivity
import com.teixeira.vcspace.activities.base.ObserveLifecycleEvents
import com.teixeira.vcspace.app.DoNothing
import com.teixeira.vcspace.app.noLocalProvidedFor
import com.teixeira.vcspace.app.strings
import com.teixeira.vcspace.core.ai.Gemini
import com.teixeira.vcspace.editor.addBlockComment
import com.teixeira.vcspace.editor.addSingleComment
import com.teixeira.vcspace.editor.events.OnContentChangeEvent
import com.teixeira.vcspace.editor.events.OnKeyBindingEvent
import com.teixeira.vcspace.events.OnOpenFolderEvent
import com.teixeira.vcspace.extensions.open
import com.teixeira.vcspace.extensions.toFile
import com.teixeira.vcspace.file.File
import com.teixeira.vcspace.file.toFile
import com.teixeira.vcspace.github.auth.Api
import com.teixeira.vcspace.github.auth.UserInfo
import com.teixeira.vcspace.keyboard.CommandPaletteManager
import com.teixeira.vcspace.keyboard.model.Command.Companion.newCommand
import com.teixeira.vcspace.plugins.Manifest
import com.teixeira.vcspace.preferences.pluginsPath
import com.teixeira.vcspace.ui.components.ai.GenerateContentDialog
import com.teixeira.vcspace.ui.screens.editor.EditorScreen
import com.teixeira.vcspace.ui.screens.editor.EditorViewModel
import com.teixeira.vcspace.ui.screens.editor.components.EditorDrawerSheet
import com.teixeira.vcspace.ui.screens.editor.components.EditorTopBar
import com.teixeira.vcspace.ui.screens.editor.components.view.CodeEditorView
import com.teixeira.vcspace.ui.screens.file.FileExplorerViewModel
import com.teixeira.vcspace.utils.launchWithProgressDialog
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

object Editor {
  val LocalEditorDrawerState = compositionLocalOf<DrawerState> {
    noLocalProvidedFor("LocalEditorDrawerState")
  }

  val LocalCommandPaletteManager = staticCompositionLocalOf {
    CommandPaletteManager.instance
  }

  val LocalEditorDrawerNavController = compositionLocalOf<NavHostController> {
    noLocalProvidedFor("LocalEditorDrawerNavController")
  }

  val LocalEditorSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    noLocalProvidedFor("LocalEditorSnackbarHostState")
  }
}

class EditorActivity : BaseComposeActivity() {
  companion object {
    private const val TAG = "EditorActivity"

    const val EXTRA_KEY_PLUGIN_MANIFEST = "plugin_manifest"

    val LAST_OPENED_FILES_JSON_PATH =
      "${PathUtils.getExternalAppFilesPath()}/settings/lastOpenedFile.json"
  }

  private val editorViewModel: EditorViewModel by viewModels()

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onContentChangeEvent(e: OnContentChangeEvent) {
    Log.d("EditorActivity", "Content change event received: ${e.file?.name}")

    val file = e.file
    if (file != null) {
      editorViewModel.setModified(
        file,
        e.event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT
      )
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onKeyBindingEvent(event: OnKeyBindingEvent) {
    editorViewModel.setCanEditorHandleCurrentKeyBinding(event.canEditorHandle)
  }

  private fun onCreate() {
    CommandPaletteManager.instance.clear()

    CommandPaletteManager.instance.addCommand(
      newCommand("Paste", "Ctrl+V") {
        val editor = currentEditor?.editor
        val canEditorHandle = canEditorHandleCurrentKeyBinding

        if (!canEditorHandle) {
          editor?.pasteText()
        }
      },
      newCommand("Copy", "Ctrl+C") {
        val editor = currentEditor?.editor
        val canEditorHandle = canEditorHandleCurrentKeyBinding

        if (!canEditorHandle && editor?.cursor?.isSelected == true) {
          editor.copyText()
        }
      },
      newCommand("Cut", "Ctrl+X") {
        val editor = currentEditor?.editor
        val canEditorHandle = canEditorHandleCurrentKeyBinding

        if (!canEditorHandle && editor?.cursor?.isSelected == true) {
          editor.cutText()
        }
      },
      newCommand("Copy Path of Active File", "Alt+Shift+C") {
        val file = currentEditor?.file
        ClipboardUtils.copyText(file?.absolutePath)
        ToastUtils.showShort("Copied path of active file: ${file?.name}")
      },
      newCommand("Copy File Name", "Alt+Shift+F") {
        val file = currentEditor?.file
        ClipboardUtils.copyText(file?.name)
        ToastUtils.showShort("Copied file name: ${file?.name}")
      },
      newCommand("Undo", "Ctrl+Z") {
        val editor = currentEditor?.editor
        val canEditorHandle = canEditorHandleCurrentKeyBinding

        if (!canEditorHandle) {
          editor?.undo()
        }
      },
      newCommand("Redo", "Ctrl+Y") {
        val editor = currentEditor?.editor
        val canEditorHandle = canEditorHandleCurrentKeyBinding

        if (!canEditorHandle) {
          editor?.redo()
        }
      },
      newCommand("Toggle Line Comment", "Ctrl+/") {
        val editor = currentEditor?.editor
        val canEditorHandle = canEditorHandleCurrentKeyBinding

        val commentRule = editor?.commentRule
        if (editor != null && !canEditorHandle) {
          if (!editor.cursor.isSelected) {
            addSingleComment(commentRule, editor.text)
          } else {
            addBlockComment(commentRule, editor.text)
          }
        }
      },
      newCommand("Settings", "Ctrl+,") {
        open(SettingsActivity::class.java)
      },
      newCommand("Manage Plugins", "Ctrl+P") {
        open(PluginsActivity::class.java)
      },
      newCommand(getString(strings.editor_action_import_components), "Ctrl+Shift+I") {
        val editor = currentEditor?.editor
        editor?.onImportComponentListener?.onImport(editor.text)
      },
      newCommand(getString(strings.generate_code), "Alt+I") { compositionContext ->
        currentEditor ?: return@newCommand
        val editor = currentEditor!!.editor

        val composeView = ComposeView(this@EditorActivity).apply {
          setParentCompositionContext(compositionContext)
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
          setContent { GenerateContentDialog(editor) }
        }

        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(composeView)
      }
    )
  }

  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onFolderOpened(event: OnOpenFolderEvent) {
    currentEditor ?: return
    val editor = currentEditor!!.editor
  }

  @Composable
  override fun MainScreen() {
    ProvideEditorCompositionLocals {
      val fileExplorerViewModel: FileExplorerViewModel = viewModel()
      val editorViewModel: EditorViewModel = viewModel()

      DoLifecycleThings(editorViewModel)

      val snackbarHostState = LocalEditorSnackbarHostState.current
      val drawerState = LocalEditorDrawerState.current

      ModalNavigationDrawer(
        modifier = Modifier
          .fillMaxSize()
          .imePadding(),
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
          ModalDrawerSheet(
            drawerState = drawerState,
            modifier = Modifier.fillMaxWidth(fraction = 0.85f),
            drawerContainerColor = MaterialTheme.colorScheme.background,
            drawerContentColor = contentColorFor(MaterialTheme.colorScheme.background)
          ) {
            EditorDrawerSheet(
              fileExplorerViewModel = fileExplorerViewModel,
              editorViewModel = editorViewModel
            )
          }
        }
      ) {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          topBar = {
            EditorTopBar(
              editorViewModel = editorViewModel
            )
          },
          snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
          EditorScreen(
            viewModel = editorViewModel,
            fileExplorerViewModel = fileExplorerViewModel,
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          )
        }
      }
    }
  }

  @Composable
  private fun ProvideEditorCompositionLocals(content: @Composable () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerNavController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
      LocalEditorDrawerState provides drawerState,
      LocalEditorDrawerNavController provides drawerNavController,
      LocalEditorSnackbarHostState provides snackbarHostState,
      content = content
    )
  }

  private fun clearCache(): Boolean {
    return FileUtils.deleteAllInDir(cacheDir).also {
      Log.i(TAG, "Cache cleared 😊")
    }
  }

  @Composable
  private fun DoLifecycleThings(editorViewModel: EditorViewModel) {
    val snackbarHostState = LocalEditorSnackbarHostState.current

    ObserveLifecycleEvents { event ->
      when (event) {
        Lifecycle.Event.ON_CREATE -> {
          EventBus.getDefault().register(this@EditorActivity)

          // Open plugin files if opened from PluginsActivity
          run {
            @Suppress("DEPRECATION")
            val manifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              intent.getSerializableExtra(EXTRA_KEY_PLUGIN_MANIFEST, Manifest::class.java)
            } else intent.getSerializableExtra(EXTRA_KEY_PLUGIN_MANIFEST) as? Manifest

            if (manifest != null) {
              val pluginPath = "$pluginsPath/${manifest.packageName}"
              val filesToOpen = listOf(
                "$pluginPath/manifest.json".toFile().toFile(),
                "$pluginPath/${manifest.scripts.first().name}".toFile().toFile()
              )
              editorViewModel.addFiles(filesToOpen)
            }
          }

          val externalFileUri = intent.data
          if (externalFileUri != null &&
            !externalFileUri.toString().startsWith(BuildConfig.OAUTH_REDIRECT_URL)
          ) {
            // TODO add file thing.
            editorViewModel.addFile(UriUtils.uri2File(externalFileUri).toFile())
          }

          onCreate()
        }

        Lifecycle.Event.ON_PAUSE -> {
          editorViewModel.rememberLastFiles()
        }

        Lifecycle.Event.ON_DESTROY -> {
          editorViewModel.rememberLastFiles()
          EventBus.getDefault().unregister(this@EditorActivity)
          clearCache()
        }

        Lifecycle.Event.ON_START -> DoNothing
        Lifecycle.Event.ON_RESUME -> {
          val code = intent?.data?.getQueryParameter("code")

          if (!code.isNullOrEmpty()) {
            runCatching {
              lifecycleScope.launch {
                Api.exchangeCodeForToken(
                  code = code,
                  onSuccess = { accessToken ->
                    Api.getUser(
                      token = accessToken.accessToken,
                      onSuccess = { user ->
                        runCatching {
                          Api.saveUser(UserInfo(user, accessToken))
                          snackbarHostState.showSnackbar("Logged in as ${user.username}")
                        }.onFailure {
                          snackbarHostState.showSnackbar("Error: ${it.message}")
                        }
                      },
                      onFailure = {
                        it.printStackTrace()
                        ToastUtils.showShort("Error: ${it.message}")
                      }
                    )
                  },
                  onFailure = {
                    it.printStackTrace()
                    ToastUtils.showShort("Error: ${it.message}")
                  }
                )
              }
            }.onFailure {
              it.printStackTrace()
              lifecycleScope.launch {
                snackbarHostState.showSnackbar(it.message ?: "")
              }
            }
          }
        }

        Lifecycle.Event.ON_STOP -> DoNothing
        Lifecycle.Event.ON_ANY -> DoNothing
      }
    }
  }

  val currentEditor get() = editorViewModel.getSelectedEditor()
  val selectedFileIndex get() = editorViewModel.uiState.value.selectedFileIndex
  val canEditorHandleCurrentKeyBinding get() = editorViewModel.canEditorHandleCurrentKeyBinding.value

  val editorForFile = { file: File -> editorViewModel.getEditorForFile(file) }

  @JvmField
  val openFile = { file: File -> editorViewModel.addFile(file) }

  @JvmField
  val closeFile = { index: Int -> editorViewModel.closeFile(index) }

  @JvmField
  val closeAll = { editorViewModel.closeAll() }

  @JvmField
  val closeOthers = { index: Int -> editorViewModel.closeOthers(index) }

  @JvmField
  val saveAll = { lifecycleScope.launch { editorViewModel.saveAll() } }

  @JvmOverloads
  fun saveFile(codeEditorView: CodeEditorView? = null) {
    lifecycleScope.launch {
      editorViewModel.saveFile(codeEditorView)
    }
  }
}
