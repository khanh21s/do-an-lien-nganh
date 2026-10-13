package com.parroto.auth.service;

import com.parroto.auth.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;
import java.util.Map;

/**
 * Verifies an Apple identity token against Apple's published JWKS.
 * https://developer.apple.com/documentation/sign_in_with_apple/verifying_a_user
 */
@Service
public class AppleTokenVerifier {

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final RestTemplate restTemplate;
    private final String expectedClientId;

    public AppleTokenVerifier(RestTemplate restTemplate, @Value("${oauth.apple.client-id}") String expectedClientId) {
        this.restTemplate = restTemplate;
        this.expectedClientId = expectedClientId;
    }

    public record VerifiedAppleUser(String email, String subject) {
    }

    public VerifiedAppleUser verify(String identityToken) {
        // AUTH-04: never accept a token without checking its audience; no client id = feature off.
        if (expectedClientId == null || expectedClientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Apple login chưa cấu hình");
        }
        String kid = extractHeaderKid(identityToken);
        RSAPublicKey publicKey = fetchPublicKey(kid);
        try {
            Claims claims = Jwts.parser().verifyWith(publicKey).build()
                    .parseSignedClaims(identityToken)
                    .getPayload();
            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw ApiException.unauthorized("Invalid Apple token issuer");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(expectedClientId)) {
                throw ApiException.unauthorized("Apple token was not issued for this application");
            }
            String email = claims.get("email", String.class);
            return new VerifiedAppleUser(email, claims.getSubject());
        } catch (JwtException e) {
            throw ApiException.unauthorized("Invalid Apple token");
        }
    }

    private String extractHeaderKid(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw ApiException.unauthorized("Malformed Apple token");
        }
        String headerJson = new String(Decoders.BASE64URL.decode(parts[0]));
        // Minimal extraction, avoids pulling in a JSON parser dependency just for this.
        int idx = headerJson.indexOf("\"kid\":\"");
        if (idx < 0) {
            throw ApiException.unauthorized("Apple token missing key id");
        }
        int start = idx + 7;
        int end = headerJson.indexOf('"', start);
        return headerJson.substring(start, end);
    }

    @SuppressWarnings("unchecked")
    private RSAPublicKey fetchPublicKey(String kid) {
        Map<String, Object> response = restTemplate.getForObject(APPLE_KEYS_URL, Map.class);
        if (response == null || !(response.get("keys") instanceof List<?> keys)) {
            throw ApiException.unauthorized("Unable to fetch Apple signing keys");
        }
        for (Object keyObj : keys) {
            Map<String, Object> jwk = (Map<String, Object>) keyObj;
            if (kid.equals(jwk.get("kid"))) {
                byte[] nBytes = Decoders.BASE64URL.decode((String) jwk.get("n"));
                byte[] eBytes = Decoders.BASE64URL.decode((String) jwk.get("e"));
                BigInteger modulus = new BigInteger(1, nBytes);
                BigInteger exponent = new BigInteger(1, eBytes);
                try {
                    KeyFactory factory = KeyFactory.getInstance("RSA");
                    return (RSAPublicKey) factory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    throw new IllegalStateException("Unable to build Apple RSA public key", e);
                }
            }
        }
        throw ApiException.unauthorized("No matching Apple signing key found");
    }
}
