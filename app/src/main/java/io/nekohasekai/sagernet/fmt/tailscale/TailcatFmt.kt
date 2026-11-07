package io.nekohasekai.sagernet.fmt.tailscale

import io.nekohasekai.sagernet.ktx.blankAsNull
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun buildSingBoxOutboundTailcatBean(bean: TailcatBean): SingBoxOptions.Outbound_TailcatOptions {
    return SingBoxOptions.Outbound_TailcatOptions().apply {
        type = "tailcat"
        private_key = bean.privateKey.blankAsNull()
        server_public_key = bean.serverPublicKey
        server_disco_key = bean.serverDiscoKey
        pre_shared_key = bean.preSharedKey.blankAsNull()
        val servers = bean.derpServers.blankAsNull()?.listByLineOrComma()
        if (servers.isNullOrEmpty()) {
            derp_map_url = bean.derpMapURL.blankAsNull()
            derp_region = bean.derpRegion?.takeIf { it > 0 }
        } else {
            derp_servers = servers.map { server ->
                SingBoxOptions.TailcatDERPServer().apply {
                    host = server
                }
            }
        }
    }
}

fun parseTailcatOutbound(map: Map<String, Any?>): TailcatBean = TailcatBean().apply {
    for ((key, value) in map) {
        if (value == null) continue
        when (key) {
            "tag" -> name = value.toString()
            "private_key" -> privateKey = value.toString()
            "server_public_key" -> serverPublicKey = value.toString()
            "server_disco_key" -> serverDiscoKey = value.toString()
            "pre_shared_key" -> preSharedKey = value.toString()
            "derp_map_url" -> derpMapURL = value.toString()
            "derp_region" -> derpRegion = value.toString().toIntOrNull() ?: 0
            "derp_servers" -> derpServers = (value as? List<*>)?.mapNotNull { server ->
                when (server) {
                    is String -> server
                    is Map<*, *> -> server["host"]?.toString()
                    else -> null
                }
            }?.joinToString("\n") ?: ""
        }
    }
}
