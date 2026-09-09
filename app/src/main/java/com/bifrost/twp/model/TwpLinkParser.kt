package com.bifrost.twp.model

import android.net.Uri

/**
 * Parser and generator for the TWP / Bifrost link standards.
 *
 * Supported link formats:
 *  1. Standard Universal TWP: twp://proxy?server=<workerHost>&clean_ip=<cleanIp>&secret=<secret>#<name>
 *  2. Direct Authority TWP:  twp://<workerHost>/?clean_ip=<cleanIp>&secret=<secret>#<name>
 *  3. Telegram Clickable TG:  tg://worker?server=<workerHost>&clean_ip=<cleanIp>&secret=<secret>#<name>
 *  4. Telegram Clickable TWP: tg://twp?server=<workerHost>&clean_ip=<cleanIp>&secret=<secret>#<name>
 *  5. Web HTTPS Deep Link:   https://bifrost.app/twp?server=<workerHost>&clean_ip=<cleanIp>#<name>
 */
object TwpLinkParser {

    /**
     * Generates a standard twp:// link containing the worker host, clean IP, secret, port, and name.
     */
    fun generateLink(config: ProxyConfig): String {
        val host = config.workerHost.trim()
        val builder = StringBuilder("twp://proxy?server=").append(Uri.encode(host))

        if (!config.cleanIp.isNullOrBlank()) {
            builder.append("&clean_ip=").append(Uri.encode(config.cleanIp.trim()))
        }

        if (!config.secret.isNullOrBlank()) {
            builder.append("&secret=").append(Uri.encode(config.secret.trim()))
        }

        if (config.port != 443) {
            builder.append("&port=").append(config.port)
        }

        if (config.name.isNotBlank()) {
            builder.append("#").append(Uri.encode(config.name.trim()))
        }

        return builder.toString()
    }

    /**
     * Generates a tg:// link that Telegram natively renders as a blue clickable link in chat.
     */
    fun generateTelegramLink(config: ProxyConfig): String {
        val host = config.workerHost.trim()
        val builder = StringBuilder("tg://worker?server=").append(Uri.encode(host))

        if (!config.cleanIp.isNullOrBlank()) {
            builder.append("&clean_ip=").append(Uri.encode(config.cleanIp.trim()))
        }

        if (!config.secret.isNullOrBlank()) {
            builder.append("&secret=").append(Uri.encode(config.secret.trim()))
        }

        if (config.port != 443) {
            builder.append("&port=").append(config.port)
        }

        if (config.name.isNotBlank()) {
            builder.append("#").append(Uri.encode(config.name.trim()))
        }

        return builder.toString()
    }

    /**
     * Universal parser that accurately extracts configuration from twp://, tg://, and https:// links.
     */
    fun parseLink(rawInput: String?): ProxyConfig? {
        if (rawInput.isNullOrBlank()) return null
        val trimmed = rawInput.trim()

        try {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase() ?: ""

            if (scheme != "twp" && scheme != "tg" && scheme != "https" && scheme != "http") {
                return null
            }

            // 1. Resolve Worker Host
            var workerHost = uri.getQueryParameter("server")
                ?: uri.getQueryParameter("host")
                ?: uri.getQueryParameter("worker")

            // If not found in query, check URI authority/host
            if (workerHost.isNullOrBlank()) {
                val host = uri.host
                if (!host.isNullOrBlank() && host != "proxy" && host != "worker" && host != "twp" && !host.contains("bifrost")) {
                    workerHost = host
                }
            }

            // Fallback for custom URI like twp://hostname?clean_ip=... where host might be in scheme-specific part
            if (workerHost.isNullOrBlank() && scheme == "twp") {
                val ssp = uri.schemeSpecificPart.trimStart('/')
                val qIdx = ssp.indexOf('?')
                val fIdx = ssp.indexOf('#')
                val endIdx = when {
                    qIdx >= 0 && fIdx >= 0 -> minOf(qIdx, fIdx)
                    qIdx >= 0 -> qIdx
                    fIdx >= 0 -> fIdx
                    else -> ssp.length
                }
                val candidate = ssp.substring(0, endIdx).trim()
                if (candidate.isNotBlank() && candidate != "proxy") {
                    workerHost = candidate
                }
            }

            if (workerHost.isNullOrBlank()) return null

            // 2. Resolve Clean IP
            val cleanIp = uri.getQueryParameter("clean_ip")
                ?: uri.getQueryParameter("cleanip")
                ?: uri.getQueryParameter("clean-ip")
                ?: uri.getQueryParameter("ip")
                ?: uri.getQueryParameter("cdn_ip")

            // 3. Resolve Secret
            val secret = uri.getQueryParameter("secret")
                ?: uri.getQueryParameter("token")

            // 4. Resolve Port (Default: 443)
            val port = uri.getQueryParameter("port")?.toIntOrNull()
                ?: if (uri.port > 0) uri.port else 443

            // 5. Resolve Name
            val name = uri.fragment?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("name")
                ?: workerHost

            return ProxyConfig(
                name = name,
                workerHost = workerHost.trim(),
                cleanIp = cleanIp?.trim()?.takeIf { it.isNotBlank() },
                secret = secret?.trim()?.takeIf { it.isNotBlank() },
                port = port
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
