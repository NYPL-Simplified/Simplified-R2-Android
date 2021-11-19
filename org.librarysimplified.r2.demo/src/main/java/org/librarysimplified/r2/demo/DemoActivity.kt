package org.librarysimplified.r2.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2PageNumberingMode
import org.librarysimplified.r2.api.SR2ScrollingMode
import org.librarysimplified.r2.ui_thread.SR2UIThreadService
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewModel
import org.librarysimplified.r2.views.SR2ReaderViewModelFactory
import org.librarysimplified.r2.views.SR2TOCFragment
import org.readium.r2.shared.publication.asset.FileAsset
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

class DemoActivity : AppCompatActivity(R.layout.demo_activity_host) {

  companion object {
    const val PICK_DOCUMENT = 1001
  }

  private val logger =
    LoggerFactory.getLogger(DemoActivity::class.java)

  private var readerParameters: SR2ReaderParameters? = null
  private var controller: SR2ControllerType? = null
  private var controllerSubscription: Disposable? = null
  private var viewSubscription: Disposable? = null

  private lateinit var scrollMode: CheckBox
  private lateinit var perChapterPageNumbering: CheckBox
  private lateinit var selectFileArea: View

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(null)

    val toolbar = this.findViewById(R.id.mainToolbar) as Toolbar
    this.setSupportActionBar(toolbar)

    this.setContentView(R.layout.demo_fragment_host)
    this.scrollMode = this.findViewById(R.id.scrollMode)
    this.perChapterPageNumbering = this.findViewById(R.id.perChapterPageNumbering)
    this.selectFileArea = this.findViewById(R.id.selectFileArea)
    val browseButton = this.findViewById<Button>(R.id.browse_button)!!
    browseButton.setOnClickListener { this.startDocumentPickerForResult() }
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
  override fun onPause() {
    super.onPause()
    this.stopReader()
  }

  /**
   * Start the reader with the given EPUB.
   */

  @UiThread
  private fun startReader(file: File, id: String) {
    SR2UIThreadService.checkIsUIThread()

    val database =
      DemoApplication.application.database()

    val readerParameters =
      SR2ReaderParameters(
        contentProtections = emptyList(),
        bookFile = FileAsset(file),
        bookId = id,
        theme = database.theme(),
        controllers = SR2Controllers(),
        scrollingMode = if (this.scrollMode.isChecked) {
          SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
        } else {
          SR2ScrollingMode.SCROLLING_MODE_PAGINATED
        },
        pageNumberingMode = if (this.perChapterPageNumbering.isChecked) {
          SR2PageNumberingMode.PER_CHAPTER
        } else {
          SR2PageNumberingMode.WHOLE_BOOK
        }
      )

    val readerModel =
      ViewModelProvider(this, SR2ReaderViewModelFactory(this.application, readerParameters))
        .get(SR2ReaderViewModel::class.java)

    this.viewSubscription =
      readerModel.viewEvents
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onViewEvent)

    this.controllerSubscription =
      readerModel.controllerEvents.subscribe(this::onControllerEvent)

    val readerFragment =
      SR2ReaderFragment.create(readerParameters)

    selectFileArea.visibility = View.GONE

    this.supportFragmentManager.beginTransaction()
      .replace(R.id.demoFragmentArea, readerFragment)
      .commitNow()

    this.readerParameters = readerParameters
  }

  private fun stopReader() {
    this.controllerSubscription?.dispose()
    this.viewSubscription?.dispose()

    this.viewModelStore.clear()
    this.supportFragmentManager.clear()

    selectFileArea.visibility = View.VISIBLE
  }

  private fun FragmentManager.clear() {
    for (i in 0 until this.backStackEntryCount) {
      this.popBackStack()
    }
    this.beginTransaction()
      .apply { fragments.forEach { remove(it) } }
      .commit()
    this.executePendingTransactions()
  }

  /**
   * Handle incoming messages from the reader.
   */

  private fun onViewEvent(event: SR2ReaderViewEvent) {
    SR2UIThreadService.checkIsUIThread()

    return when (event) {
      is SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed ->
        this.onBookLoadingFailed(event.exception)
      is SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookOpened ->
        this.onBookOpened(event.controller)
      SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationClose ->
        this.supportFragmentManager.popBackStack()
      SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC ->
        this.openTOC()
    }
  }

  private fun onBookLoadingFailed(exception: Throwable) {
    AlertDialog.Builder(this)
      .setMessage(exception.message)
      .setOnDismissListener { this.finish() }
      .create()
      .show()
  }

  private fun onBookOpened(controller: SR2ControllerType) {
    this.controller = controller

    // Navigate to the first chapter or saved reading position.
    val database = DemoApplication.application.database()
    val bookId = controller.bookMetadata.id
    controller.submitCommand(SR2Command.BookmarksLoad(database.bookmarksFor(bookId)))
    val lastRead = database.bookmarkFindLastReadLocation(bookId)
    val startLocator = lastRead?.locator ?: controller.bookMetadata.start
    controller.submitCommand(SR2Command.OpenChapter(startLocator))
  }

  private fun openTOC() {
    val tocFragment =
      SR2TOCFragment.create(this.readerParameters!!)

    this.supportFragmentManager.beginTransaction()
      .replace(R.id.demoFragmentArea, tocFragment)
      .addToBackStack(null)
      .commit()
  }

  /**
   * Handle incoming messages from the controller.
   */

  private fun onControllerEvent(event: SR2Event) {
    return when (event) {
      is SR2Event.SR2BookmarkEvent.SR2BookmarkCreated -> {
        val database = DemoApplication.application.database()
        database.bookmarkSave(this.controller!!.bookMetadata.id, event.bookmark)
      }

      is SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted -> {
        val database = DemoApplication.application.database()
        database.bookmarkDelete(this.controller!!.bookMetadata.id, event.bookmark)
      }

      is SR2Event.SR2ThemeChanged -> {
        val database = DemoApplication.application.database()
        database.themeSet(event.theme)
      }

      is SR2Event.SR2OnCenterTapped,
      is SR2Event.SR2ReadingPositionChanged,
      SR2Event.SR2BookmarkEvent.SR2BookmarksLoaded,
      is SR2Event.SR2Error.SR2ChapterNonexistent,
      is SR2Event.SR2Error.SR2WebViewInaccessible,
      is SR2Event.SR2ExternalLinkSelected,
      is SR2Event.SR2CommandEvent.SR2CommandExecutionStarted,
      is SR2Event.SR2CommandEvent.SR2CommandExecutionRunningLong,
      is SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionSucceeded,
      is SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionFailed -> {
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
        val (file, id) = this.copyToStorage(uri)!!
        this.startReader(file, id)
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
