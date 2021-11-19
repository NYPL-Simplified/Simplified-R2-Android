package org.librarysimplified.r2.views

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import org.librarysimplified.r2.views.internal.SR2TOCBookmarksFragment
import org.librarysimplified.r2.views.internal.SR2TOCChaptersFragment

class SR2ReaderFragmentFactory(
  private var parameters: SR2ReaderParameters
) : FragmentFactory() {

  override fun instantiate(
    classLoader: ClassLoader,
    className: String
  ): Fragment {
    return when (loadFragmentClass(classLoader, className)) {
      SR2ReaderFragment::class.java ->
        SR2ReaderFragment.create(this.parameters)
      SR2TOCFragment::class.java ->
        SR2TOCFragment.create(this.parameters)
      SR2TOCBookmarksFragment::class.java ->
        SR2TOCBookmarksFragment.create(this.parameters)
      SR2TOCChaptersFragment::class.java ->
        SR2TOCChaptersFragment.create(this.parameters)
      else ->
        super.instantiate(classLoader, className)
    }
  }
}
