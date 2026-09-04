package net.njsdomain.alertviewer.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Component
public class SSLContextFactory {

    @Autowired
    Environment env;

    public SSLContext getSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance(env.getProperty("https.protocol")); // SSL OR TLS
        KeyStore ks = KeyStore.getInstance(env.getProperty("keystore.type")); // PKCS12
        FileInputStream fis = new FileInputStream(env.getProperty("client.certificate.p12"));
        ks.load(fis, env.getProperty("client.certificate.keystore.password").toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(env.getProperty("keymanager.algorithm")); // SunX509
        kmf.init(ks, env.getProperty("client.certificate.password").toCharArray());
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{MOCK_TRUST_MANAGER}, new SecureRandom());
        return sslContext;
    }

    private static final TrustManager MOCK_TRUST_MANAGER = new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
            // empty method
        }
    };
}
