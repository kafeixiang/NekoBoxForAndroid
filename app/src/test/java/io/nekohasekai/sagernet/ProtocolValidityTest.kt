package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.canUseSingBox
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria1
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria2
import io.nekohasekai.sagernet.fmt.hysteria.toUri
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.juicity.buildSingBoxOutboundJuicityBean
import io.nekohasekai.sagernet.fmt.juicity.parseJuicity
import io.nekohasekai.sagernet.fmt.juicity.toUri
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowquic.buildSingBoxOutboundShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowquic.parseShadowQUIC
import io.nekohasekai.sagernet.fmt.shadowquic.toUri
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.snell.buildSingBoxOutboundSnellBean
import io.nekohasekai.sagernet.fmt.snell.parseSnell
import io.nekohasekai.sagernet.fmt.snell.toUri
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.ssh.buildSingBoxOutboundSSHBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan.parseTrojan
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic.buildSingBoxOutboundTuicBean
import io.nekohasekai.sagernet.fmt.tuic.parseTuic
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray
import io.nekohasekai.sagernet.fmt.v2ray.toUriVMessVLESSTrojan
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxOutboundWireguardBean
import io.nekohasekai.sagernet.fmt.wireguard.parseWireGuardLink
import io.nekohasekai.sagernet.group.RawUpdater
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.proxy.anytls.AnyTLSBean
import moe.matsuri.nb4a.proxy.anytls.buildSingBoxOutboundAnyTLSBean
import moe.matsuri.nb4a.proxy.anytls.parseAnytls
import moe.matsuri.nb4a.proxy.anytls.toUri
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSBean
import moe.matsuri.nb4a.proxy.shadowtls.buildSingBoxOutboundShadowTLSBean
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.fmt.juicity.canUseSingBox
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.mieru.buildSingBoxOutboundMieruBean
import io.nekohasekai.sagernet.fmt.mieru.canUseSingBox
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildSingBoxOutboundNaiveBean
import io.nekohasekai.sagernet.fmt.naive.canUseSingBox
import io.nekohasekai.sagernet.fmt.naive.parseNaive
import io.nekohasekai.sagernet.fmt.naive.toUri
import io.nekohasekai.sagernet.fmt.openconnect.OpenConnectBean
import io.nekohasekai.sagernet.fmt.openconnect.buildSingBoxEndpointOpenConnectBean
import io.nekohasekai.sagernet.fmt.openvpn.OpenVPNBean
import io.nekohasekai.sagernet.fmt.openvpn.buildSingBoxEndpointOpenVPNBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.buildSingBoxOutboundShadowsocksRBean
import io.nekohasekai.sagernet.fmt.tailscale.TailcatBean
import io.nekohasekai.sagernet.fmt.tailscale.TailscaleBean
import io.nekohasekai.sagernet.fmt.tailscale.buildSingBoxEndpointTailscaleBean
import io.nekohasekai.sagernet.fmt.tailscale.buildSingBoxOutboundTailcatBean
import io.nekohasekai.sagernet.fmt.tor.TorBean
import io.nekohasekai.sagernet.fmt.tor.buildSingBoxOutboundTorBean
import io.nekohasekai.sagernet.fmt.trusttunnel.TrustTunnelBean
import io.nekohasekai.sagernet.fmt.trusttunnel.buildSingBoxOutboundTrustTunnelBean
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

        val outbound = buildSingBoxOutboundHysteriaBean(bean) as SingBoxOptions.Outbound_Hysteria2Options
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
    fun testHysteria2RealmParsingAndBuilding() {
        val link = "hy2://testpass@1.2.3.4:443?realm=1&realm_server=https://realm.example.com&realm_id=slot123&realm_token=mytoken&realm_stun=stun.l.google.com:19302,stun.cloudflare.com&realm_ip_version=4&realm_port_mapping=1&realm_port_mapping_timeout=15&realm_port_mapping_lifetime=900#test-hy2-realm"
        val bean = parseHysteria2(link)

        assertEquals(true, bean.enableRealm)
        assertEquals("https://realm.example.com", bean.realmServerURL)
        assertEquals("slot123", bean.realmId)
        assertEquals("mytoken", bean.realmToken)
        assertTrue(bean.realmStunServers.contains("stun.l.google.com:19302"))
        assertEquals(4, bean.realmIPVersion)
        assertEquals(true, bean.realmPortMapping)
        assertEquals(15, bean.realmPortMappingTimeout)
        assertEquals(900, bean.realmPortMappingLifetime)

        // Test outbound config generation
        val outbound = buildSingBoxOutboundHysteriaBean(bean) as SingBoxOptions.Outbound_Hysteria2Options
        assertEquals("hysteria2", outbound.type)
        // With realm, server and server_ports MUST be empty/omitted
        assertNull(outbound.server_port)
        assertTrue(outbound.server.isNullOrEmpty())
        assertNull(outbound.server_ports)

        assertNotNull(outbound.realm)
        assertEquals("https://realm.example.com", outbound.realm.server_url)
        assertEquals("slot123", outbound.realm.realm_id)
        assertEquals("mytoken", outbound.realm.token)
        assertEquals(2, outbound.realm.stun_servers.size)
        assertEquals(4, outbound.realm.ip_version)
        assertNotNull(outbound.realm.port_mapping)
        assertEquals(true, outbound.realm.port_mapping.enabled)
        assertEquals("15s", outbound.realm.port_mapping.timeout)
        assertEquals("900s", outbound.realm.port_mapping.lifetime)
        assertEquals("realm.example.com", outbound.tls.server_name)

        // Test clone & Kryo roundtrip
        val cloned = bean.clone()
        assertEquals(true, cloned.enableRealm)
        assertEquals("https://realm.example.com", cloned.realmServerURL)
        assertEquals("slot123", cloned.realmId)
        assertEquals("mytoken", cloned.realmToken)
        assertEquals(4, cloned.realmIPVersion)
        assertEquals(true, cloned.realmPortMapping)
        assertEquals(15, cloned.realmPortMappingTimeout)
        assertEquals(900, cloned.realmPortMappingLifetime)

        // Test URI serialization and re-parsing
        val uri = bean.toUri()
        assertTrue(uri.contains("realm=1"))
        assertTrue(uri.contains("realm_id=slot123"))
        val reParsed = parseHysteria2(uri)
        assertEquals(true, reParsed.enableRealm)
        assertEquals("https://realm.example.com", reParsed.realmServerURL)
        assertEquals("slot123", reParsed.realmId)
        assertEquals("mytoken", reParsed.realmToken)
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
    fun testShadowQUICParsingAndBuilding() {
        val link = "sq://user1:pass1@1.2.3.4:443?sni=sq.example.com&udp_mode=stream&congestion=bbr&zero_rtt=1&alpn=h3&sunny_quic=1#test-sq"
        val bean = parseShadowQUIC(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("user1", bean.username)
        assertEquals("pass1", bean.password)
        assertEquals("sq.example.com", bean.sni)
        assertEquals(true, bean.udpOverStream)
        assertEquals("bbr", bean.congestionControl)
        assertEquals(true, bean.zeroRttHandshake)
        assertEquals("h3", bean.alpn)
        assertEquals(true, bean.sunnyQuic)
        assertEquals("test-sq", bean.name)

        val exportedUri = bean.toUri()
        assertTrue(exportedUri.startsWith("sq://"))
        assertTrue(exportedUri.contains("udp_mode=stream"))
        assertTrue(exportedUri.contains("sunny_quic=true"))

        val outbound = buildSingBoxOutboundShadowQUICBean(bean)
        assertEquals("shadowquic", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("user1", outbound.username)
        assertEquals("pass1", outbound.password)
        assertEquals("sq.example.com", outbound.server_name)
        assertEquals(true, outbound.udp_over_stream)
        assertEquals(true, outbound.zero_rtt_handshake)
        assertEquals(true, outbound.sunny_quic)
        assertEquals("bbr", outbound.congestion_control)
        assertEquals(listOf("h3"), outbound.alpn)
    }

    @Test
    fun testJuicityParsingAndBuilding() {
        val link = "juicity://test-uuid:test-pass@1.2.3.4:443?sni=jui.example.com&pinned_certchain_sha256=1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef&allow_insecure=1#test-jui"
        val bean = parseJuicity(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("test-uuid", bean.uuid)
        assertEquals("test-pass", bean.password)
        assertEquals("jui.example.com", bean.sni)
        assertEquals(true, bean.allowInsecure)
        assertEquals("test-jui", bean.name)

        val exportedUri = bean.toUri()
        assertTrue(exportedUri.startsWith("juicity://"))
        assertTrue(exportedUri.contains("allow_insecure=1"))

        val outbound = buildSingBoxOutboundJuicityBean(bean)
        assertEquals("juicity", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("test-uuid", outbound.uuid)
        assertEquals("test-pass", outbound.password)
        assertNotNull(outbound.tls)
        assertEquals("jui.example.com", outbound.tls.server_name)
        assertEquals(true, outbound.tls.insecure)
    }

    @Test
    fun testVLESSRealityAndXHTTPParsingAndBuilding() {
        val link = "vless://a5d09f63-0797-4b77-a859-96843440be54@1.2.3.4:443?security=reality&sni=vless.example.com&pbk=1111111111111111111111111111111111111111111&sid=1234&fp=chrome&type=xhttp&mode=auto&path=/test-xhttp&encryption=none&flow=xtls-rprx-vision&spoof=apple.com&spoof_method=wrong-sequence#test-vless"
        val bean = parseV2Ray(link)

        assertTrue(bean is VMessBean && bean.isVLESS)
        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("a5d09f63-0797-4b77-a859-96843440be54", (bean as VMessBean).uuid)
        assertEquals("xhttp", bean.type)
        assertEquals("auto", bean.xhttpMode)
        assertEquals("/test-xhttp", bean.path)
        assertEquals("1111111111111111111111111111111111111111111", bean.realityPubKey)
        assertEquals("1234", bean.realityShortId)
        assertEquals("chrome", bean.utlsFingerprint)
        assertEquals("apple.com", bean.tlsSpoof)
        assertEquals("wrong-sequence", bean.tlsSpoofMethod)
        assertEquals("test-vless", bean.name)

        val exportedUri = bean.toUriVMessVLESSTrojan(false)
        assertTrue(exportedUri.startsWith("vless://"))
        assertTrue(exportedUri.contains("type=xhttp"))
        assertTrue(exportedUri.contains("pbk="))

        val outbound = buildSingBoxOutboundStandardV2RayBean(bean) as SingBoxOptions.Outbound_VLESSOptions
        assertEquals("vless", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("a5d09f63-0797-4b77-a859-96843440be54", outbound.uuid)
        assertNotNull(outbound.tls)
        assertNotNull(outbound.tls.reality)
        assertEquals(true, outbound.tls.reality.enabled)
        assertEquals("1111111111111111111111111111111111111111111", outbound.tls.reality.public_key)
        assertEquals("1234", outbound.tls.reality.short_id)
        assertEquals("apple.com", outbound.tls.spoof)
        assertEquals("wrong-sequence", outbound.tls.spoof_method)
        assertNotNull(outbound.transport)
        assertEquals("xhttp", outbound.transport.type)
        assertEquals("auto", (outbound.transport as SingBoxOptions.V2RayTransportOptions_XHTTPOptions).mode)
        assertEquals("/test-xhttp", (outbound.transport as SingBoxOptions.V2RayTransportOptions_XHTTPOptions).path)
    }

    @Test
    fun testTrojanAndShadowTLSParsingAndBuilding() {
        val trojanLink = "trojan://trojanpassword@1.2.3.4:443?sni=trojan.example.com&allowInsecure=1#test-trojan"
        val trojanBean = parseTrojan(trojanLink)
        assertEquals("1.2.3.4", trojanBean.serverAddress)
        assertEquals(443, trojanBean.serverPort)
        assertEquals("trojanpassword", trojanBean.password)
        assertEquals("trojan.example.com", trojanBean.sni)
        assertEquals(true, trojanBean.allowInsecure)
        assertEquals("test-trojan", trojanBean.name)

        val trojanOutbound = buildSingBoxOutboundStandardV2RayBean(trojanBean) as SingBoxOptions.Outbound_TrojanOptions
        assertEquals("trojan", trojanOutbound.type)
        assertEquals("1.2.3.4", trojanOutbound.server)
        assertEquals(443, trojanOutbound.server_port)
        assertEquals("trojanpassword", trojanOutbound.password)
        assertNotNull(trojanOutbound.tls)
        assertEquals(true, trojanOutbound.tls.insecure)
        assertEquals("trojan.example.com", trojanOutbound.tls.server_name)

        val shadowTLSBean = ShadowTLSBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 443
            password = "stlspassword"
            version = 3
            sni = "stls.example.com"
            security = "tls"
        }
        val stlsOutbound = buildSingBoxOutboundShadowTLSBean(shadowTLSBean)
        assertEquals("shadowtls", stlsOutbound.type)
        assertEquals("1.2.3.4", stlsOutbound.server)
        assertEquals(443, stlsOutbound.server_port)
        assertEquals("stlspassword", stlsOutbound.password)
        assertEquals(3, stlsOutbound.version)
        assertNotNull(stlsOutbound.tls)
        assertEquals("stls.example.com", stlsOutbound.tls.server_name)
    }

    @Test
    fun testAnyTLSParsingAndBuilding() {
        val anytlsLink = "anytls://anypass@1.2.3.4:443?sni=anytls.example.com&insecure=1&fp=chrome&pbk=1111111111111111111111111111111111111111111&sid=abcd#test-anytls"
        val anytlsBean = parseAnytls(anytlsLink)
        assertEquals("1.2.3.4", anytlsBean.serverAddress)
        assertEquals(443, anytlsBean.serverPort)
        assertEquals("anypass", anytlsBean.password)
        assertEquals("anytls.example.com", anytlsBean.sni)
        assertEquals(true, anytlsBean.allowInsecure)
        assertEquals("chrome", anytlsBean.utlsFingerprint)
        assertEquals("1111111111111111111111111111111111111111111", anytlsBean.realityPubKey)
        assertEquals("abcd", anytlsBean.realityShortId)
        assertEquals("test-anytls", anytlsBean.name)

        val anytlsOutbound = buildSingBoxOutboundAnyTLSBean(anytlsBean)
        assertEquals("anytls", anytlsOutbound.type)
        assertEquals("1.2.3.4", anytlsOutbound.server)
        assertEquals(443, anytlsOutbound.server_port)
        assertEquals("anypass", anytlsOutbound.password)
        assertNotNull(anytlsOutbound.tls)
        assertEquals("anytls.example.com", anytlsOutbound.tls.server_name)
        assertEquals(true, anytlsOutbound.tls.insecure)
        assertNotNull(anytlsOutbound.tls.reality)
        assertEquals("1111111111111111111111111111111111111111111", anytlsOutbound.tls.reality.public_key)
        assertEquals("abcd", anytlsOutbound.tls.reality.short_id)
    }

    @Test
    fun testHysteria1ParsingAndBuilding() {
        val hy1Link = "hysteria://1.2.3.4:443?peer=hy1.example.com&auth=myauth&upmbps=50&downmbps=100&obfs=hy1obfs#test-hy1"
        val hy1Bean = parseHysteria1(hy1Link)
        assertEquals("1.2.3.4", hy1Bean.serverAddress)
        assertEquals("443", hy1Bean.serverPorts)
        assertEquals("myauth", hy1Bean.authPayload)
        assertEquals("hy1.example.com", hy1Bean.sni)
        assertEquals(50, hy1Bean.uploadMbps)
        assertEquals(100, hy1Bean.downloadMbps)
        assertEquals("test-hy1", hy1Bean.name)

        val hy1Outbound = buildSingBoxOutboundHysteriaBean(hy1Bean) as SingBoxOptions.Outbound_HysteriaOptions
        assertEquals("hysteria", hy1Outbound.type)
        assertEquals("1.2.3.4", hy1Outbound.server)
        assertEquals(443, hy1Outbound.server_port)
        assertEquals("myauth", hy1Outbound.auth_str)
        assertEquals(50, hy1Outbound.up_mbps)
        assertEquals(100, hy1Outbound.down_mbps)
    }

    @Test
    fun testSnellAndSSHParsingAndBuilding() {
        val snellBean = SnellBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 8000
            psk = "snellpsk"
            version = 4
            obfsMode = "http"
            obfsHost = "snell.example.com"
            reuse = true
        }
        val snellOutbound = buildSingBoxOutboundSnellBean(snellBean)
        assertEquals("snell", snellOutbound.type)
        assertEquals("1.2.3.4", snellOutbound.server)
        assertEquals(8000, snellOutbound.server_port)
        assertEquals("snellpsk", snellOutbound.psk)
        assertEquals(4, snellOutbound.version)
        assertEquals("http", snellOutbound.obfs_mode)
        assertEquals("snell.example.com", snellOutbound.obfs_host)
        assertEquals(true, snellOutbound.reuse)

        // Test Snell v6 building
        val snell6Bean = SnellBean().apply {
            serverAddress = "127.0.0.1"
            serverPort = 18260
            psk = "test-snell-psk-123456"
            version = 6
            mode = "unshaped"
            userKey = "user123"
            reuse = true
        }
        val snell6Outbound = buildSingBoxOutboundSnellBean(snell6Bean)
        assertEquals("snell", snell6Outbound.type)
        assertEquals("127.0.0.1", snell6Outbound.server)
        assertEquals(18260, snell6Outbound.server_port)
        assertEquals("test-snell-psk-123456", snell6Outbound.psk)
        assertEquals(6, snell6Outbound.version)
        assertEquals("unshaped", snell6Outbound.mode)
        assertEquals("user123", snell6Outbound.userkey)
        assertEquals(true, snell6Outbound.reuse)

        // Test Snell URI roundtrip
        val uri = snell6Bean.toUri()
        val parsed = parseSnell(uri)
        assertEquals("127.0.0.1", parsed.serverAddress)
        assertEquals(18260, parsed.serverPort)
        assertEquals("test-snell-psk-123456", parsed.psk)
        assertEquals(6, parsed.version)
        assertEquals("unshaped", parsed.mode)
        assertEquals("user123", parsed.userKey)
        assertEquals(true, parsed.reuse)

        val sshBean = SSHBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 22
            username = "root"
            password = "sshpassword"
            authType = SSHBean.AUTH_TYPE_PASSWORD
        }
        val sshOutbound = buildSingBoxOutboundSSHBean(sshBean)
        assertEquals("ssh", sshOutbound.type)
        assertEquals("1.2.3.4", sshOutbound.server)
        assertEquals(22, sshOutbound.server_port)
        assertEquals("root", sshOutbound.user)
        assertEquals("sshpassword", sshOutbound.password)
    }

    @Test
    fun testNaiveParsingAndBuilding() {
        val link = "naive+https://testuser:testpassword@naive.example.com:443?sni=sni.example.com&cert=mycert&insecure-concurrency=4#test-naive"
        val bean = parseNaive(link)

        assertEquals("https", bean.proto)
        assertEquals("naive.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("testuser", bean.username)
        assertEquals("testpassword", bean.password)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("mycert", bean.certificates)
        assertEquals(4, bean.insecureConcurrency)
        assertEquals("test-naive", bean.name)

        val outbound = buildSingBoxOutboundNaiveBean(bean)
        assertEquals("naive", outbound.type)
        assertEquals("naive.example.com", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("testuser", outbound.username)
        assertEquals("testpassword", outbound.password)
        assertEquals(4, outbound.insecure_concurrency)
        assertNotNull(outbound.tls)
        assertEquals(true, outbound.tls.enabled)
        assertEquals("sni.example.com", outbound.tls.server_name)
        assertEquals("mycert", outbound.tls.certificate)
        assertNull(outbound.tls.fragment)
        assertNull(outbound.tls.record_fragment)

        // Test clone & Kryo round-trip
        val cloned = bean.clone()
        assertEquals("https", cloned.proto)
        assertEquals("naive.example.com", cloned.serverAddress)
        assertEquals(443, cloned.serverPort)
        assertEquals("testuser", cloned.username)
        assertEquals("testpassword", cloned.password)
        assertEquals("sni.example.com", cloned.sni)
        assertEquals("mycert", cloned.certificates)
        assertEquals(4, cloned.insecureConcurrency)

        // Test URI roundtrip
        val uri = bean.toUri()
        val reParsed = parseNaive(uri)
        assertEquals("https", reParsed.proto)
        assertEquals("naive.example.com", reParsed.serverAddress)
        assertEquals(443, reParsed.serverPort)
        assertEquals("testuser", reParsed.username)
        assertEquals("testpassword", reParsed.password)
        assertEquals("sni.example.com", reParsed.sni)
        assertEquals("mycert", reParsed.certificates)
        assertEquals(4, reParsed.insecureConcurrency)
    }

    @Test
    fun testMieruParsingAndBuilding() {
        // Test Mieru with port ranges and multiplexing
        val bean = MieruBean().apply {
            serverAddress = "mieru.example.com"
            serverPorts = "2000-3000, 8443"
            protocol = "tcp"
            username = "testuser"
            password = "testpassword"
            multiplexing = "MULTIPLEXING_MIDDLE"
        }
        val outbound = buildSingBoxOutboundMieruBean(bean)
        assertEquals("mieru", outbound.type)
        assertEquals("mieru.example.com", outbound.server)
        assertEquals("TCP", outbound.transport)
        assertEquals("testuser", outbound.username)
        assertEquals("testpassword", outbound.password)
        assertEquals("MULTIPLEXING_MIDDLE", outbound.multiplexing)
        assertEquals(2, outbound.server_ports.size)
        assertEquals("2000-3000", outbound.server_ports[0])
        assertEquals("8443-8443", outbound.server_ports[1])

        // Test Mieru with single port
        val singlePortBean = MieruBean().apply {
            serverAddress = "1.2.3.4"
            serverPorts = "8443"
            protocol = "UDP"
            username = "u"
            password = "p"
            mtu = 1350
        }
        val singleOutbound = buildSingBoxOutboundMieruBean(singlePortBean)
        assertEquals(8443, singleOutbound.server_port)
        assertNull(singleOutbound.server_ports)
        assertEquals("UDP", singleOutbound.transport)

        // Test clone & Kryo roundtrip
        val cloned = singlePortBean.clone()
        assertEquals("1.2.3.4", cloned.serverAddress)
        assertEquals("8443", cloned.serverPorts)
        assertEquals("UDP", cloned.protocol)
        assertEquals("u", cloned.username)
        assertEquals("p", cloned.password)
        assertEquals(1350, cloned.mtu)
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

              - name: "Clash Juicity"
                type: juicity
                server: 1.2.3.4
                port: 443
                uuid: 12345678-1234-1234-1234-123456789012
                password: juipassword
                sni: jui.example.com
                skip-cert-verify: true

              - name: "Clash ShadowQUIC"
                type: shadowquic
                server: 1.2.3.4
                port: 443
                username: squser
                password: sqpass
                sni: sq.example.com
                udp-mode: stream
                zero-rtt: true
                congestion: bbr

              - name: "Clash VLESS XHTTP Reality"
                type: vless
                server: 1.2.3.4
                port: 443
                uuid: a5d09f63-0797-4b77-a859-96843440be54
                network: xhttp
                xhttp-opts:
                  mode: auto
                  path: /my-xhttp
                  host: xhttp.example.com
                reality-opts:
                  public-key: 1111111111111111111111111111111111111111111
                  short-id: "1234"
                client-fingerprint: chrome

              - name: "Clash Trojan"
                type: trojan
                server: 1.2.3.4
                port: 443
                password: trojanpassword
                sni: trojan.example.com
                skip-cert-verify: true

              - name: "Clash AnyTLS"
                type: anytls
                server: 1.2.3.4
                port: 443
                password: anypassword
                sni: anytls.example.com
                skip-cert-verify: true
        """.trimIndent()

        val proxies = RawUpdater.parseRaw(yaml)
        assertNotNull(proxies)
        assertEquals(9, proxies!!.size)

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

        val juiBean = proxies[4] as JuicityBean
        assertEquals("Clash Juicity", juiBean.name)
        assertEquals("12345678-1234-1234-1234-123456789012", juiBean.uuid)
        assertEquals("juipassword", juiBean.password)
        assertEquals("jui.example.com", juiBean.sni)
        assertEquals(true, juiBean.allowInsecure)

        val sqBean = proxies[5] as ShadowQUICBean
        assertEquals("Clash ShadowQUIC", sqBean.name)
        assertEquals("squser", sqBean.username)
        assertEquals("sqpass", sqBean.password)
        assertEquals("sq.example.com", sqBean.sni)
        assertEquals(true, sqBean.udpOverStream)
        assertEquals(true, sqBean.zeroRttHandshake)
        assertEquals("bbr", sqBean.congestionControl)

        val vlessBean = proxies[6] as VMessBean
        assertEquals("Clash VLESS XHTTP Reality", vlessBean.name)
        assertTrue(vlessBean.isVLESS)
        assertEquals("xhttp", vlessBean.type)
        assertEquals("auto", vlessBean.xhttpMode)
        assertEquals("/my-xhttp", vlessBean.path)
        assertEquals("1111111111111111111111111111111111111111111", vlessBean.realityPubKey)
        assertEquals("1234", vlessBean.realityShortId)
        assertEquals("chrome", vlessBean.utlsFingerprint)

        val trojanBean = proxies[7] as TrojanBean
        assertEquals("Clash Trojan", trojanBean.name)
        assertEquals("trojanpassword", trojanBean.password)
        assertEquals("trojan.example.com", trojanBean.sni)
        assertEquals(true, trojanBean.allowInsecure)

        val anyBean = proxies[8] as AnyTLSBean
        assertEquals("Clash AnyTLS", anyBean.name)
        assertEquals("anypassword", anyBean.password)
        assertEquals("anytls.example.com", anyBean.sni)
        assertEquals(true, anyBean.allowInsecure)
    }

    @Test
    fun testShadowsocksWithShadowTLSPlugin() {
        val link = "ss://MjAyMi1ibGFrZTMtYWVzLTEyOC1nY206c3NwYXNzd29yZA==@1.2.3.4:443?plugin=shadow-tls%3Bhost%3Dgateway.icloud.com%3Bpassword%3Dstlspass%3Bversion%3D3%3Bfingerprint%3Dchrome#test-ss-stls"
        val bean = parseShadowsocks(link)

        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("2022-blake3-aes-128-gcm", bean.method)
        assertEquals("sspassword", bean.password)
        assertEquals("shadow-tls;host=gateway.icloud.com;password=stlspass;version=3;fingerprint=chrome", bean.plugin)
        assertEquals("test-ss-stls", bean.name)

        val opts = PluginOptions("shadow-tls", bean.plugin.substringAfter(";"))
        assertEquals("gateway.icloud.com", opts["host"])
        assertEquals("stlspass", opts["password"])
        assertEquals("3", opts["version"])
        assertEquals("chrome", opts["fingerprint"])

        val outbound = buildSingBoxOutboundShadowsocksBean(bean)
        assertEquals("shadowsocks", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertNull(outbound.plugin)
        assertNull(outbound.plugin_opts)
        assertNotNull(outbound.udp_over_tcp)
        assertEquals(true, outbound.udp_over_tcp.enabled)
    }

    @Test
    fun testClashShadowsocksShadowTLS() = runBlocking {
        val yaml = """
            proxies:
              - name: "SS-ShadowTLS"
                type: ss
                server: 1.2.3.4
                port: 443
                cipher: 2022-blake3-aes-128-gcm
                password: "sspassword"
                plugin: shadow-tls
                plugin-opts:
                  host: gateway.icloud.com
                  password: shadowtlspassword
                  version: 3
                  client-fingerprint: chrome
              - name: "Standalone-ShadowTLS"
                type: shadow-tls
                server: 5.6.7.8
                port: 8443
                password: standalonepassword
                version: 3
                sni: gateway.icloud.com
                client-fingerprint: chrome
        """.trimIndent()

        val proxies = RawUpdater.parseRaw(yaml)
        assertNotNull(proxies)
        assertEquals(2, proxies!!.size)

        val ssBean = proxies[0] as ShadowsocksBean
        assertEquals("SS-ShadowTLS", ssBean.name)
        assertEquals("1.2.3.4", ssBean.serverAddress)
        assertEquals(443, ssBean.serverPort)
        assertEquals("2022-blake3-aes-128-gcm", ssBean.method)
        assertEquals("sspassword", ssBean.password)
        assertEquals(true, ssBean.sUoT)
        assertEquals("shadow-tls;host=gateway.icloud.com;password=shadowtlspassword;version=3;fingerprint=chrome", ssBean.plugin)

        val stlsBean = proxies[1] as ShadowTLSBean
        assertEquals("Standalone-ShadowTLS", stlsBean.name)
        assertEquals("5.6.7.8", stlsBean.serverAddress)
        assertEquals(8443, stlsBean.serverPort)
        assertEquals("standalonepassword", stlsBean.password)
        assertEquals(3, stlsBean.version)
        assertEquals("gateway.icloud.com", stlsBean.sni)
        assertEquals("chrome", stlsBean.utlsFingerprint)

        val stlsOutbound = buildSingBoxOutboundShadowTLSBean(stlsBean)
        assertEquals("shadowtls", stlsOutbound.type)
        assertEquals("5.6.7.8", stlsOutbound.server)
        assertEquals(8443, stlsOutbound.server_port)
        assertEquals(3, stlsOutbound.version)
        assertEquals("standalonepassword", stlsOutbound.password)
        assertNotNull(stlsOutbound.tls)
        assertEquals("gateway.icloud.com", stlsOutbound.tls.server_name)
    }

    @Test
    fun testHysteriaPluginFallbackToCore() {
        val bean1 = HysteriaBean().apply {
            protocolVersion = 1
            serverAddress = "1.2.3.4"
            serverPort = 443
            authPayload = "testpass"
        }
        val entity1 = ProxyEntity().apply {
            type = ProxyEntity.TYPE_HYSTERIA
            hysteriaBean = bean1
        }
        assertTrue(bean1.canUseSingBox())
        assertFalse(entity1.needExternal())
        assertTrue(entity1.canRunOrInternal())

        val bean2 = HysteriaBean().apply {
            protocolVersion = 2
            serverAddress = "5.6.7.8"
            serverPort = 8443
            authPayload = "testpass"
        }
        val entity2 = ProxyEntity().apply {
            type = ProxyEntity.TYPE_HYSTERIA
            hysteriaBean = bean2
        }
        assertTrue(bean2.canUseSingBox())
        assertFalse(entity2.needExternal())
        assertTrue(entity2.canRunOrInternal())

        // Test Juicity prioritizes sing-box native core
        val juicityBean = JuicityBean().apply {
            serverAddress = "juicity.example.com"
            serverPort = 443
            uuid = "test-uuid"
            password = "test-password"
        }
        val juicityEntity = ProxyEntity().apply {
            type = ProxyEntity.TYPE_JUICITY
            this.juicityBean = juicityBean
        }
        assertTrue(juicityBean.canUseSingBox())
        assertFalse(juicityEntity.needExternal())
        assertTrue(juicityEntity.canRunOrInternal())

        // Test Naive prioritizes sing-box native core
        val naiveBean = NaiveBean().apply {
            serverAddress = "naive.example.com"
            serverPort = 443
            username = "user"
            password = "pwd"
        }
        val naiveEntity = ProxyEntity().apply {
            type = ProxyEntity.TYPE_NAIVE
            this.naiveBean = naiveBean
        }
        assertTrue(naiveBean.canUseSingBox())
        assertFalse(naiveEntity.needExternal())
        assertTrue(naiveEntity.canRunOrInternal())

        // Test Mieru prioritizes sing-box native core
        val mieruBean = MieruBean().apply {
            serverAddress = "mieru.example.com"
            serverPorts = "443-2566"
            username = "2886685"
            password = "pwd"
        }
        val mieruEntity = ProxyEntity().apply {
            type = ProxyEntity.TYPE_MIERU
            this.mieruBean = mieruBean
        }
        assertTrue(mieruBean.canUseSingBox())
        assertFalse(mieruEntity.needExternal())
        assertTrue(mieruEntity.canRunOrInternal())

        // Test Shadowsocks native core
        val ssBean = ShadowsocksBean().apply {
            serverAddress = "1.1.1.1"
            serverPort = 8388
            password = "pwd"
            method = "2022-blake3-aes-128-gcm"
        }
        val ssEntity = ProxyEntity().apply {
            type = ProxyEntity.TYPE_SS
            this.ssBean = ssBean
        }
        assertFalse(ssEntity.needExternal())
        assertTrue(ssEntity.canRunOrInternal())
    }

    @Test
    fun testTrustTunnelParsingAndBuilding() {
        val bean = TrustTunnelBean().apply {
            name = "test-trusttunnel"
            serverAddress = "1.2.3.4"
            serverPort = 443
            username = "ttuser"
            password = "ttpassword"
            sni = "tt.example.com"
            quic = true
            healthCheck = true
            alpn = "h3"
            allowInsecure = false
        }
        val outbound = buildSingBoxOutboundTrustTunnelBean(bean)
        assertEquals("trusttunnel", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("ttuser", outbound.username)
        assertEquals("ttpassword", outbound.password)
        assertEquals(true, outbound.quic)
        assertEquals("tt.example.com", outbound.tls?.server_name)
    }

    @Test
    fun testOpenVPNBuilding() {
        val bean = OpenVPNBean().apply {
            name = "test-openvpn"
            serverAddress = "1.2.3.4"
            serverPort = 1194
            network = "udp"
            username = "vpnuser"
            password = "vpnpassword"
            cipher = "AES-256-GCM"
            auth = "SHA256"
            mtu = 1500
        }
        val endpoint = buildSingBoxEndpointOpenVPNBean(bean)
        assertEquals("openvpn-client", endpoint.type)
        assertEquals("1.2.3.4", endpoint.server)
        assertEquals(1194, endpoint.server_port)
        assertEquals("udp", endpoint.network)
        assertEquals("vpnuser", endpoint.username)
        assertEquals("vpnpassword", endpoint.password)
        assertEquals("AES-256-GCM", endpoint.cipher)
        assertEquals("SHA256", endpoint.auth)
        assertEquals(1500, endpoint.mtu)
    }

    @Test
    fun testOpenConnectBuilding() {
        val bean = OpenConnectBean().apply {
            name = "test-openconnect"
            server = "1.2.3.4:443"
            username = "ocuser"
            password = "ocpassword"
            flavor = "anyconnect"
            userAgent = "AnyConnect-compatible Agent"
        }
        val endpoint = buildSingBoxEndpointOpenConnectBean(bean)
        assertEquals("openconnect", endpoint.type)
        assertEquals("1.2.3.4:443", endpoint.server)
        assertEquals("ocuser", endpoint.username)
        assertEquals("ocpassword", endpoint.password)
        assertEquals("anyconnect", endpoint.flavor)
    }

    @Test
    fun testTailcatAndTailscaleBuilding() {
        val tailcatBean = TailcatBean().apply {
            name = "test-tailcat"
            privateKey = "privkey123"
            serverPublicKey = "pubkey456"
            serverDiscoKey = "discokey789"
            preSharedKey = "psk000"
            derpRegion = 1
        }
        val tailcatOutbound = buildSingBoxOutboundTailcatBean(tailcatBean)
        assertEquals("tailcat", tailcatOutbound.type)
        assertEquals("privkey123", tailcatOutbound.private_key)
        assertEquals("pubkey456", tailcatOutbound.server_public_key)
        assertEquals("discokey789", tailcatOutbound.server_disco_key)
        assertEquals("psk000", tailcatOutbound.pre_shared_key)
        assertEquals(1, tailcatOutbound.derp_region)

        val tailscaleBean = TailscaleBean().apply {
            name = "test-tailscale"
            authKey = "tskey-auth-12345"
            controlURL = "https://controlplane.tailscale.com"
            hostname = "neko-client"
            stateDirectory = "/data/data/tailscale"
        }
        val tailscaleEndpoint = buildSingBoxEndpointTailscaleBean(tailscaleBean, listOf("198.18.0.0/15"))
        assertEquals("tailscale", tailscaleEndpoint.type)
        assertEquals("/data/data/tailscale", tailscaleEndpoint.state_directory)
        assertEquals("tskey-auth-12345", tailscaleEndpoint.auth_key)
        assertEquals("https://controlplane.tailscale.com", tailscaleEndpoint.control_url)
        assertEquals("neko-client", tailscaleEndpoint.hostname)
    }

    @Test
    fun testTorBuilding() {
        val bean = TorBean().apply {
            name = "test-tor"
            executablePath = "/system/bin/tor"
            dataDirectory = "/data/data/io.nekohasekai.sagernet/tor"
            extraArgs = "--quiet"
        }
        val outbound = buildSingBoxOutboundTorBean(bean)
        assertEquals("tor", outbound.type)
        assertEquals("/system/bin/tor", outbound.executable_path)
        assertEquals("/data/data/io.nekohasekai.sagernet/tor", outbound.data_directory)
        assertEquals(listOf("--quiet"), outbound.extra_args)
    }

    @Test
    fun testMieruBuilding() {
        val bean = MieruBean().apply {
            name = "test-mieru"
            serverAddress = "1.2.3.4"
            serverPort = 12345
            username = "mieruuser"
            password = "mierupassword"
            protocol = "TCP"
            trafficPattern = "UNKNOWN"
        }
        val outbound = buildSingBoxOutboundMieruBean(bean)
        assertEquals("mieru", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(12345, outbound.server_port)
        assertEquals("mieruuser", outbound.username)
        assertEquals("mierupassword", outbound.password)
        assertEquals("TCP", outbound.transport)
    }

    @Test
    fun testShadowsocksRBuilding() {
        val bean = ShadowsocksRBean().apply {
            name = "test-ssr"
            serverAddress = "1.2.3.4"
            serverPort = 8388
            method = "aes-128-cfb"
            password = "ssrpassword"
            protocol = "auth_aes128_md5"
            protocolParam = "123"
            obfs = "http_simple"
            obfsParam = "cloudflare.com"
        }
        val outbound = buildSingBoxOutboundShadowsocksRBean(bean)
        assertEquals("shadowsocksr", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(8388, outbound.server_port)
        assertEquals("aes-128-cfb", outbound.method)
        assertEquals("ssrpassword", outbound.password)
        assertEquals("auth_aes128_md5", outbound.protocol)
        assertEquals("123", outbound.protocol_param)
        assertEquals("http_simple", outbound.obfs)
        assertEquals("cloudflare.com", outbound.obfs_param)
    }

    @Test
    fun testVLESSKCPAndEncryptionBuilding() {
        val bean = VMessBean().apply {
            alterId = -1
            name = "test-vless-kcp-enc"
            serverAddress = "1.2.3.4"
            serverPort = 443
            uuid = "a5d09f63-0797-4b77-a859-96843440be54"
            type = "kcp"
            headerType = "wechat-video"
            mKcpSeed = "kcpseed123"
            kcpMtu = 1350
            kcpTti = 50
            vlessEncryption = "mlkem768x25519plus.native.1rtt.dGVzdA"
        }
        val outbound = buildSingBoxOutboundStandardV2RayBean(bean) as SingBoxOptions.Outbound_VLESSOptions
        assertEquals("vless", outbound.type)
        assertEquals("1.2.3.4", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("a5d09f63-0797-4b77-a859-96843440be54", outbound.uuid)
        assertEquals("mlkem768x25519plus.native.1rtt.dGVzdA", outbound.encryption)
        assertNotNull(outbound.transport)
        assertEquals("kcp", outbound.transport?.type)
        val kcp = outbound.transport as SingBoxOptions.V2RayTransportOptions_KCPOptions
        assertEquals("wechat-video", kcp.header_type)
        assertEquals("kcpseed123", kcp.seed)
        assertEquals(1350, kcp.mtu)
        assertEquals(50, kcp.tti)
    }

    @Test
    fun testActivityClassPresence() {
        val expectedActivities = listOf(
            "io.nekohasekai.sagernet.ui.profile.JuicitySettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.TrustTunnelSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.OpenVPNSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.OpenConnectSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.TailcatSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.TailscaleSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.TorSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.ShadowsocksRSettingsActivity"
        )
        for (className in expectedActivities) {
            val clazz = Class.forName(className)
            assertNotNull("Class $className should be loadable", clazz)
            assertTrue(
                "Class $className should inherit from ProfileSettingsActivity",
                io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity::class.java.isAssignableFrom(clazz)
            )
        }
    }
}
