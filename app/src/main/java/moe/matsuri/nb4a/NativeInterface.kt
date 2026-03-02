package moe.matsuri.nb4a

import android.annotation.SuppressLint
import android.content.Context
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.DefaultNetworkMonitor
import io.nekohasekai.sagernet.bg.ServiceNotification
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.PackageCache
import libbox.*
import moe.matsuri.nb4a.net.LocalResolver
import moe.matsuri.nb4a.utils.LibcoreUtil
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import libbox.NetworkInterface as LibboxNetworkInterface

class NativeInterface : PlatformInterface, NB4AInterface {

    override fun localDNSTransport(): LocalDNSTransport = LocalResolver

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        DataStore.vpnService?.protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        return DataStore.vpnService?.startVpn(options) ?: throw Exception("no VpnService")
    }

    override fun writeLog(message: String) {
        Libbox.nekoLogPrintln(message)
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int {
        return SagerNet.connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
    }

    override fun packageNameByUid(uid: Int): String {
        PackageCache.awaitLoadSync()
        if (uid <= 1000) return "android"
        return PackageCache.uidMap[uid]?.firstOrNull() ?: ""
    }

    override fun uidByPackageName(packageName: String): Int {
        PackageCache.awaitLoadSync()
        return PackageCache[packageName] ?: 0
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        @Suppress("DEPRECATION")
        val networks = SagerNet.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibboxNetworkInterface()
            val linkProperties = SagerNet.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities = SagerNet.connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface = networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer = StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
            boxInterface.type = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            boxInterface.index = networkInterface.index
            runCatching { boxInterface.mtu = networkInterface.mtu }
            boxInterface.addresses = StringArray(networkInterface.interfaceAddresses.map { it.toPrefix() }.iterator())

            var dumpFlags = 0
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) dumpFlags = dumpFlags or (OsConstants.IFF_UP or OsConstants.IFF_RUNNING)
            if (networkInterface.isLoopback) dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
            if (networkInterface.isPointToPoint) dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
            if (networkInterface.supportsMulticast()) dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
            boxInterface.flags = dumpFlags
            boxInterface.metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator())
    }

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun clearDNSCache() {}
    override fun sendNotification(notification: Notification) {}

    override fun readWIFIState(): WIFIState {
        val wifiManager = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        return WIFIState(info.ssid ?: "", info.bssid ?: "")
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val cert = keyStore.getCertificate(aliases.nextElement())
                certificates.add("-----BEGIN CERTIFICATE-----\n${Base64.encode(cert.encoded)}\n-----END CERTIFICATE-----")
            }
        }
        return StringArray(certificates.iterator())
    }

    private class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    private class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private fun InterfaceAddress.toPrefix(): String = "${address.hostAddress}/$networkPrefixLength"

    override fun useOfficialAssets(): Boolean = DataStore.rulesProvider == 0

    override fun selector_OnProxySelected(selectorTag: String, tag: String) {
        if (selectorTag != "proxy") return
        LibcoreUtil.resetAllConnections(true)
        DataStore.baseService?.apply {
            runOnDefaultDispatcher {
                val id = data.proxy!!.config.profileTagMap.filterValues { it == tag }.keys.firstOrNull() ?: -1
                val ent = SagerDatabase.proxyDao.getById(id) ?: return@runOnDefaultDispatcher
                data.proxy?.apply {
                    looper?.selectMain(id)
                    displayProfileName = ServiceNotification.genTitle(ent)
                    data.notification?.postNotificationTitle(displayProfileName)
                }
                data.binder.broadcast { it.cbSelectorUpdate(id) }
            }
        }
    }
}
