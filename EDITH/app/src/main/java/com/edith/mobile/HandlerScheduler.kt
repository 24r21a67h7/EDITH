package com.edith.mobile

import android.os.Handler
import com.edith.runtime.Cancellable
import com.edith.runtime.Scheduler

/** [Scheduler] backed by an Android [Handler] (pass the main-thread handler). */
class HandlerScheduler(private val handler: Handler) : Scheduler {

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val runnable = Runnable { task() }
        handler.postDelayed(runnable, delayMs)
        return Cancellable { handler.removeCallbacks(runnable) }
    }
}
