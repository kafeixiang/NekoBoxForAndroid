package io.nekohasekai.sagernet.fmt.direct

import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions

fun buildSingBoxOutboundDirectBean(bean: DirectBean): SingBoxOptions.Outbound_DirectOptions {
    return SingBoxOptions.Outbound_DirectOptions().apply {
        type = "direct"
        reuse_addr = true
    }
}

fun DirectBean.toUri(): String {
    return if (name.isNotBlank()) "direct://#${name.urlSafe()}" else "direct://"
}
