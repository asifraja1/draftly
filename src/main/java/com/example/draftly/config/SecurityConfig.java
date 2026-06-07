package com.example.draftly.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Autowired
    private OAuth2AuthorizationRequestResolver authorizationRequestResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/error",
                                "/gmail/webhook",
                                "/api/relations/**",
                                "/api/summaries/**",
                                "/api/drafts/**",
                                "/api/user",
                                "/api/events"
                        )
                        .permitAll()
                        // /api/emails/** requires login (needs OAuth2 token to call Gmail)
                        .anyRequest()
                        .authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(authEndpoint ->
                                authEndpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .defaultSuccessUrl("/auth/success", true)
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("http://localhost:3000/login")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
