package org.librarysimplified.r2.views.internal

import org.librarysimplified.r2.api.SR2ControllerType

/**
 * An internal set of events published by the reader's activity-scoped view model. These events indicate
 * that a book was opened or failed to open.
 */

internal sealed class SR2ViewModelBookEvent {

  data class SR2ViewModelBookOpened(
    val controller: SR2ControllerType
  ) : SR2ViewModelBookEvent()

  data class SR2ViewModelBookOpenFailed(
    val exception: Throwable
  ) : SR2ViewModelBookEvent()
}
