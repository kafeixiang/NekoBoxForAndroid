package io.nekohasekai.sagernet.fmt.shadowquic

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions.Outbound_ShadowQUICOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseShadowQUIC(url: String): ShadowQUICBean {
    val httpLink = when {
        url.startsWith("sq://") -> url.replace("sq://", "https://")
        url.startsWith("shadowquic://") -> url.replace("shadowquic://", "https://")
        else -> "https://" + url.substringAfter("://")
    }
    val link = httpLink.toHttpUrlOrNull() ?: error("invalid shadowquic link $url")
    return ShadowQUICBean().apply {
        initializeDefaultValues()
        if (!link.fragment.isNullOrEmpty()) {
            name = link.fragment!!.unUrlSafe()
        } else {
            name = link.queryParameter("tag").orEmpty()
        }
        if (link.username.isNotEmpty()) {
            username = link.username.unUrlSafe()
        }
        if (link.password.isNotEmpty()) {
            password = link.password.unUrlSafe()
        }
        serverAddress = link.host
        serverPort = if (link.port > 0) link.port else 443
        (link.queryParameter("sni") ?: link.queryParameter("server_name") ?: link.queryParameter("server-name"))?.let {
            sni = it.unUrlSafe()
        }
        val udpMode = link.queryParameter("udp_over_stream")
            ?: link.queryParameter("udp-over-stream")
            ?: link.queryParameter("udp_mode")
            ?: link.queryParameter("udp_mod")
            ?: link.queryParameter("udp-mode")
        if (udpMode != null) {
            udpOverStream = when (udpMode.lowercase()) {
                "stream", "true", "1", "yes" -> true
                "datagram", "false", "0", "no" -> false
                else -> false
            }
        }
        (link.queryParameter("congestion") ?: link.queryParameter("congestion_control") ?: link.queryParameter("congestion-control") ?: link.queryParameter("congestion-controller"))?.let {
            congestionControl = it.unUrlSafe()
        }
        val zeroRttParam = link.queryParameter("zero_rtt")
            ?: link.queryParameter("zero-rtt")
            ?: link.queryParameter("zero_rtt_handshake")
            ?: link.queryParameter("zero-rtt-handshake")
        if (zeroRttParam != null) {
            zeroRttHandshake = when (zeroRttParam.lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> true
            }
        }
        link.queryParameter("alpn")?.let {
            alpn = it.unUrlSafe()
        }
        val sunnyParam = link.queryParameter("sunny_quic")
            ?: link.queryParameter("sunny-quic")
            ?: link.queryParameter("sunnyquic")
        if (sunnyParam != null) {
            sunnyQuic = when (sunnyParam.lowercase()) {
                "true", "1", "yes" -> true
                else -> false
            }
        }
    }
}

fun ShadowQUICBean.toUri(): String {
    val builder = linkBuilder()
    if (!username.isNullOrEmpty()) {
        builder.username(username)
    }
    if (!password.isNullOrEmpty()) {
        builder.password(password)
    }
    builder.host(if (!serverAddress.isNullOrEmpty()) serverAddress else "127.0.0.1")
    builder.port(if (serverPort != null && serverPort > 0) serverPort else 443)

    if (!sni.isNullOrBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (udpOverStream == true) {
        builder.addQueryParameter("udp_mode", "stream")
    } else {
        builder.addQueryParameter("udp_mode", "datagram")
    }
    if (zeroRttHandshake == true) {
        builder.addQueryParameter("zero_rtt", "true")
    }
    if (!congestionControl.isNullOrBlank() && congestionControl != "bbr") {
        builder.addQueryParameter("congestion", congestionControl)
    }
    if (!alpn.isNullOrBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }
    if (sunnyQuic == true) {
        builder.addQueryParameter("sunny_quic", "true")
    }
    if (!name.isNullOrBlank()) {
        builder.encodedFragment(name.urlSafe())
    }
    return builder.toLink("sq")
}

fun buildSingBoxOutboundShadowQUICBean(bean: ShadowQUICBean): Outbound_ShadowQUICOptions {
    return Outbound_ShadowQUICOptions().apply {
        type = "shadowquic"
        server = bean.serverAddress.orEmpty()
        server_port = if (bean.serverPort != null && bean.serverPort > 0) bean.serverPort else 443
        username = bean.username.orEmpty()
        password = bean.password.orEmpty()
        if (!bean.sni.isNullOrBlank()) {
            server_name = bean.sni
        }
        if (!bean.alpn.isNullOrBlank()) {
            alpn = bean.alpn.listByLineOrComma()
        }
        if (!bean.congestionControl.isNullOrBlank()) {
            congestion_control = bean.congestionControl
        }
        if (bean.udpOverStream == true) {
            udp_over_stream = true
        }
        if (bean.zeroRttHandshake == true) {
            zero_rtt_handshake = true
        }
        if (bean.sunnyQuic == true) {
            sunny_quic = true
        }
    }
}
