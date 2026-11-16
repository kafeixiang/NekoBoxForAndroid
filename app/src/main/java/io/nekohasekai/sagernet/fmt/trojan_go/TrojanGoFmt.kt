package io.nekohasekai.sagernet.fmt.trojan_go

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import moe.matsuri.nb4a.SingBoxOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

fun parseTrojanGo(server: String): TrojanGoBean {
    val httpUrl = server.replace("trojan-go://", "https://")
        .replace("trojango://", "https://")
        .replace("trojan://", "https://")
    val link = httpUrl.toHttpUrlOrNull() ?: error(
        "invalid trojan-go link $server"
    )
    return TrojanGoBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username
        link.queryParameter("sni")?.takeIf { it.isNotBlank() }?.let {
            sni = it
        } ?: link.queryParameter("peer")?.takeIf { it.isNotBlank() }?.let {
            sni = it
        }

        link.queryParameter("type")?.let { lType ->
            type = lType

            when (type) {
                "ws" -> {
                    link.queryParameter("host")?.let {
                        host = it
                    }
                    link.queryParameter("path")?.let {
                        path = it
                    }
                }
                else -> {
                }
            }
        }
        link.queryParameter("encryption")?.let {
            encryption = it
        }
        link.queryParameter("plugin")?.let {
            plugin = it
        }

        (link.queryParameter("fp") ?: link.queryParameter("fingerprint"))?.takeIf { it.isNotBlank() }?.let {
            fingerprint = it
        }
        link.queryParameter("alpn")?.takeIf { it.isNotBlank() }?.let {
            alpn = it
        }

        link.queryParameter("mux")?.let {
            mux = it.toBooleanStrictOrNull() ?: (it == "1")
        }
        link.queryParameter("concurrency")?.toIntOrNull()?.let {
            if (it > 0) muxConcurrency = it
        }
        (link.queryParameter("tfo") ?: link.queryParameter("fastopen") ?: link.queryParameter("tcpFastOpen"))?.let {
            tcpFastOpen = it.toBooleanStrictOrNull() ?: (it == "1")
        }
        link.queryParameter("allowInsecure")?.let {
            allowInsecure = it.toBooleanStrictOrNull() ?: (it == "1")
        }

        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
    }
}

fun TrojanGoBean.toUri(): String {
    val builder = linkBuilder().username(password).host(serverAddress).port(serverPort)
    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (type.isNotBlank() && type != "original") {
        builder.addQueryParameter("type", type)

        when (type) {
            "ws" -> {
                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
    }
    if (encryption.isNotBlank() && encryption != "none") {
        builder.addQueryParameter("encryption", encryption)
    }
    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }
    if (!fingerprint.isNullOrBlank()) {
        builder.addQueryParameter("fp", fingerprint)
    }
    if (!alpn.isNullOrBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }
    if (mux == true) {
        builder.addQueryParameter("mux", "true")
        if (muxConcurrency != null && muxConcurrency != 8) {
            builder.addQueryParameter("concurrency", muxConcurrency.toString())
        }
    }
    if (tcpFastOpen == true) {
        builder.addQueryParameter("tfo", "true")
    }
    if (allowInsecure == true) {
        builder.addQueryParameter("allowInsecure", "true")
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("trojan-go")
}

fun buildSingBoxOutboundTrojanGoBean(bean: TrojanGoBean): SingBoxOptions.Outbound_TrojanGoOptions {
    return SingBoxOptions.Outbound_TrojanGoOptions().apply {
        type = "trojan-go"
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password

        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni.ifBlank { bean.serverAddress }
            insecure = bean.allowInsecure ?: false
            if (!bean.fingerprint.isNullOrBlank()) {
                utls = SingBoxOptions.OutboundUTLSOptions().apply {
                    enabled = true
                    fingerprint = bean.fingerprint
                }
            }
            if (!bean.alpn.isNullOrBlank()) {
                alpn = bean.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }

        if (bean.tcpFastOpen == true) {
            tcp_fast_open = true
        }

        if (bean.type == "ws") {
            val wsHost = bean.host.ifBlank { bean.sni.ifBlank { bean.serverAddress } }
            transport = SingBoxOptions.V2RayTransportOptions_WebsocketOptions().apply {
                type = "ws"
                path = if (bean.path.isNullOrBlank()) "/" else if (bean.path.startsWith("/")) bean.path else "/${bean.path}"
                if (wsHost.isNotBlank()) {
                    headers = mutableMapOf("Host" to wsHost)
                }
            }
        }

        if (bean.encryption.startsWith("ss;")) {
            val method = bean.encryption.substringAfter(";").substringBefore(":")
            val pass = bean.encryption.substringAfter(":")
            shadowsocks = SingBoxOptions.TrojanGoShadowsocksOptions().apply {
                enabled = true
                this.method = method
                this.password = pass
            }
        }

        if (bean.mux == true) {
            mux = SingBoxOptions.TrojanGoMuxOptions().apply {
                enabled = true
                concurrency = if (bean.muxConcurrency != null && bean.muxConcurrency > 0) bean.muxConcurrency else 8
                idle_timeout = 60
            }
        }

        if (!bean.plugin.isNullOrBlank()) {
            plugin = bean.plugin
        }
    }
}

fun TrojanGoBean.buildTrojanGoConfig(port: Int): String {
    return JSONObject().apply {
        put("run_type", "client")
        put("local_addr", LOCALHOST)
        put("local_port", port)
        put("remote_addr", finalAddress)
        put("remote_port", finalPort)
        put("password", JSONArray().apply {
            put(password)
        })
        put("log_level", if (DataStore.logLevel > 0) 0 else 2)
        if (mux == true) put("mux", JSONObject().apply {
            put("enabled", true)
            put("concurrency", if (muxConcurrency != null && muxConcurrency > 0) muxConcurrency else 8)
        })
        put("tcp", JSONObject().apply {
            put("prefer_ipv4", DataStore.ipv6Mode <= IPv6Mode.ENABLE)
        })

        when (type) {
            "original" -> {
            }
            "ws" -> put("websocket", JSONObject().apply {
                put("enabled", true)
                put("host", host)
                put("path", path)
            })
        }

        if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
            sni = serverAddress
        }

        put("ssl", JSONObject().apply {
            if (sni.isNotBlank()) put("sni", sni)
            if (allowInsecure == true) put("verify", false)
        })

        when {
            encryption == "none" -> {
            }
            encryption.startsWith("ss;") -> put("shadowsocks", JSONObject().apply {
                put("enabled", true)
                put("method", encryption.substringAfter(";").substringBefore(":"))
                put("password", encryption.substringAfter(":"))
            })
        }
    }.toStringPretty()
}

fun JSONObject.parseTrojanGo(): TrojanGoBean {
    return TrojanGoBean().applyDefaultValues().apply {
        serverAddress = optString("remote_addr", serverAddress)
        serverPort = optInt("remote_port", serverPort)
        when (val pass = get("password")) {
            is String -> {
                password = pass
            }
            is List<*> -> {
                password = pass[0] as String
            }
        }
        optJSONArray("ssl")?.apply {
            sni = optString("sni", sni)
        }
        optJSONArray("websocket")?.apply {
            if (optBoolean("enabled", false)) {
                type = "ws"
                host = optString("host", host)
                path = optString("path", path)
            }
        }
        optJSONArray("shadowsocks")?.apply {
            if (optBoolean("enabled", false)) {
                encryption = "ss;${optString("method", "")}:${optString("password", "")}"
            }
        }
    }
}
