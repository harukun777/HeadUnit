package com.andrerinas.headunitrevived.update

import java.net.URI

internal object UpdatePolicy {
    fun requireHttps(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "HTTPS URL required"
        }
    }

    fun eligible(version: Long, installed: Long, minSdk: Int, sdk: Int, variant: String, currentVariant: String): Boolean =
        version > installed && minSdk <= sdk && variant == currentVariant

    fun matchingSigners(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && installed == candidate
}
