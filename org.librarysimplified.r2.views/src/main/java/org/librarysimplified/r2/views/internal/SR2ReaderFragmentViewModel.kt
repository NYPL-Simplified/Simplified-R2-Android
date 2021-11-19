package org.librarysimplified.r2.views.internal

import androidx.lifecycle.ViewModel
import hu.akarnokd.rxjava2.subjects.UnicastWorkSubject
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.Subject
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.views.SR2ReaderViewModel

internal class SR2ReaderFragmentViewModel(
  private val activityModel: SR2ReaderViewModel
) : ViewModel() {

  private val controllerEventsSubject: Subject<SR2Event> =
    UnicastWorkSubject.create()

  private val subscriptions: CompositeDisposable =
    CompositeDisposable(
      this.activityModel.controllerEvents
        .subscribe(this.controllerEventsSubject::onNext)
    )

  val controllerEvents: Observable<SR2Event> =
    this.controllerEventsSubject.observeOn(AndroidSchedulers.mainThread())

  val bookEvents: Observable<SR2ViewModelBookEvent> =
    this.activityModel.bookEvents.observeOn(AndroidSchedulers.mainThread())

  fun openTOC() {
    this.activityModel.openTOC()
  }

  override fun onCleared() {
    super.onCleared()
    this.subscriptions.clear()
  }
}
