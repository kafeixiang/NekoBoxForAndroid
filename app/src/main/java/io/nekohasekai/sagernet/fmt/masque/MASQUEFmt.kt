package io.nekohasekai.sagernet.fmt.masque

import io.nekohasekai.sagernet.ktx.blankAsNull
import moe.matsuri.nb4a.utils.listByLineOrComma
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions

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
        version = bean.httpVersion
        disable_version_fallback = bean.disableVersionFallback
        mtu = bean.mtu
        if (bean.enableTLS) {
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                enabled = true
                server_name = bean.serverName.blankAsNull()
                if (bean.allowInsecure) insecure = true
                if (bean.disableSNI) disable_sni = true
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
    if (username.isNotBlank() || password.isNotBlank()) {
        builder.append(username.urlSafe()).append(":").append(password.urlSafe()).append("@")
    }
    builder.append(serverAddress).append(":").append(serverPort)
    val params = mutableListOf<String>()
    if (path.isNotBlank()) params.add("path=${path.urlSafe()}")
    if (httpVersion != 3) params.add("version=$httpVersion")
    if (disableVersionFallback == true) params.add("disable_version_fallback=true")
    if (enableTLS == false) params.add("tls=false")
    if (serverName.isNotBlank()) params.add("sni=${serverName.urlSafe()}")
    if (alpn.isNotBlank()) params.add("alpn=${alpn.urlSafe()}")
    if (allowInsecure == true) params.add("allowInsecure=true")
    if (disableSNI == true) params.add("disableSNI=true")
    if (utlsFingerprint.isNotBlank()) params.add("utls=${utlsFingerprint.urlSafe()}")
    if (mtu != null && mtu != MASQUEBean.DEFAULT_MTU) params.add("mtu=$mtu")
    if (params.isNotEmpty()) {
        builder.append("?").append(params.joinToString("&"))
    }
    if (name.isNotBlank()) {
        builder.append("#").append(name.urlSafe())
    }
    return builder.toString()
}
