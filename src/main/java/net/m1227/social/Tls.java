package net.m1227.social;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS for the 1227 Link online relay. Minecraft 1.8.9 launchers often run an old Java 8 (8u51 from
 * 2015) whose certificate list lacks today's common roots (Let's Encrypt ISRG, Google Trust
 * Services, ...), so HTTPS to a current host fails with "PKIX path building failed". This trusts
 * Java's own list plus a few public root certificates shipped in the jar
 * (assets/m1227/certs/extra-roots.pem, from the Mozilla CA list). Certificates are still fully
 * checked: chain, signatures, dates and the host name.
 */
public final class Tls {
    private Tls() {}

    private static SSLSocketFactory factory;

    public static synchronized SSLSocketFactory socketFactory() throws Exception {
        if (factory != null) return factory;
        TrustManagerFactory def = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        def.init((KeyStore) null);
        final X509TrustManager system = first(def.getTrustManagers());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        int n = 0;
        try (InputStream in = Tls.class.getResourceAsStream("/assets/m1227/certs/extra-roots.pem")) {
            if (in != null) {
                Collection<? extends java.security.cert.Certificate> certs = CertificateFactory.getInstance("X.509").generateCertificates(in);
                for (java.security.cert.Certificate c : certs) ks.setCertificateEntry("extra" + n++, c);
            }
        }
        TrustManagerFactory ex = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        ex.init(ks);
        final X509TrustManager extra = first(ex.getTrustManagers());
        X509TrustManager both = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                system.checkClientTrusted(chain, auth);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                try {
                    system.checkServerTrusted(chain, auth);
                } catch (CertificateException e) {
                    extra.checkServerTrusted(chain, auth); // throws if neither list trusts it
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                List<X509Certificate> l = new ArrayList<X509Certificate>();
                for (X509Certificate c : system.getAcceptedIssuers()) l.add(c);
                for (X509Certificate c : extra.getAcceptedIssuers()) l.add(c);
                return l.toArray(new X509Certificate[0]);
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{both}, null);
        // the host name is checked by the HTTPS endpoint identification of each socket (see WsClient)
        factory = ctx.getSocketFactory();
        return factory;
    }

    private static X509TrustManager first(TrustManager[] tms) {
        for (TrustManager t : tms) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        throw new IllegalStateException("no X509TrustManager");
    }
}
