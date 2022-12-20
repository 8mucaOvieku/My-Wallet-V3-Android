package com.blockchain.android.testutils

import com.blockchain.testutils.after
import com.blockchain.testutils.before
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers

fun rxInit(block: RxInit.() -> Unit) =
    before {
        RxInit().also(block)
    } after {
        RxAndroidPlugins.reset()
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(null)
        RxJavaPlugins.reset()
    }

class RxInit : com.blockchain.testutils.RxInit() {

    fun main(scheduler: Scheduler) {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { scheduler }
    }

    override fun mainTrampoline() {
        main(Schedulers.trampoline())
    }
}
