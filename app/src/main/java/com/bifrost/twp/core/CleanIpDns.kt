package com.bifrost.twp.core

import okhttp3.Dns
import java.net.InetAddress

/**
 * Custom OkHttp DNS implementation that resolves the Cloudflare Worker domain
 * to a user-configured Clean IP (CDN IP), while allowing OkHttp to properly
 * set the TLS Server Name Indication (SNI) and HTTP Host header to the Worker domain.
 *
 * @param targetHost The Cloudflare Worker hostname (e.g. "my-proxy.workers.dev").
 * @param cleanIp Optional Clean IP (e.g. "104.16.132.229").
 */
class CleanIpDns(
    private val targetHost: String,
    private val cleanIp: String?
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        if (!cleanIp.isNullOrBlank() && hostname.equals(targetHost, ignoreCase = true)) {
            return try {
                listOf(InetAddress.getByName(cleanIp.trim()))
            } catch (e: Exception) {
                // If clean IP parsing fails, fallback to system DNS
                Dns.SYSTEM.lookup(hostname)
            }
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}
