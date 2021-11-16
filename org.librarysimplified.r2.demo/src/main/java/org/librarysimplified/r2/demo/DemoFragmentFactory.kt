package org.librarysimplified.r2.demo

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2TOCFragment

/**
 * A fragment factory which enables to set reader parameters
 * after being registered with the FragmentManager.
 */

internal class DemoFragmentFactory(
  private var parameters: SR2ReaderParameters? = null
) : FragmentFactory() {

  override fun instantiate(
    classLoader: ClassLoader,
    className: String
  ): Fragment {
    val parametersNow = requireNotNull(parameters)

    return when (loadFragmentClass(classLoader, className)) {
      SR2ReaderFragment::class.java ->
        SR2ReaderFragment.create(parametersNow)
      SR2TOCFragment::class.java ->
        SR2TOCFragment.create(parametersNow)
      else ->
        super.instantiate(classLoader, className)
    }
  }

  fun setParameters(parameters: SR2ReaderParameters) {
    this.parameters = parameters
  }
}
