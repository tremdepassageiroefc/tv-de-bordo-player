package br.tvbordo.player

import java.net.Inet4Address
import java.net.NetworkInterface

object NetInfo {
    /** IPs IPv4 da box (ex.: "eth0 192.168.50.103"). Vazio = sem rede. */
    fun ipv4List(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .map { "${nif.name} ${it.hostAddress}" }
            }
    } catch (e: Exception) {
        emptyList()
    }

    fun summary(): String = ipv4List().joinToString("   ").ifEmpty { "sem rede (confira o cabo)" }
}
