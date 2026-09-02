package io.casehub.platform.signing.document;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;

@ApplicationScoped
public class TrustedListManager {

    private static final Logger LOG = Logger.getLogger(TrustedListManager.class);
    private static final String EU_LOTL_URL = "https://ec.europa.eu/tools/lotl/eu-lotl.xml";

    private final TrustedListsCertificateSource trustedListSource = new TrustedListsCertificateSource();
    private final boolean enabled;
    private final String lotlUrl;

    @Inject
    TrustedListManager(DssSigningConfig config) {
        this(config.trustedListUrl().orElse(null));
    }

    public TrustedListManager(String lotlUrl) {
        this.lotlUrl = lotlUrl;
        this.enabled = lotlUrl != null && !lotlUrl.isBlank();
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            LOG.info("Trusted List validation disabled — no LOTL URL configured");
            return;
        }
        try {
            refresh();
        } catch (Exception e) {
            LOG.warnf("Initial Trusted List load failed — verification will run without TL: %s", e.getMessage());
        }
    }

    public void refresh() {
        if (!enabled) return;

        LOG.infof("Loading EU Trusted List from %s", lotlUrl);
        var job = new TLValidationJob();

        var lotlSource = new LOTLSource();
        lotlSource.setUrl(lotlUrl);

        var onlineLoader = new CommonsDataLoader();
        var cacheLoader = new FileCacheDataLoader(onlineLoader);
        cacheLoader.setCacheExpirationTime(1000L * 60 * 60 * 24);
        cacheLoader.setFileCacheDirectory(new File(System.getProperty("java.io.tmpdir"), "dss-tsl-cache"));

        job.setListOfTrustedListSources(lotlSource);
        job.setOnlineDataLoader(cacheLoader);
        job.setOfflineDataLoader(cacheLoader);
        job.setTrustedListCertificateSource(trustedListSource);
        job.onlineRefresh();

        int count = trustedListSource.getCertificates().size();
        LOG.infof("Trusted List loaded — %d trusted certificates", count);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TrustedListsCertificateSource getTrustedListSource() {
        return trustedListSource;
    }
}
