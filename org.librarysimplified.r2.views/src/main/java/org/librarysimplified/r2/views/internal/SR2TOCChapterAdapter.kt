package org.librarysimplified.r2.views.internal

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.r2.api.SR2TOCEntry
import org.librarysimplified.r2.views.R
import org.librarysimplified.r2.views.internal.SR2DiffUtils.tocEntryCallback
import org.librarysimplified.r2.views.internal.SR2TOCChapterAdapter.SR2TOCEntryViewHolder

internal class SR2TOCChapterAdapter(
  private val resources: Resources,
  private val onTOCEntrySelected: (SR2TOCEntry) -> Unit
) : ListAdapter<SR2TOCEntry, SR2TOCEntryViewHolder>(tocEntryCallback) {

  class SR2TOCEntryViewHolder(
    val rootView: View
  ) : RecyclerView.ViewHolder(rootView) {
    val chapterIcon: ImageView =
      this.rootView.findViewById(R.id.chapterIcon)
    val chapterTitleText: TextView =
      this.rootView.findViewById(R.id.chapterTitle)
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): SR2TOCEntryViewHolder {
    val inflater =
      LayoutInflater.from(parent.context)
    val chapterView =
      inflater.inflate(R.layout.sr2_toc_chapter_item, parent, false)
    return SR2TOCEntryViewHolder(chapterView)
  }

  override fun onBindViewHolder(
    holder: SR2TOCEntryViewHolder,
    position: Int
  ) {
    val chapter = this.getItem(position)
    holder.rootView.setOnClickListener(SR2TOC.reenableClickDelay) {
      this.onTOCEntrySelected.invoke(chapter)
    }

    /*
     * Dynamically apply multiples of 16dp to the left margin to simulate "nesting".
     */

    val layoutParams = holder.chapterTitleText.layoutParams as ViewGroup.MarginLayoutParams
    layoutParams.marginStart = (chapter.depth + 1) * this.dpToPixels(24.0f)
    holder.chapterTitleText.layoutParams = layoutParams
    holder.chapterTitleText.text = chapter.title
  }

  private fun dpToPixels(dp: Float): Int {
    return Math.round(dp * (this.resources.displayMetrics.densityDpi / 160f))
  }

  fun setTableOfContentsEntries(entriesNow: List<SR2TOCEntry>) {
    this.submitList(entriesNow)
  }
}
