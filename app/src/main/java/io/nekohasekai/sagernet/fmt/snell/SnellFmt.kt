package io.nekohasekai.sagernet.fmt.snell

import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// URI 格式: snell://base64(psk)@server:port?version=6&userkey=base64(userkey)&mode=default&reuse=true&network=tcp#name
fun parseSnell(url: String): SnellBean {
    val link = url.replace("snell://", "https://").toHttpUrlOrNull()
        ?: error("Invalid snell URL")

    return SnellBean().apply {
        initializeDefaultValues()
        serverAddress = link.host
        serverPort = link.port
        psk = link.username
        name = link.fragment ?: ""

        link.queryParameter("version")?.toIntOrNull()?.let {
            version = it.coerceIn(1, 6)
        }
        link.queryParameter("userkey")?.let { userKey = it }
        link.queryParameter("obfs-mode")?.let { obfsMode = it }
        link.queryParameter("obfs-host")?.let { obfsHost = it }
        link.queryParameter("reuse")?.let { reuse = it.toBoolean() }
        link.queryParameter("network")?.let { network = it }
        link.queryParameter("mode")?.let { mode = it }
        link.queryParameter("quic-proxy-mode")?.let { quicProxyMode = it.toBoolean() }
    }
}

fun SnellBean.toUri(): String {
    val builder = StringBuilder("snell://")
    builder.append((psk ?: "").urlSafe()).append("@")
    builder.append(serverAddress ?: "").append(":").append(serverPort ?: 443)

    val params = mutableListOf<String>()
    params.add("version=${version ?: 4}")
    if (!userKey.isNullOrBlank()) params.add("userkey=${userKey.urlSafe()}")
    if (version == 6) {
        if (!mode.isNullOrBlank() && mode != "default") params.add("mode=$mode")
        if (quicProxyMode == true) params.add("quic-proxy-mode=true")
    } else {
        if (!obfsMode.isNullOrBlank()) params.add("obfs-mode=$obfsMode")
        if (!obfsHost.isNullOrBlank()) params.add("obfs-host=$obfsHost")
    }
    if (reuse == true) params.add("reuse=true")
    if (!network.isNullOrBlank()) params.add("network=$network")

    if (params.isNotEmpty()) {
        builder.append("?").append(params.joinToString("&"))
    }

    if (!name.isNullOrBlank()) {
        builder.append("#").append(name.urlSafe())
    }

    return builder.toString()
}

fun parseClashSnell(proxy: Map<String, Any?>): SnellBean {
    return SnellBean().apply {
        name = proxy["name"] as? String ?: ""
        serverAddress = proxy["server"] as? String ?: ""
        serverPort = (proxy["port"] as? Number)?.toInt() ?: 443
        psk = proxy["psk"] as? String ?: ""

        version = ((proxy["version"] as? Number)?.toInt() ?: 4).coerceIn(1, 6)

        reuse = proxy["reuse"] as? Boolean ?: false

        val udpEnabled = proxy["udp"] as? Boolean ?: false
        network = if (udpEnabled) {
            ""
        } else {
            "tcp"
        }

        // obfs-opts
        (proxy["obfs-opts"] as? Map<*, *>)?.let { obfsOpts ->
            obfsMode = obfsOpts["mode"] as? String ?: ""
            obfsHost = obfsOpts["host"] as? String ?: ""
        }
    }
}
