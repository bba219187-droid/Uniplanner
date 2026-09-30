package com.uniplanner.app.timetable

import android.content.Context
import android.net.http.SslError
import android.os.Build
import com.uniplanner.app.R
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Some school servers (IPCA's SIGA) do not send the middle certificate of their chain, and Android's
 * WebView does not fetch it. This fetches it from the address the site's own certificate names, then
 * checks the whole chain against the phone's trusted roots plus IPCA's (HARICA). Only a chain that
 * passes the same checks as normal, for the right site name, is accepted.
 */
object PortalCertificates {
    private val trusted = HashSet<String>()

    fun schoolSite(host: String?) = host != null && (host == "ipca.pt" || host.endsWith(".ipca.pt"))

    /** Runs off the main thread. True when the page's certificate is valid once the chain is complete. */
    fun verify(ctx: Context, error: SslError): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val host = runCatching { URL(error.url).host }.getOrNull() ?: return false
        if (!schoolSite(host)) return false
        val leaf = error.certificate.x509Certificate ?: return false
        val key = leaf.encoded.contentHashCode().toString()
        synchronized(trusted) { if (key in trusted) return true }
        if (error.primaryError != SslError.SSL_UNTRUSTED) return false
        return runCatching {
            val chain = mutableListOf(leaf)
            var cert = leaf
            repeat(3) {
                if (cert.subjectX500Principal == cert.issuerX500Principal) return@repeat
                val url = issuerUrl(cert) ?: return@repeat
                val next = download(url) ?: return@repeat
                chain += next
                cert = next
            }
            trustManager(ctx).checkServerTrusted(chain.toTypedArray(), "RSA".takeIf { leaf.publicKey.algorithm == "RSA" } ?: "ECDHE_ECDSA")
            if (!matches(leaf, host)) return false
            synchronized(trusted) { trusted += key }
            true
        }.getOrDefault(false)
    }

    private fun matches(cert: X509Certificate, host: String): Boolean {
        val names = cert.subjectAlternativeNames?.filter { it[0] == 2 }?.map { (it[1] as String).lowercase() } ?: return false
        return names.any { n ->
            n == host || (n.startsWith("*.") && host.endsWith(n.substring(1)) && host.count { it == '.' } == n.count { it == '.' })
        }
    }

    private fun trustManager(ctx: Context): X509TrustManager {
        val cf = CertificateFactory.getInstance("X.509")
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val system = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        system.aliases().toList().forEach { store.setCertificateEntry(it, system.getCertificate(it)) }
        ctx.resources.openRawResource(R.raw.ipca_ca).use { input ->
            cf.generateCertificates(input).forEachIndexed { i, c -> store.setCertificateEntry("ipca$i", c) }
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** The "CA Issuers" address in the certificate's Authority Information Access extension. */
    private fun issuerUrl(cert: X509Certificate): String? {
        val raw = cert.getExtensionValue("1.3.6.1.5.5.7.1.1") ?: return null
        val text = String(raw, Charsets.ISO_8859_1)
        return Regex("https?://[\\x21-\\x7e]+?\\.(crt|cer|der|p7c|pem)").findAll(text).map { it.value }
            .firstOrNull { !it.contains("ocsp", ignoreCase = true) }
    }

    private fun download(url: String): X509Certificate? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        return try {
            val bytes = c.inputStream.use { it.readBytes() }
            CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(bytes))
                .filterIsInstance<X509Certificate>().firstOrNull()
        } finally {
            c.disconnect()
        }
    }
}
