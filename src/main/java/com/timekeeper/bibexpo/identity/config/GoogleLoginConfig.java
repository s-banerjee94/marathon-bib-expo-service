package com.timekeeper.bibexpo.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Configuration
public class GoogleLoginConfig {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration JWK_FETCH_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Verifies Google ID tokens: signed by Google (keys fetched and cached from its JWK set), issued by
     * Google, not expired, and issued for this app's client ID.
     */
    @Bean
    public JwtDecoder googleIdTokenDecoder(@Value("${google.client-id:}") String clientId) {
        // Nimbus defaults to 500 ms, shorter than a cold TLS handshake to Google on a slow link.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(JWK_FETCH_TIMEOUT);
        requestFactory.setReadTimeout(JWK_FETCH_TIMEOUT);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
                new JwtClaimValidator<>(JwtClaimNames.ISS, iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> !clientId.isBlank() && aud != null && aud.contains(clientId)))));
        return decoder;
    }
}
