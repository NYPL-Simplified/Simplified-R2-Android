package org.librarysimplified.r2.views.internal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.reactivex.disposables.Disposable
import org.librarysimplified.r2.api.SR2TOCEntry
import org.librarysimplified.r2.ui_thread.SR2UIThreadService
import org.librarysimplified.r2.views.R
import org.librarysimplified.r2.views.SR2ReaderParameters
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

  private val viewModel: SR2TOCChaptersFragmentViewModel by viewModels(
    factoryProducer = {
      val actFactory = { SR2ReaderViewModelFactory(requireActivity().application, this.parameters) }
      val actModel: SR2ReaderViewModel by activityViewModels(factoryProducer = actFactory)
      SR2FragmentViewModelFactory(actModel)
    }
  )

  private lateinit var chapterAdapter: SR2TOCChapterAdapter
  private lateinit var tocChaptersList: RecyclerView
  private lateinit var tocChaptersErrors: View

  private var bookEvents: Disposable? = null

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
  ): View {
    return inflater.inflate(R.layout.sr2_toc_chapters, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.tocChaptersList =
      view.findViewById(R.id.tocChaptersList)
    this.tocChaptersErrors =
      view.findViewById(R.id.tocChaptersError)

    this.tocChaptersList.adapter = this.chapterAdapter
    this.tocChaptersList.setHasFixedSize(true)
    this.tocChaptersList.setItemViewCacheSize(32)
    (this.tocChaptersList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

    this.bookEvents = this.viewModel.bookEvents.subscribe(this::onViewModelEvent)
  }

  private fun onViewModelEvent(event: SR2ViewModelBookEvent) {
    return when (event) {
      is SR2ViewModelBookEvent.SR2ViewModelBookOpenFailed -> {
        // Nothing
      }
      is SR2ViewModelBookEvent.SR2ViewModelBookOpened -> {
        val controller = event.controller
        this.reloadToc(controller.bookMetadata.tableOfContents)
      }
    }
  }

  private fun reloadToc(toc: List<SR2TOCEntry>) {
    this.tocChaptersErrors.isVisible = toc.isEmpty()
    this.tocChaptersList.isGone = toc.isEmpty()
    this.chapterAdapter.setTableOfContentsEntries(toc)
  }

  private fun onTOCEntrySelected(entry: SR2TOCEntry) {
    this.viewModel.openTocEntry(entry)
    SR2UIThreadService.runOnUIThreadUnsafeDelayed(
      this.viewModel::closeToc,
      SR2TOC.tocSelectionDelay()
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.bookEvents?.dispose()
  }
}
