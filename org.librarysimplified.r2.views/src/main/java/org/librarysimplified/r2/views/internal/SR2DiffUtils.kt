package org.librarysimplified.r2.views.internal

import androidx.recyclerview.widget.DiffUtil
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2TOCEntry

internal object SR2DiffUtils {

  val bookmarkDiffCallback: DiffUtil.ItemCallback<SR2Bookmark> =
    object : DiffUtil.ItemCallback<SR2Bookmark>() {
      override fun areItemsTheSame(
        oldItem: SR2Bookmark,
        newItem: SR2Bookmark
      ): Boolean =
        oldItem == newItem

      override fun areContentsTheSame(
        oldItem: SR2Bookmark,
        newItem: SR2Bookmark
      ): Boolean =
        oldItem == newItem
    }

  val tocEntryCallback: DiffUtil.ItemCallback<SR2TOCEntry> =
    object : DiffUtil.ItemCallback<SR2TOCEntry>() {
      override fun areItemsTheSame(
        oldItem: SR2TOCEntry,
        newItem: SR2TOCEntry
      ): Boolean =
        oldItem == newItem

      override fun areContentsTheSame(
        oldItem: SR2TOCEntry,
        newItem: SR2TOCEntry
      ): Boolean =
        oldItem == newItem
    }
}
