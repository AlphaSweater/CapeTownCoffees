package com.synaptix.capetowncoffees.ui._simple

import timber.log.Timber

/* ──────────────────────────────────────────────────────────────────────────────
 * SIMPLE VM Debug
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Toggleable debug utilities for SimpleViewModel.
 * Enable in debug builds to get rich logs & misuse hints.
 */
object SimpleVmDebug {
    var enabled: Boolean = true
    var logTagPrefix: String = "SimpleVM"
    var warnIfBeforeStart: Boolean = true

    internal fun tag(vm: SimpleViewModel): String =
        "$logTagPrefix:${vm::class.simpleName ?: "VM"}"

    internal fun logD(vm: SimpleViewModel, msg: () -> String) {
        if (!enabled) return
        Timber.tag(tag(vm)).d(msg())
    }

    internal fun logE(vm: SimpleViewModel, msg: () -> String, t: Throwable? = null) {
        if (!enabled) return
        if (t != null) Timber.tag(tag(vm)).e(t, msg()) else Timber.tag(tag(vm)).e(msg())
    }
}

/** Per-VM debug state (kept internal). */
internal class VmDebugState {
    @Volatile var startInvoked: Boolean = false
    @Volatile var lastError: UiError? = null
}