/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

internal fun getLocalSocketOrNull(
    port: Int,
    trials: Int,
    trialDelay: Long,
): Socket? {
    for (i in 0..trials) {
        try {
            return Socket(localhost, port)
        } catch (e: IOException) {
            Thread.sleep(trialDelay)
        }
    }

    return null
}

internal val localhost: InetAddress
    get() = InetAddress.getByName("127.0.0.1")

internal fun newServerSocket() =
    ServerSocket(0, 0, localhost).apply {
        reuseAddress = true
        soTimeout = SOCKET_TIMEOUT_MS
    }

internal fun <T> Iterator<T>.nextOrNull(): T? =
    if (hasNext()) next() else null