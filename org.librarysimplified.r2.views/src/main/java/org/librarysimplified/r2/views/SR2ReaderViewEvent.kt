package org.librarysimplified.r2.views

import org.librarysimplified.r2.api.SR2ControllerType

/**
 * The type of events published by the reader view.
 */

sealed class SR2ReaderViewEvent {

  sealed class SR2ReaderViewNavigationEvent : SR2ReaderViewEvent() {

    object SR2ReaderViewNavigationClose : SR2ReaderViewNavigationEvent() {
      override fun toString(): String =
        "[SR2ReaderViewNavigationClose]"
    }

    object SR2ReaderViewNavigationOpenTOC : SR2ReaderViewNavigationEvent() {
      override fun toString(): String =
        "[SR2ReaderViewNavigationOpenTOC]"
    }
  }

  sealed class SR2ReaderViewBookEvent : SR2ReaderViewEvent() {

    data class SR2BookOpened(
      val controller: SR2ControllerType
    ) : SR2ReaderViewBookEvent()

    data class SR2BookLoadingFailed(
      val exception: Throwable
    ) : SR2ReaderViewBookEvent()
  }
}
