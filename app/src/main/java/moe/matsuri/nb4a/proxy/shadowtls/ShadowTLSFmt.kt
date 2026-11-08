package moe.matsuri.nb4a.proxy.shadowtls

import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundTLS
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseShadowTLSLink(url: String): ShadowTLSBean {
    val link = url.replace("shadowtls://", "https://")
        .replace("shadow-tls://", "https://")
        .toHttpUrlOrNull() ?: error("invalid shadow-tls link $url")
    return ShadowTLSBean().apply {
        initializeDefaultValues()
        serverAddress = link.host
        serverPort = if (link.port > 0) link.port else 443
        name = link.fragment
        password = link.username.takeIf { it.isNotBlank() }
            ?: link.queryParameter("password")
            ?: link.queryParameter("passwd")
            ?: link.queryParameter("pwd")
            ?: link.queryParameter("secret")
            ?: ""
        (link.queryParameter("version") ?: link.queryParameter("v"))?.toIntOrNull()?.let {
            version = it
        }
        (link.queryParameter("host")
            ?: link.queryParameter("sni")
            ?: link.queryParameter("server_name")
            ?: link.queryParameter("servername"))?.let {
            sni = it
        }
        (link.queryParameter("fingerprint")
            ?: link.queryParameter("client-fingerprint")
            ?: link.queryParameter("fp"))?.let {
            utlsFingerprint = it
        }
        link.queryParameter("alpn")?.let {
            if (it != "none") alpn = it
        }
    }
}

fun ShadowTLSBean.toUri(): String {
    val builder = linkBuilder()
        .username(password ?: "")
        .host(serverAddress ?: "")
        .port(serverPort ?: 443)
    if (version != null && version != 3) builder.addQueryParameter("version", version.toString())
    if (!sni.isNullOrBlank()) builder.addQueryParameter("host", sni)
    if (!utlsFingerprint.isNullOrBlank()) builder.addQueryParameter("fingerprint", utlsFingerprint)
    if (!alpn.isNullOrBlank()) builder.addQueryParameter("alpn", alpn)
    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("shadow-tls").replace("${serverPort ?: 443}/", "${serverPort ?: 443}")
}

fun buildSingBoxOutboundShadowTLSBean(bean: ShadowTLSBean): SingBoxOptions.Outbound_ShadowTLSOptions {
    return SingBoxOptions.Outbound_ShadowTLSOptions().apply {
        type = "shadowtls"
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version ?: 3
        password = bean.password ?: ""
        tls = buildSingBoxOutboundTLS(bean) ?: SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            if (!bean.sni.isNullOrBlank()) server_name = bean.sni
        }
    }
}
