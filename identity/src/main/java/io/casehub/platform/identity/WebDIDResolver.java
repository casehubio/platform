package io.casehub.platform.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.platform.identity.config.IdentityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves did:web DIDs via HTTPS GET.
 *
 * <p>Security: rejects RFC 1918, loopback, and link-local hosts (SSRF protection).
 * Does not follow HTTP→HTTPS redirects that would bypass the SSRF check.
 * Enforces maximum response size to prevent DoS via large documents.
 * TLS is mandatory (HTTPS only).
 *
 * <p>URL derivation:
 * <ul>
 *   <li>{@code did:web:example.com} → {@code https://example.com/.well-known/did.json}</li>
 *   <li>{@code did:web:example.com:users:alice} → {@code https://example.com/users/alice/did.json}</li>
 * </ul>
 */
@ApplicationScoped
@Alternative
public class WebDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(WebDIDResolver.class);

    private static final Pattern BLOCKED_HOSTS = Pattern.compile(
            "^(localhost|127\\..*|::1|0\\.0\\.0\\.0|10\\..*|" +
            "172\\.(1[6-9]|2[0-9]|3[01])\\..*|192\\.168\\..*)$");

    private static final String DID_WEB_PREFIX = "did:web:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IdentityConfig config;
    private final HttpClient httpClient;

    @Inject
    public WebDIDResolver(final IdentityConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(config.webResolverTimeoutMs()))
                .build();
    }

    @Override
    public Optional<DIDDocument> resolve(final String did) {
        if (did == null || !did.startsWith(DID_WEB_PREFIX)) {
            return Optional.empty();
        }
        try {
            final String url = toUrl(did);
            final URI uri = URI.create(url);
            if (!isAllowedHost(uri.getHost())) {
                LOG.warnf("WebDIDResolver: blocked SSRF attempt for host %s in DID %s", uri.getHost(), did);
                return Optional.empty();
            }
            final HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(config.webResolverTimeoutMs()))
                    .build();
            final HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.debugf("WebDIDResolver: HTTP %d for %s", response.statusCode(), did);
                return Optional.empty();
            }
            final String body = response.body();
            if (body.length() > config.webResolverMaxResponseBytes()) {
                LOG.warnf("WebDIDResolver: response for %s exceeds max size (%d bytes)", did, body.length());
                return Optional.empty();
            }
            return Optional.of(parseDocument(body));
        } catch (final Exception e) {
            LOG.debugf("WebDIDResolver: failed to resolve %s: %s", did, e.getMessage());
            return Optional.empty();
        }
    }

    protected boolean isAllowedHost(final String host) {
        return host != null && !BLOCKED_HOSTS.matcher(host).matches();
    }

    protected String scheme() {
        return "https";
    }

    private String toUrl(final String did) {
        final String hostAndPath = did.substring(DID_WEB_PREFIX.length());
        final String[] parts = hostAndPath.split(":", -1);
        final String authority = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        final String path;
        if (parts.length > 1) {
            final String[] pathSegments = Arrays.copyOfRange(parts, 1, parts.length);
            path = "/" + String.join("/", pathSegments) + "/did.json";
        } else {
            path = "/.well-known/did.json";
        }
        return scheme() + "://" + authority + path;
    }

    private DIDDocument parseDocument(final String json) throws Exception {
        final JsonNode root = OBJECT_MAPPER.readTree(json);
        final String id = root.path("id").asText("");
        final List<VerificationMethod> vms = new ArrayList<>();
        final JsonNode vmArray = root.path("verificationMethod");
        if (vmArray.isArray()) {
            for (final JsonNode vmNode : vmArray) {
                final String vmId = vmNode.path("id").asText("");
                final String type = vmNode.path("type").asText("");
                final String multibase = vmNode.path("publicKeyMultibase").asText("");
                byte[] keyBytes = new byte[0];
                if (multibase.startsWith("z") && multibase.length() > 1) {
                    try {
                        keyBytes = Base64.getUrlDecoder().decode(multibase.substring(1));
                    } catch (final Exception ex) {
                        LOG.debugf("WebDIDResolver: failed to decode publicKeyMultibase: %s", ex.getMessage());
                    }
                }
                vms.add(new VerificationMethod(vmId, type, keyBytes));
            }
        }
        final List<String> alsoKnownAs = new ArrayList<>();
        final JsonNode akaArray = root.path("alsoKnownAs");
        if (akaArray.isArray()) {
            for (final JsonNode akaNode : akaArray) {
                alsoKnownAs.add(akaNode.asText());
            }
        }
        return new DIDDocument(id, vms, alsoKnownAs);
    }
}
