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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.forgerock.backstage.connect.client.BackstageConnectUsageService.JWT_KEY_CLAIM;
import static org.forgerock.backstage.connect.client.BackstageConnectUsageService.JWT_URL_CLAIM;
import static org.forgerock.backstage.connect.client.BackstageConnectUsageService.JWT_VERSION_HEADER;
import static org.forgerock.backstage.connect.client.BackstageConnectUsageService.SERVICE_URL;

import java.util.Date;

import org.forgerock.json.jose.builders.JwtBuilderFactory;
import org.forgerock.json.jose.jwt.JwtClaimsSet;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BackstageConnectUsageServiceTest {

    private static final String PRODUCT = "Product Name";
    private static final String VERSION = "Product Version";
    private static final String KEY = "9d1142cf-c6f4-445e-83ef-090bcf54d352";
    private static final String JWT_VERSION = "1.0";

    private static final String VALID_JWT;
    private static final String VALID_JWT_URL_OVERRIDE;
    private static final String INVALID_JWT_URL_OVERRIDE_INVALID;
    private static final String INVALID_JWT_MISSING_VERSION_HEADER;
    private static final String INVALID_JWT_INCOMPATIBLE_VERSION_HEADER;
    private static final String INVALID_JWT_MISSING_KEY_CLAIM;

    static {
        final JwtClaimsSet claims = new JwtClaimsSet();
        claims.setClaim(JWT_KEY_CLAIM, KEY);
        claims.setIssuedAtTime(new Date());

        final JwtClaimsSet claimsWithUrl = new JwtClaimsSet();
        claimsWithUrl.setClaim(JWT_KEY_CLAIM, KEY);
        claimsWithUrl.setClaim(JWT_URL_CLAIM, SERVICE_URL);
        claimsWithUrl.setIssuedAtTime(new Date());

        final JwtClaimsSet claimsWithInvalidUrl = new JwtClaimsSet();
        claimsWithInvalidUrl.setClaim(JWT_KEY_CLAIM, KEY);
        claimsWithInvalidUrl.setClaim(JWT_URL_CLAIM, "https://www.example.com/hello");
        claimsWithInvalidUrl.setIssuedAtTime(new Date());

        final JwtClaimsSet claimsWithNoIssuedAtTime = new JwtClaimsSet();
        claims.setClaim(JWT_KEY_CLAIM, KEY);

        VALID_JWT = new JwtBuilderFactory()
                .jwt()
                .headers().header(JWT_VERSION_HEADER, JWT_VERSION).done()
                .claims(claims).build();

        VALID_JWT_URL_OVERRIDE = new JwtBuilderFactory()
                .jwt()
                .headers().header(JWT_VERSION_HEADER, JWT_VERSION).done()
                .claims(claimsWithUrl).build();

        INVALID_JWT_URL_OVERRIDE_INVALID = new JwtBuilderFactory()
                .jwt()
                .headers().header(JWT_VERSION_HEADER, JWT_VERSION).done()
                .claims(claimsWithInvalidUrl).build();

        INVALID_JWT_MISSING_VERSION_HEADER = new JwtBuilderFactory()
                .jwt()
                .headers().done()
                .claims(claims).build();

        INVALID_JWT_INCOMPATIBLE_VERSION_HEADER = new JwtBuilderFactory()
                .jwt()
                .headers().header(JWT_VERSION_HEADER, "0.1").done()
                .claims(claims).build();

        INVALID_JWT_MISSING_KEY_CLAIM = new JwtBuilderFactory()
                .jwt()
                .headers().header(JWT_VERSION_HEADER, JWT_VERSION).done()
                .build();
    }

    @Test
    public void testValidJwt() throws FailedVerificationException {
        new BackstageConnectUsageService(PRODUCT, VERSION, VALID_JWT);
    }

    @Test
    public void testValidJwtWithUrlOverride() throws FailedVerificationException {
        new BackstageConnectUsageService(PRODUCT, VERSION, VALID_JWT_URL_OVERRIDE);
    }

    @DataProvider
    public Object[][] constructorErrorConditionsData() {
        return new Object[][]{
                {null, null, null, NullPointerException.class},
                {PRODUCT, null, null, NullPointerException.class},
                {PRODUCT, VERSION, null, FailedVerificationException.class},
                {PRODUCT, VERSION, INVALID_JWT_MISSING_KEY_CLAIM, FailedVerificationException.class},
                {PRODUCT, VERSION, INVALID_JWT_URL_OVERRIDE_INVALID, FailedVerificationException.class},
                {PRODUCT, VERSION, INVALID_JWT_MISSING_VERSION_HEADER, FailedVerificationException.class},
                {PRODUCT, VERSION, INVALID_JWT_INCOMPATIBLE_VERSION_HEADER, FailedVerificationException.class},
        };
    }

    @Test(dataProvider = "constructorErrorConditionsData")
    public void testConstructorErrorConditions(final String productName, final String productVersion,
            final String backstageConnectJwt, final Class<? extends Throwable> expectedException)
            throws Exception {
        try {
            new BackstageConnectUsageService(productName, productVersion, backstageConnectJwt);
            failBecauseExceptionWasNotThrown(expectedException);
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(expectedException);
        }
    }

}
