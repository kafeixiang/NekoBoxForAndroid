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
        version = if (bean.version == null || bean.version < 4) 4 else bean.version

        if (bean.network != null && bean.network.isNotBlank()) {
            network = bean.network
        }

        if (bean.version == 6) {
            if (!bean.mode.isNullOrBlank() && bean.mode != "default") {
                mode = bean.mode
            }
            if (bean.quicProxyMode == true) {
                quic_proxy_mode = true
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
