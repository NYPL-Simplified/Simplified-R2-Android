package org.librarysimplified.r2.views.internal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Bookmark.Type.LAST_READ
import org.librarysimplified.r2.ui_thread.SR2UIThreadService
import org.librarysimplified.r2.views.R
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2ReaderViewModel
import org.librarysimplified.r2.views.SR2ReaderViewModelFactory
import org.slf4j.LoggerFactory

internal class SR2TOCBookmarksFragment private constructor(
  private val parameters: SR2ReaderParameters
) : Fragment() {

  companion object {
    fun create(parameters: SR2ReaderParameters): SR2TOCBookmarksFragment {
      return SR2TOCBookmarksFragment(parameters)
    }
  }

  private val logger = LoggerFactory.getLogger(SR2TOCBookmarksFragment::class.java)

  private val viewModel: SR2TOCBookmarksFragmentViewModel by viewModels(
    factoryProducer = {
      val actFactory = { SR2ReaderViewModelFactory(requireActivity().application, this.parameters) }
      val actModel: SR2ReaderViewModel by activityViewModels(factoryProducer = actFactory)
      SR2FragmentViewModelFactory(actModel)
    }
  )

  private lateinit var lastReadItem: SR2TOCBookmarkViewHolder
  private lateinit var bookmarkAdapter: SR2TOCBookmarkAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.bookmarkAdapter =
      SR2TOCBookmarkAdapter(
        resources = this.resources,
        onBookmarkSelected = {
          this.onBookmarkSelected(it)
        },
        onBookmarkDeleteRequested = {
          this.onBookmarkDeleteRequested(it)
        }
      )
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return inflater.inflate(R.layout.sr2_toc_bookmarks, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val recyclerView =
      view.findViewById<RecyclerView>(R.id.tocBookmarksList)

    recyclerView.adapter = this.bookmarkAdapter
    recyclerView.setHasFixedSize(true)
    recyclerView.setItemViewCacheSize(32)
    recyclerView.layoutManager = LinearLayoutManager(this.context)
    (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

    this.lastReadItem =
      SR2TOCBookmarkViewHolder(view.findViewById<ViewGroup>(R.id.tocBookmarksLastRead))

    this.viewModel.bookmarks.observe(viewLifecycleOwner, this::reloadBookmarks)
  }

  private fun reloadBookmarks(bookmarks: List<SR2Bookmark>) {
    SR2UIThreadService.checkIsUIThread()

    this.logger.debug("received {} bookmarks", bookmarks.size)
    this.bookmarkAdapter.setBookmarks(bookmarks.filter { it.type != LAST_READ })

    val lastRead = bookmarks.find { it.type == LAST_READ }
    if (lastRead != null) {
      this.lastReadItem.rootView.visibility = View.VISIBLE
      this.lastReadItem.bindTo(
        resources = this.resources,
        onBookmarkSelected = { },
        onBookmarkDeleteRequested = { },
        bookmark = lastRead
      )
    } else {
      this.lastReadItem.rootView.visibility = View.GONE
    }
  }

  private fun onBookmarkSelected(bookmark: SR2Bookmark) {
    this.viewModel.openBookmark(bookmark)
    SR2UIThreadService.runOnUIThreadUnsafeDelayed(
      this.viewModel::closeToc,
      SR2TOC.closeTocDelay
    )
  }

  private fun onBookmarkDeleteRequested(bookmark: SR2Bookmark) {
    this.viewModel.deleteBookmark(bookmark)
  }
}
