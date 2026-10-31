package io.nekohasekai.sagernet.fmt.shadowsocks

import io.nekohasekai.sagernet.ktx.*
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

fun ShadowsocksBean.fixPluginName() {
    if (plugin.startsWith("simple-obfs")) {
        plugin = plugin.replaceFirst("simple-obfs", "obfs-local")
    }
}

fun parseShadowsocks(url: String): ShadowsocksBean {

    if (url.substringBefore("#").contains("@")) {
        var link = url.replace("ss://", "https://").toHttpUrlOrNull() ?: error(
            "invalid ss-android link $url"
        )

        if (link.username.isBlank()) { // fix justmysocks's shit link
            link = (("https://" + url.substringAfter("ss://")
                .substringBefore("#")
                .decodeBase64UrlSafe()).toHttpUrlOrNull()
                ?: error("invalid jms link $url")
                    ).newBuilder().fragment(url.substringAfter("#")).build()
        }

        val uotParam = link.queryParameter("uot") ?: link.queryParameter("udp-over-tcp")
        val uotVerParam = link.queryParameter("uot_version") ?: link.queryParameter("uot-version")

        // ss-android style

        if (link.password.isNotBlank()) {
            return ShadowsocksBean().apply {
                serverAddress = link.host
                serverPort = link.port
                method = link.username
                password = link.password
                plugin = link.queryParameter("plugin") ?: ""
                name = link.fragment
                if (uotParam != null) {
                    sUoT = uotParam == "1" || uotParam.equals("true", ignoreCase = true)
                }
                if (uotVerParam != null) {
                    sUoTVersion = uotVerParam.toIntOrNull() ?: 2
                }
                fixPluginName()
            }
        }

        val methodAndPswd = link.username.decodeBase64UrlSafe()

        return ShadowsocksBean().apply {
            serverAddress = link.host
            serverPort = link.port
            method = methodAndPswd.substringBefore(":")
            password = methodAndPswd.substringAfter(":")
            plugin = link.queryParameter("plugin") ?: ""
            name = link.fragment
            if (uotParam != null) {
                sUoT = uotParam == "1" || uotParam.equals("true", ignoreCase = true)
            }
            if (uotVerParam != null) {
                sUoTVersion = uotVerParam.toIntOrNull() ?: 2
            }
            fixPluginName()
        }
    } else {
        // v2rayN style
        var v2Url = url

        if (v2Url.contains("#")) v2Url = v2Url.substringBefore("#")

        val link = ("https://" + v2Url.substringAfter("ss://")
            .decodeBase64UrlSafe()).toHttpUrlOrNull() ?: error("invalid v2rayN link $url")

        val uotParam = link.queryParameter("uot") ?: link.queryParameter("udp-over-tcp")
        val uotVerParam = link.queryParameter("uot_version") ?: link.queryParameter("uot-version")

        return ShadowsocksBean().apply {
            serverAddress = link.host
            serverPort = link.port
            method = link.username
            password = link.password
            plugin = link.queryParameter("plugin") ?: ""
            if (uotParam != null) {
                sUoT = uotParam == "1" || uotParam.equals("true", ignoreCase = true)
            }
            if (uotVerParam != null) {
                sUoTVersion = uotVerParam.toIntOrNull() ?: 2
            }
            val remarks = url.substringAfter("#").unUrlSafe()
            if (remarks.isNotBlank()) name = remarks
        }
    }

}

fun ShadowsocksBean.toUri(): String {

    val builder = linkBuilder().username(Util.b64EncodeUrlSafe("$method:$password"))
        .host(serverAddress)
        .port(serverPort)

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (sUoT == true) {
        builder.addQueryParameter("uot", "1")
        if (sUoTVersion != null && sUoTVersion != 2) {
            builder.addQueryParameter("uot_version", sUoTVersion.toString())
        }
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("ss").replace("$serverPort/", "$serverPort")

}

fun JSONObject.parseShadowsocks(): ShadowsocksBean {
    return ShadowsocksBean().apply {
        serverAddress = getStr("server")
        serverPort = getIntNya("server_port")
        password = getStr("password")
        method = getStr("method")
        name = optString("remarks", "")

        val pId = getStr("plugin")
        if (!pId.isNullOrBlank()) {
            plugin = pId + ";" + optString("plugin_opts", "")
        }

        if (has("udp_over_tcp")) {
            val uotObj = optJSONObject("udp_over_tcp")
            if (uotObj != null) {
                sUoT = uotObj.optBoolean("enabled", true)
                sUoTVersion = uotObj.optInt("version", 2)
            } else {
                sUoT = optBoolean("udp_over_tcp")
            }
        } else if (has("uot")) {
            sUoT = optBoolean("uot")
            if (has("uot_version")) {
                sUoTVersion = optInt("uot_version", 2)
            }
        }
    }
}

fun buildSingBoxOutboundShadowsocksBean(bean: ShadowsocksBean): SingBoxOptions.Outbound_ShadowsocksOptions {
    return SingBoxOptions.Outbound_ShadowsocksOptions().apply {
        type = "shadowsocks"
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        method = bean.method
        if (bean.plugin.isNotBlank()) {
            plugin = bean.plugin.substringBefore(";")
            plugin_opts = bean.plugin.substringAfter(";")
            if (plugin == "none") {
                plugin = null
                plugin_opts = null
            }
        }
        if (bean.sUoT == true) {
            udp_over_tcp = SingBoxOptions.UDPOverTCPOptions().apply {
                enabled = true
                version = bean.sUoTVersion ?: 2
            }
        }
    }
}
