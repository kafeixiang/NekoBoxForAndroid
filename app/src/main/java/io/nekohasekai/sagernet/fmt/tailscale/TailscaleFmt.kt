package io.nekohasekai.sagernet.fmt.tailscale

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

fun buildSingBoxEndpointTailscaleBean(
    bean: TailscaleBean,
    extraRoutes: List<String> = emptyList(),
): SingBoxOptions.Endpoint_TailscaleOptions {
    return SingBoxOptions.Endpoint_TailscaleOptions().apply {
        type = "tailscale"
        state_directory = bean.stateDirectory.blankAsNull()
            ?: runCatching { File(SagerNet.application.filesDir, "tailscale").apply { mkdirs() }.absolutePath }.getOrNull()
        auth_key = bean.authKey.blankAsNull()
        control_url = bean.controlURL.blankAsNull()
        ephemeral = bean.ephemeral?.takeIf { it }
        hostname = bean.hostname.blankAsNull()
        accept_routes = bean.acceptRoutes?.takeIf { it }
        exit_node = bean.exitNode.blankAsNull()
        exit_node_allow_lan_access = bean.exitNodeAllowLANAccess?.takeIf { it }
        advertise_routes = (bean.advertiseRoutes.blankAsNull()?.listByLineOrComma().orEmpty() + extraRoutes)
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.toMutableList()
        advertise_exit_node = bean.advertiseExitNode?.takeIf { it }
        advertise_tags = bean.advertiseTags.blankAsNull()?.listByLineOrComma()?.toMutableList()
        listen_port = bean.listenPort?.takeIf { it > 0 }
    }
}

fun TailscaleBean.toUri(): String {
    val builder = linkBuilder()
    if (!authKey.isNullOrBlank()) {
        builder.username(authKey)
    }
    val cUrl = controlURL?.takeIf { it.isNotBlank() }
    if (!cUrl.isNullOrBlank()) {
        val cleaned = cUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val hostOnly = if (cleaned.contains("/")) cleaned.substringBefore("/") else cleaned
        val host = if (hostOnly.contains(":")) hostOnly.substringBefore(":") else hostOnly
        val port = if (hostOnly.contains(":")) hostOnly.substringAfter(":").toIntOrNull() ?: 443 else 443
        builder.host(host)
        builder.port(port)
    } else {
        builder.host("controlplane.tailscale.com")
        builder.port(443)
    }

    if (!cUrl.isNullOrBlank()) builder.addQueryParameter("control_url", cUrl)
    if (!hostname.isNullOrBlank()) builder.addQueryParameter("hostname", hostname)
    if (!exitNode.isNullOrBlank()) builder.addQueryParameter("exit_node", exitNode)
    if (ephemeral == true) builder.addQueryParameter("ephemeral", "true")
    if (acceptRoutes == true) builder.addQueryParameter("accept_routes", "true")
    if (exitNodeAllowLANAccess == true) builder.addQueryParameter("exit_node_allow_lan_access", "true")
    if (!advertiseRoutes.isNullOrBlank()) builder.addQueryParameter("advertise_routes", advertiseRoutes)
    if (advertiseExitNode == true) builder.addQueryParameter("advertise_exit_node", "true")
    if (!advertiseTags.isNullOrBlank()) builder.addQueryParameter("advertise_tags", advertiseTags)
    if ((listenPort ?: 0) > 0) builder.addQueryParameter("listen_port", listenPort.toString())
    if (sshServer == true) builder.addQueryParameter("ssh_server", "true")
    if (advertiseFakeDNSRoutes == true) builder.addQueryParameter("advertise_fake_dns_routes", "true")
    if (!stateDirectory.isNullOrBlank()) builder.addQueryParameter("state_directory", stateDirectory)

    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("tailscale", appendDefaultPort = false)
}

fun parseTailscale(url: String): TailscaleBean {
    val link = url.replace("tailscale://", "https://").toHttpUrlOrNull()
        ?: error("Invalid Tailscale URL: $url")

    return TailscaleBean().apply {
        initializeDefaultValues()
        if (link.username.isNotEmpty()) {
            authKey = link.username
        }
        val hostPort = if (link.port != 443 && link.port > 0) "${link.host}:${link.port}" else link.host
        controlURL = "https://$hostPort"

        if (!link.fragment.isNullOrEmpty()) {
            name = link.fragment!!
        }

        (link.queryParameter("auth_key") ?: link.queryParameter("authKey"))?.let { authKey = it }
        (link.queryParameter("control_url") ?: link.queryParameter("controlURL"))?.let { controlURL = it }
        link.queryParameter("hostname")?.let { hostname = it }
        link.queryParameter("exit_node")?.let { exitNode = it }
        link.queryParameter("ephemeral")?.let { ephemeral = it == "true" || it == "1" }
        link.queryParameter("accept_routes")?.let { acceptRoutes = it == "true" || it == "1" }
        link.queryParameter("exit_node_allow_lan_access")?.let { exitNodeAllowLANAccess = it == "true" || it == "1" }
        link.queryParameter("advertise_routes")?.let { advertiseRoutes = it }
        link.queryParameter("advertise_exit_node")?.let { advertiseExitNode = it == "true" || it == "1" }
        link.queryParameter("advertise_tags")?.let { advertiseTags = it }
        (link.queryParameter("listen_port") ?: link.queryParameter("port"))?.toIntOrNull()?.let { listenPort = it }
        link.queryParameter("ssh_server")?.let { sshServer = it == "true" || it == "1" }
        link.queryParameter("advertise_fake_dns_routes")?.let { advertiseFakeDNSRoutes = it == "true" || it == "1" }
        (link.queryParameter("state_directory") ?: link.queryParameter("state_dir"))?.let { stateDirectory = it }
    }
}

fun parseTailscaleEndpoint(map: Map<String, Any?>): TailscaleBean = TailscaleBean().apply {
    initializeDefaultValues()
    for ((key, value) in map) {
        if (value == null) continue
        when (key.replace("_", "-").lowercase()) {
            "tag", "name" -> name = value.toString()
            "state-directory", "state-dir" -> stateDirectory = value.toString()
            "auth-key", "authkey" -> authKey = value.toString()
            "control-url", "controlurl" -> controlURL = value.toString()
            "ephemeral" -> ephemeral = value.toString() == "true" || value.toString() == "1"
            "hostname" -> hostname = value.toString()
            "accept-routes", "acceptroutes" -> acceptRoutes = value.toString() == "true" || value.toString() == "1"
            "exit-node", "exitnode" -> exitNode = value.toString()
            "exit-node-allow-lan-access" -> exitNodeAllowLANAccess = value.toString() == "true" || value.toString() == "1"
            "advertise-routes", "advertiseroutes" -> advertiseRoutes = when (value) {
                is List<*> -> value.mapNotNull { it?.toString() }.joinToString("\n")
                else -> value.toString()
            }
            "advertise-exit-node", "advertiseexitnode" -> advertiseExitNode = value.toString() == "true" || value.toString() == "1"
            "advertise-tags", "advertisetags" -> advertiseTags = when (value) {
                is List<*> -> value.mapNotNull { it?.toString() }.joinToString("\n")
                else -> value.toString()
            }
            "listen-port", "listenport", "port" -> listenPort = value.toString().toIntOrNull() ?: 0
            "ssh-server", "sshserver" -> sshServer = value.toString() == "true" || value.toString() == "1"
            "advertise-fake-dns-routes" -> advertiseFakeDNSRoutes = value.toString() == "true" || value.toString() == "1"
        }
    }
}
