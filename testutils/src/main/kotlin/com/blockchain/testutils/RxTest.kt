package com.blockchain.testutils

import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.Rule

/**
 * Class that forces all Rx observables to be subscribed and observed in the same thread through the
 * same Scheduler that runs immediately. Also exposes a [TestScheduler] for testing of
 * time-based methods.
 */
open class RxTest
@Deprecated("Implement an rxInit rule locally, specifying just the schedulers the test requires")
constructor() {

    /**
     * Returns a [TestScheduler] object which allows for easy testing of time-based methods
     * that return [io.reactivex.Observable] objects.
     */
    protected val testScheduler: TestScheduler = TestScheduler()

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        newThreadTrampoline()
        singleTrampoline()
        computation(testScheduler)
        RxJavaPlugins.setErrorHandler { it.printStackTrace() }
    }
}
