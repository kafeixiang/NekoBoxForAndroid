package io.nekohasekai.sagernet.fmt.tailscale

import io.nekohasekai.sagernet.ktx.blankAsNull
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun buildSingBoxEndpointTailscaleBean(
    bean: TailscaleBean,
    extraRoutes: List<String> = emptyList(),
): SingBoxOptions.Endpoint_TailscaleOptions {
    return SingBoxOptions.Endpoint_TailscaleOptions().apply {
        type = "tailscale"
        state_directory = bean.stateDirectory.blankAsNull()
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
