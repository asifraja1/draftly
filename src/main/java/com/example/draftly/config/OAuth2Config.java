package com.example.draftly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Customizes the Google authorization request to add access_type=offline and
 * prompt=consent so Google always returns a refresh token (needed to refresh
 * the Gmail access token after it expires).
 *
 * This is done via a resolver rather than overriding the authorization-uri in
 * properties, which would corrupt the redirect_uri and cause redirect_uri_mismatch.
 */
@Configuration
public class OAuth2Config {

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository repo) {

        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");

        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.additionalParameters(params -> {
                    params.put("access_type", "offline");
                    params.put("prompt", "consent");
                })
        );

        return resolver;
    }
}
