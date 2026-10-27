package io.nekohasekai.sagernet.fmt.shadowquic

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
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
        name = link.fragment ?: link.queryParameter("tag").orEmpty()
        username = link.username
        password = link.password
        serverAddress = link.host
        serverPort = link.port
        (link.queryParameter("sni") ?: link.queryParameter("server_name") ?: link.queryParameter("server-name"))?.let {
            sni = it
        }
        val udpMode = link.queryParameter("udp_mode") ?: link.queryParameter("udp_mod")
        udpOverStream = when (udpMode?.lowercase()) {
            "stream" -> true
            "datagram" -> false
            else -> false
        }
        (link.queryParameter("congestion") ?: link.queryParameter("congestion_control"))?.let {
            congestionControl = it
        }
        val zeroRttParam = link.queryParameter("zero_rtt")
        zeroRttHandshake = when (zeroRttParam?.lowercase()) {
            null -> false
            "false", "0", "no" -> false
            else -> true
        }
        link.queryParameter("alpn")?.let {
            alpn = it
        }
        val sunnyParam = link.queryParameter("sunny_quic") ?: link.queryParameter("sunnyquic")
        sunnyQuic = when (sunnyParam?.lowercase()) {
            "true", "1", "yes" -> true
            else -> false
        }
    }
}

fun ShadowQUICBean.toUri(): String {
    val builder = linkBuilder()
        .username(username)
        .password(password)
        .host(serverAddress)
        .port(serverPort)

    if (sni.isNotBlank()) {
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
    if (congestionControl.isNotBlank()) {
        builder.addQueryParameter("congestion", congestionControl)
    }
    if (alpn.isNotBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }
    if (sunnyQuic == true) {
        builder.addQueryParameter("sunny_quic", "true")
    }
    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }
    return builder.toLink("sq")
}

fun buildSingBoxOutboundShadowQUICBean(bean: ShadowQUICBean): Outbound_ShadowQUICOptions {
    return Outbound_ShadowQUICOptions().apply {
        type = "shadowquic"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        if (bean.sni.isNotBlank()) {
            server_name = bean.sni
        }
        if (bean.alpn.isNotBlank()) {
            alpn = bean.alpn.listByLineOrComma()
        }
        if (bean.congestionControl.isNotBlank()) {
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
