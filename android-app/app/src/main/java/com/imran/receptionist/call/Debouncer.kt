package com.imran.receptionist.call

import kotlin.concurrent.timer

class Debouncer<T> {
    private var lastInvokeTime = 0L
    private var lastKey: T? = null

    fun debounce(key: T, delayMs: Long, action: () -> Unit) {
        lastKey = key
        val currentTime = System.currentTimeMillis()
        lastInvokeTime = currentTime

        timer(initialDelay = delayMs) {
            if (System.currentTimeMillis() - lastInvokeTime >= delayMs && lastKey == key) {
                action.invoke()
                cancel()
            }
        }
    }
}
