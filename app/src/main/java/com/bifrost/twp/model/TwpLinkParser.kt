package com.bifrost.twp.model

import android.net.Uri

/**
 * Parser and generator for the twp:// link standard (TWP: MTProto-over-WebSocket).
 *
 * Mandatory field:
 *  - Worker Host (e.g. "my-worker.workers.dev")
 *
 * Optional fields:
 *  - clean_ip: Cloudflare Clean IP / CDN IP (e.g. "104.16.132.229")
 *  - secret: Authentication token matching Cloudflare Worker SECRET env
 *  - port: Worker port (default: 443)
 *  - name: Label/alias for the proxy configuration (fragment #Name or query ?name=Name)
 */
object TwpLinkParser {

    private const val SCHEME_TWP = "twp"
    private const val SCHEME_TG = "tg"

    /**
     * Generates a standard twp:// link containing the worker host, clean IP, secret, port, and name.
     */
    fun generateLink(config: ProxyConfig): String {
        val host = config.workerHost.trim()
        val builder = StringBuilder("twp://").append(host)

        if (config.port != 443) {
            builder.append(":").append(config.port)
        }

        val queryParts = mutableListOf<String>()

        // Clean IP is explicitly included as first-class query parameter
        if (!config.cleanIp.isNullOrBlank()) {
            queryParts.add("clean_ip=${Uri.encode(config.cleanIp.trim())}")
        }

        if (!config.secret.isNullOrBlank()) {
            queryParts.add("secret=${Uri.encode(config.secret.trim())}")
        }

        if (config.port != 443) {
            queryParts.add("port=${config.port}")
        }

        if (queryParts.isNotEmpty()) {
            builder.append("?").append(queryParts.joinToString("&"))
        }

        if (config.name.isNotBlank()) {
            builder.append("#").append(Uri.encode(config.name.trim()))
        }

        return builder.toString()
    }

    /**
     * Parses a link string into a [ProxyConfig].
     * Supports both twp:// and tg://worker?... formats.
     *
     * @return [ProxyConfig] if parsing succeeded, or null if invalid.
     */
    fun parseLink(rawInput: String?): ProxyConfig? {
        if (rawInput.isNullOrBlank()) return null
        val trimmed = rawInput.trim()

        try {
            // Case 1: Interoperability with tg://worker?...
            if (trimmed.startsWith("tg://worker", ignoreCase = true)) {
                return parseTgWorkerLink(trimmed)
            }

            // Case 2: Standard twp:// link
            if (trimmed.startsWith("twp://", ignoreCase = true)) {
                return parseTwpLink(trimmed)
            }

            // Case 3: Raw URL or domain fallback
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val uri = Uri.parse(trimmed)
                val host = uri.host ?: return null
                return ProxyConfig(
                    name = uri.fragment?.takeIf { it.isNotBlank() } ?: host,
                    workerHost = host,
                    cleanIp = uri.getQueryParameter("clean_ip")
                        ?: uri.getQueryParameter("cleanip")
                        ?: uri.getQueryParameter("ip"),
                    secret = uri.getQueryParameter("secret"),
                    port = if (uri.port > 0) uri.port else 443
                )
            }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseTwpLink(link: String): ProxyConfig? {
        // Replace scheme with https temporarily to use robust Android Uri parser
        val normalized = "https://" + link.substring(6)
        val uri = Uri.parse(normalized)

        val authority = uri.authority ?: return null
        val hostParts = authority.split(":")
        val workerHost = hostParts[0].trim()
        if (workerHost.isBlank()) return null

        val portFromAuth = if (hostParts.size > 1) hostParts[1].toIntOrNull() else null
        val portFromQuery = uri.getQueryParameter("port")?.toIntOrNull()
        val port = portFromAuth ?: portFromQuery ?: 443

        // Extract Clean IP from various common aliases
        val cleanIp = uri.getQueryParameter("clean_ip")
            ?: uri.getQueryParameter("cleanip")
            ?: uri.getQueryParameter("clean-ip")
            ?: uri.getQueryParameter("ip")
            ?: uri.getQueryParameter("cdn_ip")

        val secret = uri.getQueryParameter("secret")
            ?: uri.getQueryParameter("token")

        val nameFromQuery = uri.getQueryParameter("name")
        val nameFromFragment = uri.fragment?.takeIf { it.isNotBlank() }
        val name = nameFromFragment ?: nameFromQuery ?: workerHost

        return ProxyConfig(
            name = name,
            workerHost = workerHost,
            cleanIp = cleanIp?.trim()?.takeIf { it.isNotBlank() },
            secret = secret?.trim()?.takeIf { it.isNotBlank() },
            port = port
        )
    }

    private fun parseTgWorkerLink(link: String): ProxyConfig? {
        val uri = Uri.parse(link)
        val server = uri.getQueryParameter("server")
            ?: uri.getQueryParameter("host")
            ?: return null

        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 443
        val secret = uri.getQueryParameter("secret")
        val cleanIp = uri.getQueryParameter("clean_ip") ?: uri.getQueryParameter("ip")
        val name = uri.fragment?.takeIf { it.isNotBlank() } ?: server

        return ProxyConfig(
            name = name,
            workerHost = server.trim(),
            cleanIp = cleanIp?.trim()?.takeIf { it.isNotBlank() },
            secret = secret?.trim()?.takeIf { it.isNotBlank() },
            port = port
        )
    }
}
