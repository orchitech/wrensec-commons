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
 * Copyright 2013-2017 ForgeRock AS.
 */

package org.forgerock.json.jose.jwk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.json.jose.jws.JwsAlgorithm;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class JWKSetTest {

    private String jsonAsString;
    private JsonValue jwkSetJson;
    private Map<String, JWK> jwksMapByKid = new HashMap<>();

    @BeforeClass
    public void setup() throws NoSuchAlgorithmException {
        jwksMapByKid.clear();

        //Generate some RSA JWKs
        List<JsonValue> listOfKeys = new LinkedList<>();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        for (int i = 0; i < 10; i++) {
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RsaJWK rsaJwk = new RsaJWK((RSAPublicKey) keyPair.getPublic(), KeyUse.SIG, JwsAlgorithm.RS256.name(),
                    "rsaJwk" + i, null, null, null);
            jwksMapByKid.put(rsaJwk.getKeyId(), rsaJwk);
            listOfKeys.add(rsaJwk.toJsonValue());
        }

        KeyPairGenerator ecKeyPairGenerator = KeyPairGenerator.getInstance("EC");
        ecKeyPairGenerator.initialize(256);
        for (int i = 0; i < 10; i++) {
            KeyPair keyPair = ecKeyPairGenerator.generateKeyPair();
            EcJWK ecJwk = new EcJWK((ECPublicKey) keyPair.getPublic(), KeyUse.SIG, "ecJwk" + i);
            jwksMapByKid.put(ecJwk.getKeyId(), ecJwk);
            listOfKeys.add(ecJwk.toJsonValue());
        }

        jwkSetJson = json(object(field("keys", listOfKeys)));
        jsonAsString = jwkSetJson.toString();
    }

    @Test
    public void testJWKSetConstructorFromAString() {
        JWKSet jwkSet = JWKSet.parse(jsonAsString);
        assertCopiedJwksIsEqualToOriginal(jwkSet.getJWKsAsList());
    }

    @Test
    public void testJWKSetConstructorFromAJsonValue() {
        JWKSet jwkSet = JWKSet.parse(jwkSetJson);
        assertCopiedJwksIsEqualToOriginal(jwkSet.getJWKsAsList());
    }

    @Test
    public void testJWKSetConstructorFromJWKList() {
        JWKSet jwkSet = new JWKSet(new ArrayList<>(jwksMapByKid.values()));
        assertCopiedJwksIsEqualToOriginal(jwkSet.getJWKsAsList());
    }

    /** Cannot use equals() on the two lists because JWK does not implement equals() and hashcode(). */
    private void assertCopiedJwksIsEqualToOriginal(List<JWK> jwks) {
        for (JWK jwk : jwks) {
            assertThat(jwk.getKeyId()).isNotNull();
            JWK jwkExpected = jwksMapByKid.get(jwk.getKeyId());
            assertThat(jwkExpected).isNotNull();
            assertThat(jwk.getKeyType()).isEqualTo(jwkExpected.getKeyType());
            assertThat(jwk.getAlgorithm()).isEqualTo(jwkExpected.getAlgorithm());
        }
    }
}
