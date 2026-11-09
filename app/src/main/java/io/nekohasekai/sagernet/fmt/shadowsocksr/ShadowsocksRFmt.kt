package io.nekohasekai.sagernet.fmt.shadowsocksr

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.util.Locale

fun parseShadowsocksR(url: String): ShadowsocksRBean {
    val cleanUrl = url.trim()
    val decoded = cleanUrl.substringAfter("ssr://").decodeBase64UrlSafe()

    val beforeSlash = decoded.substringBefore("/?")
    val queryPart = if (decoded.contains("/?")) decoded.substringAfter("/?") else if (decoded.contains("?")) decoded.substringAfter("?") else ""

    // Format: host:port:protocol:method:obfs:base64pass
    val b64Pass = beforeSlash.substringAfterLast(":")
    val rest1 = beforeSlash.substringBeforeLast(":")
    val obfsVal = rest1.substringAfterLast(":")
    val rest2 = rest1.substringBeforeLast(":")
    val methodVal = rest2.substringAfterLast(":")
    val rest3 = rest2.substringBeforeLast(":")
    val protocolVal = rest3.substringAfterLast(":")
    val rest4 = rest3.substringBeforeLast(":")
    val portVal = rest4.substringAfterLast(":").toIntOrNull() ?: 8388
    val hostVal = rest4.substringBeforeLast(":")

    val bean = ShadowsocksRBean().apply {
        initializeDefaultValues()
        serverAddress = hostVal
        serverPort = portVal
        protocol = protocolVal
        method = methodVal
        obfs = obfsVal
        password = runCatching { b64Pass.decodeBase64UrlSafe() }.getOrDefault(b64Pass)
    }

    if (queryPart.isNotBlank()) {
        val httpUrl = ("https://localhost/?" + queryPart.removePrefix("/").removePrefix("?")).toHttpUrlOrNull()
        if (httpUrl != null) {
            httpUrl.queryParameter("obfsparam")?.let { raw ->
                bean.obfsParam = runCatching { raw.decodeBase64UrlSafe() }.getOrDefault(raw)
            }
            httpUrl.queryParameter("protoparam")?.let { raw ->
                bean.protocolParam = runCatching { raw.decodeBase64UrlSafe() }.getOrDefault(raw)
            }
            httpUrl.queryParameter("remarks")?.let { raw ->
                bean.name = runCatching { raw.decodeBase64UrlSafe() }.getOrDefault(raw)
            }
        }
    }

    return bean
}

fun ShadowsocksRBean.toUri(): String {
    val safePassword = password ?: ""
    val safeObfsParam = obfsParam ?: ""
    val safeProtoParam = protocolParam ?: ""
    val safeName = name ?: ""

    return "ssr://" + Util.b64EncodeUrlSafe(
        "%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s".format(
            Locale.ENGLISH,
            serverAddress ?: "",
            serverPort ?: 8388,
            protocol ?: "origin",
            method ?: "none",
            obfs ?: "plain",
            Util.b64EncodeUrlSafe(safePassword),
            Util.b64EncodeUrlSafe(safeObfsParam),
            Util.b64EncodeUrlSafe(safeProtoParam),
            Util.b64EncodeUrlSafe(safeName)
        )
    )
}

fun JSONObject.parseShadowsocksR(): ShadowsocksRBean {
    return ShadowsocksRBean().applyDefaultValues().apply {
        serverAddress = optString("server", serverAddress)
        serverPort = optInt("server_port", serverPort)
        method = optString("method", method)
        password = optString("password", password)
        protocol = optString("protocol", protocol)
        protocolParam = optString("protocol_param", protocolParam)
        obfs = optString("obfs", obfs)
        obfsParam = optString("obfs_param", obfsParam)
        name = optString("remarks", name)
    }
}

fun buildSingBoxOutboundShadowsocksRBean(bean: ShadowsocksRBean): SingBoxOptions.Outbound_ShadowsocksROptions {
    return SingBoxOptions.Outbound_ShadowsocksROptions().apply {
        type = "shadowsocksr"
        server = bean.serverAddress
        server_port = bean.serverPort
        method = bean.method ?: "none"
        password = bean.password ?: ""
        protocol = bean.protocol ?: "origin"
        protocol_param = bean.protocolParam ?: ""
        obfs = bean.obfs ?: "plain"
        obfs_param = bean.obfsParam ?: ""
        // do NOT set network field here
    }
}
