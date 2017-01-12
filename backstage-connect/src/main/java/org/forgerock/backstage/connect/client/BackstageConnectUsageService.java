/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017 ForgeRock AS.
 */

package org.forgerock.backstage.connect.client;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.forgerock.http.apache.async.AsyncHttpClientProvider.OPTION_WORKER_THREADS;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.Utils.isNullOrEmpty;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_LOADER;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.util.Utils.newThreadFactory;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.apache.async.AsyncHttpClientProvider;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.Version;
import org.forgerock.http.spi.Loader;
import org.forgerock.json.JsonValue;
import org.forgerock.json.jose.builders.JwtBuilderFactory;
import org.forgerock.json.jose.jwt.Jwt;
import org.forgerock.json.jose.jwt.JwtClaimsSet;
import org.forgerock.json.jose.jwt.JwtHeader;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that initiates a <em>ping</em> to ForgeRock Backstage. The following information is transmitted:
 * <ul>
 * <li>Product Name</li>
 * <li>Product Version</li>
 * <li>Unique Customer-Issued Key</li>
 * </ul>
 */
public class BackstageConnectUsageService {

    private static final Logger logger = LoggerFactory.getLogger(BackstageConnectUsageService.class);

    // settings for retrying failed communication with backstage service
    private static final int MAX_RETRY_COUNT = 2;
    private static final long RETRY_DELAY = 1;
    private static final TimeUnit RETRY_UNIT = TimeUnit.HOURS;

    /**
     * {@link #JWT_VERSION_HEADER} must be compatible with version {@code 1.x}.
     */
    private static final Version COMPATIBLE_JWT_VERSION = version(1);

    /**
     * {@link #JWT_URL_CLAIM}, if provided in JWT, must use the {@code forgerock.com} domain or a sub-domain.
     */
    private static final Pattern FORGEROCK_URL_PATTERN = Pattern.compile("^https://(?:[^.]+\\.)?forgerock\\.com/.*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Name of JWT header, containing the version of the JWT structure. This is a custom header, and not part of the
     * JWT related specifications.
     */
    static final String JWT_VERSION_HEADER = "v";

    /**
     * Name of JWT claim, containing a unique customer-issued key.
     */
    static final String JWT_KEY_CLAIM = "key";

    /**
     * Name of JWT claim, containing an optional Service-URL override.
     */
    static final String JWT_URL_CLAIM = "url";

    /**
     * Backstage Connect Service URL to POST to in a production environment.
     *
     * @see #JWT_URL_CLAIM The URL can be optionally overridden within the JWT.
     */
    static final String SERVICE_URL = "https://backstage.forgerock.com/analytics/api/v1/product/ping";

    /**
     * Name of the {@code BACKSTAGE_CONNECT_JWT} environment variable, which is an alternative to using
     * {@link #CONFIG_RESOURCE_PATH} on the classpath.
     */
    public static final String CONFIG_ENV_VAR = "BACKSTAGE_CONNECT_JWT";

    /**
     * Classpath of the {@code backstage-connect.jwt} resource.
     */
    public static final String CONFIG_RESOURCE_PATH = "/backstage-connect.jwt";

    /**
     * Symbolic-name of the OSGi bundle containing the {@code backstage-connect.jwt} resource.
     */
    public static final String CONFIG_OSGI_BUNDLE_SYMBOLIC_NAME = "org.forgerock.backstage.connect.config";

    private final HttpClientHandler httpClientHandler;
    private final SendUsageRunnable sendUsageRunnable;
    private final ScheduledExecutorService scheduler;

    /**
     * Initializes the this service, by reading a customer-assigned key from a given JSON Web Token (JWT).
     *
     * @param productName Product Name (e.g., OpenIG)
     * @param productVersion Product Version (e.g., 4.5.0)
     * @param backstageConnectJwt JSON Web Token (JWT) containing customer-assigned key
     * @throws FailedVerificationException JSON Web Token (JWT) is either missing, invalid, or failed verification
     */
    public BackstageConnectUsageService(final String productName, final String productVersion,
            final String backstageConnectJwt) throws FailedVerificationException {
        Reject.ifNull(productName);
        Reject.ifNull(productVersion);
        if (isNullOrEmpty(backstageConnectJwt)) {
            throw new FailedVerificationException("JWT is required");
        }
        final Jwt jwt = getJwt(backstageConnectJwt);
        final Version version = getVersion(jwt.getHeader());
        final JwtClaimsSet claimsSet = jwt.getClaimsSet();
        final String key = getKey(claimsSet, version);
        final String serviceUrl = getServiceUrl(claimsSet, version);
        getIssuedAtTime(claimsSet, version);

        final JsonValue payload = buildPayload(key, productName, productVersion);
        httpClientHandler = buildHttpClientHandler();
        scheduler = newScheduledThreadPool(1,
                newThreadFactory(null, getClass().getSimpleName() + "-%d", false));
        sendUsageRunnable = new SendUsageRunnable(serviceUrl, payload, new Client(httpClientHandler), scheduler);
    }

    private static Jwt getJwt(final String jwt) throws FailedVerificationException {
        try {
            return new JwtBuilderFactory().reconstruct(jwt.trim(), Jwt.class);
        } catch (final Exception e) {
            throw new FailedVerificationException("JWT invalid", e);
        }
    }

    private static Version getVersion(final JwtHeader header) throws FailedVerificationException {
        final String jwtVersion = header.get(JWT_VERSION_HEADER).asString();
        if (isNullOrEmpty(jwtVersion)) {
            throw new FailedVerificationException("JWT version header is missing");
        }
        final Version version = version(jwtVersion);
        if (!COMPATIBLE_JWT_VERSION.isCompatibleWith(version)) {
            throw new FailedVerificationException("JWT version not compatible: " + version);
        }
        return version;
    }

    private static String getKey(final JwtClaimsSet claimsSet, final Version version)
            throws FailedVerificationException {
        final String key = claimsSet.get(JWT_KEY_CLAIM).asString();
        if (isNullOrEmpty(key)) {
            throw new FailedVerificationException("JWT missing key claim, with version: " + version);
        }
        return key;
    }

    private static String getServiceUrl(final JwtClaimsSet claimsSet, final Version version)
            throws FailedVerificationException {
        final String overrideUrl = claimsSet.get(JWT_URL_CLAIM).asString();
        if (isNullOrEmpty(overrideUrl)) {
            return SERVICE_URL;
        }
        if (!FORGEROCK_URL_PATTERN.matcher(overrideUrl).matches()) {
            throw new FailedVerificationException("JWT url claim is invalid '" + overrideUrl + "' with version: "
                    + version);
        }
        return overrideUrl;
    }

    private static Date getIssuedAtTime(final JwtClaimsSet claimsSet, final Version version)
            throws FailedVerificationException {
        final Date jwtIssuedAtTime = claimsSet.getIssuedAtTime();
        if (jwtIssuedAtTime == null) {
            throw new FailedVerificationException("JWT missing issued-at-time claim, with version: " + version);
        }
        return jwtIssuedAtTime;
    }

    /**
     * Starts the service.
     */
    public void startup() {
        try {
            if (scheduler != null && sendUsageRunnable != null) {
                // execute worker-thread immediately
                scheduler.schedule(sendUsageRunnable, 0, TimeUnit.MILLISECONDS);
            }
        } catch (final Exception e) {
            logger.debug("Unexpected failure during startup", e);
        }
    }

    /**
     * Cleans up service resources on shutdown.
     */
    public void shutdown() {
        try {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (httpClientHandler != null) {
                httpClientHandler.close();
            }
        } catch (final Exception e) {
            logger.debug("Unexpected failure during shutdown", e);
        }
    }

    /**
     * Builds JSON payload.
     *
     * @param key Unique customer-issued key
     * @param productName Product name
     * @param productVersion Product version
     * @return JSON payload
     */
    private static JsonValue buildPayload(final String key, final String productName, final String productVersion) {
        return json(object(
                field("key", key),
                field("product", object(
                        field("name", productName),
                        field("version", productVersion)
                        )
                )));
    }

    /**
     * Builds an async HTTP-client handler.
     *
     * @return HTTP-client handler, which must be closed on shutdown
     */
    private static HttpClientHandler buildHttpClientHandler() {
        try {
            return new HttpClientHandler(
                    Options.defaultOptions()
                            .set(OPTION_WORKER_THREADS, 1)
                            .set(OPTION_LOADER, new Loader() {
                                @Override
                                public <S> S load(final Class<S> service, final Options options) {
                                    return service.cast(new AsyncHttpClientProvider());
                                }
                            }));
        } catch (final HttpApplicationException e) {
            throw new RuntimeException("Error while building default HTTP Client", e);
        }
    }

    /**
     * {@link Runnable} that {@code POST}s a JSON payload to the service URL, and will retry up to
     * {@link #MAX_RETRY_COUNT} times, using {@link #RETRY_DELAY} and {@link #RETRY_UNIT} settings.
     */
    private static final class SendUsageRunnable implements Runnable {

        private final String serviceUrl;
        private final JsonValue payload;
        private final Client client;
        private final ScheduledExecutorService scheduler;
        private int retryCount;

        /**
         * Initializes the service worker.
         *
         * @param serviceUrl Service URL
         * @param payload JSON payload
         * @param client HTTP client
         * @param scheduler Thread scheduler
         */
        SendUsageRunnable(final String serviceUrl, final JsonValue payload, final Client client,
                final ScheduledExecutorService scheduler) {
            this.serviceUrl = checkNotNull(serviceUrl);
            this.payload = checkNotNull(payload);
            this.client = checkNotNull(client);
            this.scheduler = checkNotNull(scheduler);
        }

        /**
         * Communicates via HTTP with the Backstage-Connect Usage Service.
         */
        @Override
        public void run() {
            try {
                final Request request = new Request();
                request.setMethod("POST");
                request.setUri(serviceUrl);
                request.setEntity(payload);

                client.send(request).thenOnResult(new ResultHandler<Response>() {
                    @Override
                    public void handleResult(final Response result) {
                        final boolean success = result.getStatus().isSuccessful();
                        if (!success && !scheduler.isShutdown()) {
                            // failed, so reschedule worker up to maximum number of retries
                            if (++retryCount < MAX_RETRY_COUNT) {
                                scheduler.schedule(SendUsageRunnable.this, RETRY_DELAY, RETRY_UNIT);
                            }
                            if (result.getCause() != null) {
                                logger.debug("Backstage communication failure", result.getCause());
                            }
                        } else {
                            // success
                            retryCount = 0;
                        }
                    }
                });
            } catch (final Exception e) {
                logger.debug("Service worker failure", e);
            }
        }
    }

}
