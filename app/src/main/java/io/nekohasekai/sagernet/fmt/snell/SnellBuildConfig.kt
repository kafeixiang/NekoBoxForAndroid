package io.nekohasekai.sagernet.fmt.snell

import moe.matsuri.nb4a.SingBoxOptions

fun buildSingBoxOutboundSnellBean(bean: SnellBean): SingBoxOptions.Outbound_SnellOptions {
    return SingBoxOptions.Outbound_SnellOptions().apply {
        type = "snell"
        server = bean.serverAddress
        server_port = bean.serverPort
        psk = bean.psk
        if (!bean.userKey.isNullOrBlank()) {
            userkey = bean.userKey
        }
        val v = if (bean.version == 6) 6 else 4
        version = v

        if (bean.network != null && bean.network.isNotBlank()) {
            network = bean.network
        }

        if (v == 6) {
            if (!bean.mode.isNullOrBlank() && bean.mode != "default") {
                mode = bean.mode
            }
        } else if (bean.obfsMode != null && bean.obfsMode.isNotBlank()) {
            obfs_mode = bean.obfsMode
            if (bean.obfsHost != null && bean.obfsHost.isNotBlank()) {
                obfs_host = bean.obfsHost
            }
        }

        if (bean.reuse != null && bean.reuse) {
            this.reuse = true
        }
    }
}
