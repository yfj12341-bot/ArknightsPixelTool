package com.pixelpainter.app.autofill

import java.util.concurrent.CompletableFuture

/**
 * Implemented by the accessibility service so the fill runner can dispatch
 * synthetic taps/swipes and wait for each gesture to finish.
 */
interface IDispatcher {
    fun dispatch(action: AutoFillAction): CompletableFuture<Boolean>
}