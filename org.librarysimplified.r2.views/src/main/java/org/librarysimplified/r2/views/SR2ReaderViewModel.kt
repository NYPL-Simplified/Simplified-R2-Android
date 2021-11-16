package org.librarysimplified.r2.views

import android.app.Application
import androidx.lifecycle.ViewModel
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import hu.akarnokd.rxjava2.subjects.UnicastWorkSubject
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.r2.api.SR2ControllerConfiguration
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.views.internal.SR2ViewModelBookEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * The view model shared between all SR2 fragments and the hosting activity.
 */

class SR2ReaderViewModel(
  application: Application,
  private val parameters: SR2ReaderParameters,
) : ViewModel() {

  private val logger =
    LoggerFactory.getLogger(SR2ReaderViewModel::class.java)

  private val ioExecutor: ListeningExecutorService =
    MoreExecutors.listeningDecorator(
      Executors.newFixedThreadPool(1) { runnable ->
        val thread = Thread(runnable)
        thread.name = "org.librarysimplified.r2.io"
        thread
      }
    )

  init {
    this.logger.debug("{}: initialized", this)

    /**
     * Initialize controller
     */

    val configuration = SR2ControllerConfiguration(
      bookFile = this.parameters.bookFile,
      bookId = this.parameters.bookId,
      context = application,
      ioExecutor = this.ioExecutor,
      contentProtections = this.parameters.contentProtections,
      theme = this.parameters.theme,
      scrollingMode = this.parameters.scrollingMode,
      pageNumberingMode = this.parameters.pageNumberingMode
    )

    val future =
      this.parameters.controllers.create(configuration)

    future.addListener(
      { this.onControllerCreated(future) },
      MoreExecutors.directExecutor()
    )
  }

  private fun onControllerCreated(future: Future<SR2ControllerType>) {
    try {
      val newController = future.get()
      this.logger.debug("controller created")

      this.viewEventsSubject.onNext(
        SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookOpened(newController)
      )

      this.bookEventsSubject.onNext(
        SR2ViewModelBookEvent.SR2ViewModelBookOpened(newController)
      )

      this.subscriptions.add(
        newController.events.subscribe(this.controllerEventsSubject::onNext)
      )
    } catch (e: Throwable) {
      this.logger.error("unable to create controller: ", e)

      this.viewEventsSubject.onNext(
        SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed(e)
      )

      this.bookEventsSubject.onNext(
        SR2ViewModelBookEvent.SR2ViewModelBookOpenFailed(e)
      )
    }
  }

  private val controllerEventsSubject: Subject<SR2Event> =
    PublishSubject.create()

  private val bookEventsSubject: Subject<SR2ViewModelBookEvent> =
    BehaviorSubject.create<SR2ViewModelBookEvent>().toSerialized()

  private val viewEventsSubject: Subject<SR2ReaderViewEvent> =
    UnicastWorkSubject.create<SR2ReaderViewEvent>().toSerialized()

  private val subscriptions: CompositeDisposable =
    CompositeDisposable()

  /**
   * Events published by the controller's lifecycle and expected to be used by internal fragments.
   *
   * Note: This observable emits the most recent item it has observed and all subsequent observed items
   * to each subscribed Observer.
   */

  internal val bookEvents: Observable<SR2ViewModelBookEvent> =
    bookEventsSubject

  internal fun openTOC() {
    this.viewEventsSubject.onNext(
      SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC
    )
  }

  internal fun closeTOC() {
    this.viewEventsSubject.onNext(
      SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationClose
    )
  }

  /**
   * Events published by the views and expected to be used by the activity hosting the reader fragment.
   *
   * Note: Once an Observer has subscribed, this observable emits all subsequently observed items
   * to the subscriber.
   */

  val viewEvents: Observable<SR2ReaderViewEvent> =
    viewEventsSubject

  /**
   * Events published by the controller.
   *
   * This is offered for convenience in order for callers to be able to subscribe
   * to controller's events before it's available.
   */

  val controllerEvents: Observable<SR2Event> =
    controllerEventsSubject

  override fun toString(): String =
    "[SR2ReaderViewModel 0x${Integer.toHexString(this.hashCode())}]"

  override fun onCleared() {
    super.onCleared()
    this.logger.debug("{}: onCleared", this)
    this.ioExecutor.shutdown()
    this.subscriptions.clear()
  }
}
