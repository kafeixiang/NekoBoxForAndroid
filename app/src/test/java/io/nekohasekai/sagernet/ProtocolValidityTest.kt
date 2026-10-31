package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria2
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic.buildSingBoxOutboundTuicBean
import io.nekohasekai.sagernet.fmt.tuic.parseTuic
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxOutboundWireguardBean
import io.nekohasekai.sagernet.fmt.wireguard.parseWireGuardLink
import io.nekohasekai.sagernet.group.RawUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProtocolValidityTest {

    @Test
    fun testWireGuardParsingAndBuilding() {
        val link = "wireguard://J+qTi6nj6NRm9ncQ8vUn99YDnzvwifvxlTVSaOFXXe4=@1.2.3.4:51820?publickey=VNCR8uuNIIsW2+PpzeN2DwQmuz+ZirlLMoAIM1CulS0=&presharedkey=yD3a8mvUoKN4qEO3icjpYVECbFlaIpR2Updf7AEmK3I=&address=10.0.0.2/32,fd00::2/128&mtu=1420&reserved=1,2,3&workers=2&keepalive=25&system=1#test-wg"
        val bean = parseWireGuardLink(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertEquals("J+qTi6nj6NRm9ncQ8vUn99YDnzvwifvxlTVSaOFXXe4=", bean.privateKey)
        assertEquals("VNCR8uuNIIsW2+PpzeN2DwQmuz+ZirlLMoAIM1CulS0=", bean.peerPublicKey)
        assertEquals("yD3a8mvUoKN4qEO3icjpYVECbFlaIpR2Updf7AEmK3I=", bean.peerPreSharedKey)
        assertTrue(bean.localAddress.contains("10.0.0.2/32"))
        assertTrue(bean.localAddress.contains("fd00::2/128"))
        assertEquals(1420, bean.mtu)
        assertEquals("1,2,3", bean.reserved)
        assertEquals(2, bean.workers)
        assertEquals(25, bean.persistentKeepalive)
        assertEquals(true, bean.systemInterface)
        assertEquals("test-wg", bean.name)

        val outbound = buildSingBoxOutboundWireguardBean(bean)
        assertEquals("wireguard", outbound.type)
        assertEquals(true, outbound.system)
        assertEquals(1420, outbound.mtu)
        assertEquals(2, outbound.workers)
        assertEquals(1, outbound.peers.size)
        assertEquals("1.2.3.4", outbound.peers[0].address)
        assertEquals(51820, outbound.peers[0].port)
        assertEquals(listOf(1, 2, 3), outbound.peers[0].reserved)
        assertEquals(25, outbound.peers[0].persistent_keepalive_interval)
    }

    @Test
    fun testShadowsocksUoTParsingAndBuilding() {
        val link = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@1.2.3.4:8388?uot=1&uot_version=2#test-ss"
        val bean = parseShadowsocks(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(8388, bean.serverPort)
        assertEquals("aes-256-gcm", bean.method)
        assertEquals("password", bean.password)
        assertEquals(true, bean.sUoT)
        assertEquals(2, bean.sUoTVersion)
        assertEquals("test-ss", bean.name)

        val outbound = buildSingBoxOutboundShadowsocksBean(bean)
        assertEquals("shadowsocks", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(8388, outbound.server_port)
        assertNotNull(outbound.udp_over_tcp)
        assertEquals(true, outbound.udp_over_tcp.enabled)
        assertEquals(2, outbound.udp_over_tcp.version)
    }

    @Test
    fun testHysteria2ParsingAndBuilding() {
        val link = "hy2://testpass@1.2.3.4:443?sni=example.com&hop_interval=10&hop_interval_max=30&pinSHA256=abcdef123456&bbr_profile=standard&obfs=salamander&obfs-password=obfspass#test-hy2"
        val bean = parseHysteria2(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals("443", bean.serverPorts)
        assertEquals("testpass", bean.authPayload)
        assertEquals("example.com", bean.sni)
        assertEquals(10, bean.hopInterval)
        assertEquals(30, bean.hopIntervalMax)
        assertEquals("abcdef123456", bean.pinSHA256)
        assertEquals("standard", bean.bbrProfile)
        assertEquals("salamander", bean.obfsType)
        assertEquals("obfspass", bean.obfuscation)
        assertEquals("test-hy2", bean.name)

        val outbound = buildSingBoxOutboundHysteriaBean(bean) as moe.matsuri.nb4a.SingBoxOptions.Outbound_Hysteria2Options
        assertEquals("hysteria2", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("10s", outbound.hop_interval)
        assertEquals("30s", outbound.hop_interval_max)
        assertEquals("standard", outbound.bbr_profile)
        assertNotNull(outbound.obfs)
        assertEquals("salamander", outbound.obfs.type)
        assertEquals("obfspass", outbound.obfs.password)
    }

    @Test
    fun testTuicParsingAndBuilding() {
        val link = "tuic://a5d09f63-0797-4b77-a859-96843440be54:token123@1.2.3.4:8443?congestion_control=bbr&udp_relay_mode=quic&udp_over_stream=1&heartbeat=15s&sni=example.com#test-tuic"
        val bean = parseTuic(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals("a5d09f63-0797-4b77-a859-96843440be54", bean.uuid)
        assertEquals("token123", bean.token)
        assertEquals("bbr", bean.congestionController)
        assertEquals("quic", bean.udpRelayMode)
        assertEquals(true, bean.udpOverStream)
        assertEquals(15, bean.heartbeat)
        assertEquals("example.com", bean.sni)
        assertEquals("test-tuic", bean.name)

        val outbound = buildSingBoxOutboundTuicBean(bean)
        assertEquals("tuic", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(8443, outbound.server_port)
        assertEquals("bbr", outbound.congestion_control)
        assertEquals("quic", outbound.udp_relay_mode)
        assertEquals(true, outbound.udp_over_stream)
        assertEquals("15s", outbound._hack_config_map["heartbeat"])
    }

    @Test
    fun testClashMetaYamlParsing() = runBlocking {
        val yaml = """
            proxies:
              - name: "Clash SS UoT"
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-256-gcm
                password: secret
                udp-over-tcp: true
                uot-version: 2

              - name: "Clash HY2"
                type: hysteria2
                server: 1.2.3.4
                port: 443
                password: hypass
                sni: hy2.example.com
                hop-interval: 15
                hop-interval-max: 45
                pin-sha256: 1234567890abcdef
                bbr: standard

              - name: "Clash TUIC"
                type: tuic
                server: 1.2.3.4
                port: 8443
                uuid: a5d09f63-0797-4b77-a859-96843440be54
                password: tuicpass
                udp-over-stream: true
                heartbeat: 12s

              - name: "Clash WireGuard"
                type: wireguard
                server: 1.2.3.4
                port: 51820
                ip: 10.0.0.2/32
                ipv6: fd00::2/128
                public-key: VNCR8uuNIIsW2+PpzeN2DwQmuz+ZirlLMoAIM1CulS0=
                private-key: J+qTi6nj6NRm9ncQ8vUn99YDnzvwifvxlTVSaOFXXe4=
                preshared-key: yD3a8mvUoKN4qEO3icjpYVECbFlaIpR2Updf7AEmK3I=
                reserved: [1, 2, 3]
                mtu: 1420
                workers: 4
                system-interface: true
        """.trimIndent()

        val proxies = RawUpdater.parseRaw(yaml)
        assertNotNull(proxies)
        assertEquals(4, proxies!!.size)

        val ssBean = proxies[0] as ShadowsocksBean
        assertEquals("Clash SS UoT", ssBean.name)
        assertEquals(true, ssBean.sUoT)
        assertEquals(2, ssBean.sUoTVersion)

        val hy2Bean = proxies[1] as HysteriaBean
        assertEquals("Clash HY2", hy2Bean.name)
        assertEquals(15, hy2Bean.hopInterval)
        assertEquals(45, hy2Bean.hopIntervalMax)
        assertEquals("1234567890abcdef", hy2Bean.pinSHA256)
        assertEquals("standard", hy2Bean.bbrProfile)

        val tuicBean = proxies[2] as TuicBean
        assertEquals("Clash TUIC", tuicBean.name)
        assertEquals(true, tuicBean.udpOverStream)
        assertEquals(12, tuicBean.heartbeat)

        val wgBean = proxies[3] as WireGuardBean
        assertEquals("Clash WireGuard", wgBean.name)
        assertEquals(51820, wgBean.serverPort)
        assertEquals("1,2,3", wgBean.reserved)
        assertEquals(4, wgBean.workers)
        assertEquals(true, wgBean.systemInterface)
    }
}
