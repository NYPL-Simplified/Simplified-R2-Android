package org.librarysimplified.r2.views.internal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Locator.SR2LocatorPercent
import org.librarysimplified.r2.api.SR2TOCEntry
import org.librarysimplified.r2.ui_thread.SR2UIThread
import org.librarysimplified.r2.views.R
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationClose
import org.librarysimplified.r2.views.SR2ReaderViewModel
import org.librarysimplified.r2.views.SR2ReaderViewModelFactory

internal class SR2TOCChaptersFragment private constructor(
  private val parameters: SR2ReaderParameters
) : Fragment() {

  companion object {
    fun create(parameters: SR2ReaderParameters): SR2TOCChaptersFragment {
      return SR2TOCChaptersFragment(parameters)
    }
  }

  private lateinit var controller: SR2ControllerType
  private lateinit var readerModel: SR2ReaderViewModel
  private lateinit var chapterAdapter: SR2TOCChapterAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.chapterAdapter =
      SR2TOCChapterAdapter(
        resources = this.resources,
        onTOCEntrySelected = { this.onTOCEntrySelected(it) }
      )
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.sr2_toc_chapters, container, false)
    val recyclerView =
      layout.findViewById<RecyclerView>(R.id.tocChaptersList)

    recyclerView.adapter = this.chapterAdapter
    recyclerView.setHasFixedSize(true)
    recyclerView.setItemViewCacheSize(32)
    recyclerView.layoutManager = LinearLayoutManager(this.context)
    (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    return layout
  }

  override fun onStart() {
    super.onStart()

    val activity = this.requireActivity()

    this.readerModel =
      ViewModelProvider(activity, SR2ReaderViewModelFactory(this.parameters))
        .get(SR2ReaderViewModel::class.java)

    this.controller = this.readerModel.get()!!
    this.chapterAdapter.setTableOfContentsEntries(
      this.controller.bookMetadata.navigationGraph.tableOfContentsFlat
    )
  }

  private fun onTOCEntrySelected(entry: SR2TOCEntry) {
    this.controller.submitCommand(
      SR2Command.OpenChapter(
        SR2LocatorPercent(
          chapterHref = entry.node.navigationPoint.locator.chapterHref,
          chapterProgress = 0.0
        )
      )
    )

    SR2UIThread.runOnUIThreadDelayed(
      Runnable { this.readerModel.publishViewEvent(SR2ReaderViewNavigationClose) },
      SR2TOC.tocSelectionDelay()
    )
  }
}
