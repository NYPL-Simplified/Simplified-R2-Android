package org.librarysimplified.r2.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import io.reactivex.disposables.Disposable
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkCreated
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarksLoaded
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionFailed
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionSucceeded
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandExecutionRunningLong
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandExecutionStarted
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2ChapterNonexistent
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2WebViewInaccessible
import org.librarysimplified.r2.api.SR2Event.SR2ExternalLinkSelected
import org.librarysimplified.r2.api.SR2Event.SR2OnCenterTapped
import org.librarysimplified.r2.api.SR2Event.SR2ReadingPositionChanged
import org.librarysimplified.r2.api.SR2Event.SR2ScrollingModeChanged
import org.librarysimplified.r2.api.SR2Event.SR2ThemeChanged
import org.librarysimplified.r2.ui_thread.SR2UIThread
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2ControllerReference
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderFragmentFactory
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationClose
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC
import org.librarysimplified.r2.views.SR2ReaderViewModel
import org.librarysimplified.r2.views.SR2ReaderViewModelFactory
import org.librarysimplified.r2.views.SR2TOCFragment
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.parser.epub.EpubParser
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

class DemoActivity : AppCompatActivity() {

  companion object {
    const val PICK_DOCUMENT = 1001
  }

  private val logger =
    LoggerFactory.getLogger(DemoActivity::class.java)

  private lateinit var readerFragmentFactory: SR2ReaderFragmentFactory
  private lateinit var readerParameters: SR2ReaderParameters
  private var controller: SR2ControllerType? = null
  private var controllerSubscription: Disposable? = null
  private var epubFile: File? = null
  private var epubId: String? = null
  private var viewSubscription: Disposable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      this.setContentView(R.layout.demo_fragment_host)

      val browseButton = this.findViewById<Button>(R.id.browse_button)!!
      browseButton.setOnClickListener { this.startDocumentPickerForResult() }
    }
  }

  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    super.onActivityResult(requestCode, resultCode, data)

    when (requestCode) {
      PICK_DOCUMENT -> this.onPickDocumentResult(resultCode, data)
    }
  }

  override fun onStart() {
    super.onStart()
    this.epubFile?.let(this::startReader)
  }

  override fun onStop() {
    super.onStop()
    this.controllerSubscription?.dispose()
    this.viewSubscription?.dispose()
  }

  private fun onControllerBecameAvailable(reference: SR2ControllerReference) {
    this.controller = reference.controller

    // Listen for messages from the controller.
    this.controllerSubscription =
      reference.controller.events.subscribe(this::onControllerEvent)

    if (reference.isFirstStartup) {
      // Navigate to the first chapter or saved reading position.
      val database = DemoApplication.application.database()
      val bookId = reference.controller.bookMetadata.id
      val lastRead = database.bookmarkFindLastReadLocation(bookId, reference.controller.bookMetadata)
      reference.controller.submitCommand(SR2Command.BookmarksLoad(database.bookmarksFor(bookId)))
      reference.controller.submitCommand(SR2Command.OpenChapter(lastRead.locator))
      // reference.controller.submitCommand(SR2Command.ScrollingModeSet(SR2ScrollingMode.SCROLLING_MODE_ENABLED))
    } else {
      // Refresh whatever the controller was looking at previously.
      reference.controller.submitCommand(SR2Command.Refresh)
    }
  }

  /**
   * Start the reader with the given EPUB.
   */

  @UiThread
  private fun startReader(file: File) {
    SR2UIThread.checkIsUIThread()

    val streamer =
      Streamer(
        context = this,
        parsers = listOf(EpubParser()),
        ignoreDefaultParsers = true
      )

    val database =
      DemoApplication.application.database()

    this.readerParameters =
      SR2ReaderParameters(
        streamer = streamer,
        bookFile = FileAsset(file),
        bookId = this.epubId!!,
        theme = database.theme(),
        controllers = SR2Controllers()
      )

    this.readerFragmentFactory =
      SR2ReaderFragmentFactory(this.readerParameters)

    val readerModel =
      ViewModelProvider(this, SR2ReaderViewModelFactory(this.readerParameters))
        .get(SR2ReaderViewModel::class.java)

    this.viewSubscription =
      readerModel.viewEvents.subscribe(this::onViewEvent)

    this.supportFragmentManager.beginTransaction()
      .replace(R.id.demoFragmentArea, this.readerFragmentFactory.instantiate(this.classLoader, SR2ReaderFragment::class.java.name))
      .commit()
  }

  /**
   * Handle incoming messages from the view fragments.
   */

  private fun onViewEvent(event: SR2ReaderViewEvent) {
    SR2UIThread.checkIsUIThread()

    return when (event) {
      SR2ReaderViewNavigationClose ->
        this.supportFragmentManager.popBackStack()
      SR2ReaderViewNavigationOpenTOC ->
        this.openTOC()
      is SR2ControllerBecameAvailable ->
        this.onControllerBecameAvailable(event.reference)
      is SR2BookLoadingFailed ->
        this.onBookLoadingFailed(event.exception)
    }
  }

  private fun onBookLoadingFailed(exception: Throwable) {
    AlertDialog.Builder(this)
      .setMessage(exception.message)
      .setOnDismissListener { this.finish() }
      .create()
      .show()
  }

  private fun openTOC() {
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.demoFragmentArea, this.readerFragmentFactory.instantiate(this.classLoader, SR2TOCFragment::class.java.name))
      .addToBackStack(null)
      .commit()
  }

  /**
   * Handle incoming messages from the controller.
   */

  private fun onControllerEvent(event: SR2Event) {
    return when (event) {
      is SR2BookmarkCreated -> {
        val database = DemoApplication.application.database()
        database.bookmarkSave(this.controller!!.bookMetadata.id, event.bookmark)
      }

      is SR2BookmarkDeleted -> {
        val database = DemoApplication.application.database()
        database.bookmarkDelete(this.controller!!.bookMetadata.id, event.bookmark)
      }

      is SR2ThemeChanged -> {
        val database = DemoApplication.application.database()
        database.themeSet(event.theme)
      }

      is SR2OnCenterTapped,
      is SR2ReadingPositionChanged,
      SR2BookmarksLoaded,
      is SR2ChapterNonexistent,
      is SR2WebViewInaccessible,
      is SR2ExternalLinkSelected,
      is SR2ScrollingModeChanged,
      is SR2CommandExecutionStarted,
      is SR2CommandExecutionRunningLong,
      is SR2CommandExecutionSucceeded,
      is SR2CommandExecutionFailed -> {
        // Nothing
      }
    }
  }

  /**
   * Handle the result of a pick document intent.
   */

  private fun onPickDocumentResult(resultCode: Int, intent: Intent?) {
    // Assume the user picked a valid EPUB file. In reality, we'd want to verify
    // this is a supported file type.
    if (resultCode == Activity.RESULT_OK) {
      intent?.data?.let { uri ->
        // This copy operation should be done on a worker thread; omitted for brevity.
        val data = this.copyToStorage(uri)!!
        this.epubFile = data.first
        this.epubId = data.second
      }
    }
  }

  /**
   * Present the user with an error message.
   */

  private fun showError(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
  }

  /**
   * Copy the book to internal storage; returns null if an error occurs.
   */

  private fun copyToStorage(uri: Uri): Pair<File, String>? {
    val file = File(this.filesDir, "book.epub")
    var ips: InputStream? = null
    var ops: OutputStream? = null

    try {
      ips = this.contentResolver.openInputStream(uri)
      ops = file.outputStream()
      ips.copyTo(ops)
      return Pair(file, hashOf(file))
    } catch (e: FileNotFoundException) {
      this.logger.warn("File not found", e)
      this.showError("File not found")
    } catch (e: IOException) {
      this.logger.warn("Error copying file", e)
      this.showError("Error copying file")
    } finally {
      ips?.close()
      ops?.close()
    }
    return null
  }

  private fun hashOf(
    file: File
  ): String {
    val digest = MessageDigest.getInstance("SHA-256")

    DigestInputStream(file.inputStream(), digest).use { input ->
      NullOutputStream().use { output ->
        input.copyTo(output)
        return digest.digest().joinToString("") { "%02x".format(it) }
      }
    }
  }

  private class NullOutputStream : OutputStream() {
    override fun write(b: Int) {
    }
  }

  /**
   * Present the native document picker and prompt the user to select an EPUB.
   */

  private fun startDocumentPickerForResult() {
    val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      this.type = "*/*"
      this.addCategory(Intent.CATEGORY_OPENABLE)

      // Filter by MIME type; Android versions prior to Marshmallow don't seem
      // to understand the 'application/epub+zip' MIME type.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.putExtra(
          Intent.EXTRA_MIME_TYPES,
          arrayOf("application/epub+zip")
        )
      }
    }
    this.startActivityForResult(pickIntent, PICK_DOCUMENT)
  }
}
