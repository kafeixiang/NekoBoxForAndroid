package io.nekohasekai.sagernet.fmt.masque

import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.isIpAddressV6
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseMASQUE(url: String): MASQUEBean {
    val link = url.replace("masque://", "https://").toHttpUrlOrNull()
        ?: error("Invalid MASQUE URL: $url")

    return MASQUEBean().apply {
        initializeDefaultValues()
        serverAddress = link.host
        serverPort = link.port
        if (link.username.isNotEmpty()) username = link.username
        if (link.password.isNotEmpty()) password = link.password
        if (!link.fragment.isNullOrEmpty()) name = link.fragment!!

        link.queryParameter("path")?.let { path = it }
        link.queryParameter("version")?.toIntOrNull()?.let {
            if (it in 1..3) httpVersion = it
        }
        (link.queryParameter("disable_version_fallback") ?: link.queryParameter("disableVersionFallback"))?.let {
            disableVersionFallback = it == "true" || it == "1"
        }
        link.queryParameter("tls")?.let {
            enableTLS = it != "false" && it != "0"
        }
        (link.queryParameter("sni") ?: link.queryParameter("serverName"))?.let {
            serverName = it
        }
        link.queryParameter("alpn")?.let { alpn = it }
        (link.queryParameter("allowInsecure") ?: link.queryParameter("allow_insecure") ?: link.queryParameter("insecure"))?.let {
            allowInsecure = it == "true" || it == "1"
        }
        (link.queryParameter("disableSNI") ?: link.queryParameter("disable_sni"))?.let {
            disableSNI = it == "true" || it == "1"
        }
        (link.queryParameter("utls") ?: link.queryParameter("fp"))?.let {
            utlsFingerprint = it
        }
        link.queryParameter("mtu")?.toIntOrNull()?.let {
            if (it > 0) mtu = it
        }
    }
}

fun buildSingBoxEndpointMASQUEBean(bean: MASQUEBean): SingBoxOptions.Endpoint_MASQUEClientOptions {
    return SingBoxOptions.Endpoint_MASQUEClientOptions().apply {
        type = "masque-client"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username.blankAsNull()
        password = bean.password.blankAsNull()
        path = bean.path.blankAsNull()
        if (!bean.headers.isNullOrBlank()) {
            val map = mutableMapOf<String, MutableList<String>>()
            bean.headers.lines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map.getOrPut(key) { mutableListOf() }.add(value)
                    }
                }
            }
            if (map.isNotEmpty()) headers = map
        }
        version = bean.httpVersion ?: 3
        if (bean.disableVersionFallback == true) disable_version_fallback = true
        mtu = bean.mtu ?: MASQUEBean.DEFAULT_MTU
        if (bean.enableTLS != false) {
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                enabled = true
                server_name = bean.serverName.blankAsNull()
                if (bean.allowInsecure == true) insecure = true
                if (bean.disableSNI == true) disable_sni = true
                alpn = bean.alpn.blankAsNull()?.listByLineOrComma()?.toMutableList()
                certificate = bean.certificates.blankAsNull()
                bean.utlsFingerprint.blankAsNull()?.let {
                    utls = SingBoxOptions.OutboundUTLSOptions().apply {
                        enabled = true
                        fingerprint = it
                    }
                }
            }
        }
    }
}

fun MASQUEBean.toUri(): String {
    val builder = StringBuilder("masque://")
    val user = username.orEmpty()
    val pass = password.orEmpty()
    if (user.isNotBlank() || pass.isNotBlank()) {
        builder.append(user.urlSafe()).append(":").append(pass.urlSafe()).append("@")
    }
    val addr = serverAddress.orEmpty()
    if (addr.isIpAddressV6()) {
        builder.append("[$addr]")
    } else {
        builder.append(addr)
    }
    builder.append(":").append(if (serverPort > 0) serverPort else 443)

    val params = mutableListOf<String>()
    if (!path.isNullOrBlank()) params.add("path=${path.urlSafe()}")
    if (httpVersion != null && httpVersion != 3) params.add("version=$httpVersion")
    if (disableVersionFallback == true) params.add("disable_version_fallback=true")
    if (enableTLS == false) params.add("tls=false")
    if (!serverName.isNullOrBlank()) params.add("sni=${serverName.urlSafe()}")
    if (!alpn.isNullOrBlank()) params.add("alpn=${alpn.urlSafe()}")
    if (allowInsecure == true) params.add("allowInsecure=true")
    if (disableSNI == true) params.add("disableSNI=true")
    if (!utlsFingerprint.isNullOrBlank()) params.add("utls=${utlsFingerprint.urlSafe()}")
    if (mtu != null && mtu != MASQUEBean.DEFAULT_MTU) params.add("mtu=$mtu")
    if (params.isNotEmpty()) {
        builder.append("?").append(params.joinToString("&"))
    }
    if (!name.isNullOrBlank()) {
        builder.append("#").append(name.urlSafe())
    }
    return builder.toString()
}
