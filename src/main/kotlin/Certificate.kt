package cn.pantheon

import cn.pantheon.CertificateHolder.getCertificate
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*


object CertificateGenerator {

    private val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA", "BC").also { it.initialize(2048, SecureRandom()) }.generateKeyPair()
    }

    private val rootCA: X509Certificate

    private val rootCAPrivateKey: PrivateKey


    init {
        Security.addProvider(BouncyCastleProvider())

        val key =
            Files.readAllBytes(Paths.get("C:\\Users\\Administrator\\Documents\\GitHub\\hostPlus\\src\\main\\resources\\rootCA.der"))
        rootCAPrivateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(key))

        rootCA =
            CertificateFactory.getInstance("X.509")
                .generateCertificate(FileInputStream("C:\\Users\\Administrator\\Documents\\GitHub\\hostPlus\\src\\main\\resources\\rootCA.crt")) as X509Certificate

    }


    fun createProxyCertificate(host: String): X509Certificate {
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            rootCA, Random().nextInt().toBigInteger(), rootCA.notBefore,
            rootCA.notAfter,
            X500Name("CN=IntermedCA"), keyPair.public
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign))
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(true))
        return JcaX509CertificateConverter().getCertificate(
            builder
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(rootCAPrivateKey))
        )
    }
}

object CertificateHolder {

    private val certificates = mutableMapOf<String, X509Certificate>()


    fun getCertificate(host:String): X509Certificate =
        if (certificates.containsKey(host)) certificates[host]!! else {
        with(CertificateGenerator.createProxyCertificate(host)){
            certificates[host] = this
            this
        }
    }


}


fun main() {
    val certificate = getCertificate("www.baidu.com")

    println(certificate)
}