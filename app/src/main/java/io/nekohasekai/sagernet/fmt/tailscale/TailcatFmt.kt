package io.nekohasekai.sagernet.fmt.tailscale

import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
                    val trimmed = server.trim()
                    if (trimmed.startsWith("[") && trimmed.contains("]:")) {
                        host = trimmed.substringAfter("[").substringBefore("]")
                        derp_port = trimmed.substringAfter("]:").toIntOrNull()
                    } else if (trimmed.contains(":") && !trimmed.startsWith("[")) {
                        host = trimmed.substringBeforeLast(":")
                        derp_port = trimmed.substringAfterLast(":").toIntOrNull()
                    } else {
                        host = trimmed
                    }
                }
            }
        }
    }
}

fun TailcatBean.toUri(): String {
    val builder = linkBuilder()
    if (!privateKey.isNullOrBlank()) {
        builder.username(privateKey)
    }

    val firstDerp = derpServers?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
    val hostFromUrl = derpMapURL?.let { runCatching { it.toHttpUrlOrNull()?.host }.getOrNull() }
    val host = when {
        !firstDerp.isNullOrBlank() && !firstDerp.contains(":") -> firstDerp
        !firstDerp.isNullOrBlank() -> firstDerp.substringBefore(":")
        !hostFromUrl.isNullOrBlank() -> hostFromUrl
        else -> "tailcat.local"
    }
    builder.host(host)

    if (!serverPublicKey.isNullOrBlank()) builder.addQueryParameter("public_key", serverPublicKey)
    if (!serverDiscoKey.isNullOrBlank()) builder.addQueryParameter("disco_key", serverDiscoKey)
    if (!preSharedKey.isNullOrBlank()) builder.addQueryParameter("psk", preSharedKey)
    if (!derpMapURL.isNullOrBlank()) builder.addQueryParameter("derp_map_url", derpMapURL)
    if ((derpRegion ?: 0) > 0) builder.addQueryParameter("derp_region", derpRegion.toString())
    if (!derpServers.isNullOrBlank()) builder.addQueryParameter("derp_servers", derpServers.replace("\n", ","))

    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("tailcat", appendDefaultPort = false)
}

fun parseTailcat(url: String): TailcatBean {
    val link = url.replace("tailcat://", "https://").toHttpUrlOrNull()
        ?: error("Invalid Tailcat URL: $url")

    return TailcatBean().apply {
        initializeDefaultValues()
        if (link.username.isNotEmpty()) {
            privateKey = link.username.unUrlSafe()
        }
        if (!link.fragment.isNullOrEmpty()) {
            name = link.fragment!!.unUrlSafe()
        }

        (link.queryParameter("public_key")
            ?: link.queryParameter("server_public_key")
            ?: link.queryParameter("server-public-key")
            ?: link.queryParameter("serverPublicKey"))?.let {
            serverPublicKey = it.unUrlSafe()
        }
        (link.queryParameter("disco_key")
            ?: link.queryParameter("server_disco_key")
            ?: link.queryParameter("server-disco-key")
            ?: link.queryParameter("serverDiscoKey"))?.let {
            serverDiscoKey = it.unUrlSafe()
        }
        (link.queryParameter("psk")
            ?: link.queryParameter("pre_shared_key")
            ?: link.queryParameter("pre-shared-key")
            ?: link.queryParameter("preSharedKey"))?.let {
            preSharedKey = it.unUrlSafe()
        }
        (link.queryParameter("private_key")
            ?: link.queryParameter("private-key")
            ?: link.queryParameter("privateKey"))?.let {
            privateKey = it.unUrlSafe()
        }
        (link.queryParameter("derp_map_url")
            ?: link.queryParameter("derp-map-url")
            ?: link.queryParameter("derpMapURL"))?.let {
            derpMapURL = it.unUrlSafe()
        }
        (link.queryParameter("derp_region")
            ?: link.queryParameter("derp-region")
            ?: link.queryParameter("derpRegion"))?.toIntOrNull()?.let {
            derpRegion = it
        }
        (link.queryParameter("derp_servers")
            ?: link.queryParameter("derp-servers")
            ?: link.queryParameter("derp_server")
            ?: link.queryParameter("derp-server")
            ?: link.queryParameter("derpServers"))?.let {
            derpServers = it.unUrlSafe().replace(",", "\n")
        }
        if (serverPublicKey.isNullOrBlank() && link.host != "tailcat.local") {
            serverPublicKey = link.host
        }
    }
}

fun parseTailcatOutbound(map: Map<String, Any?>): TailcatBean = TailcatBean().apply {
    initializeDefaultValues()
    for ((key, value) in map) {
        if (value == null) continue
        when (key.replace("_", "-").lowercase()) {
            "tag", "name" -> name = value.toString()
            "private-key", "privatekey" -> privateKey = value.toString()
            "server-public-key", "serverpublickey", "public-key" -> serverPublicKey = value.toString()
            "server-disco-key", "serverdiscokey", "disco-key" -> serverDiscoKey = value.toString()
            "pre-shared-key", "preshared-key", "psk" -> preSharedKey = value.toString()
            "derp-map-url", "derpmapurl" -> derpMapURL = value.toString()
            "derp-region", "derpregion" -> derpRegion = value.toString().toIntOrNull() ?: 0
            "derp-servers", "derpservers" -> derpServers = when (value) {
                is List<*> -> value.mapNotNull { server ->
                    when (server) {
                        is String -> server
                        is Map<*, *> -> {
                            val h = server["host"]?.toString() ?: ""
                            val p = server["derp_port"]?.toString() ?: server["derp-port"]?.toString()
                            if (h.isNotEmpty() && p != null) "$h:$p" else h
                        }
                        else -> null
                    }
                }.joinToString("\n")
                else -> value.toString()
            }
        }
    }
}
