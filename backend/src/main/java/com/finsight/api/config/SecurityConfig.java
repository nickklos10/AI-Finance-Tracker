package com.finsight.api.config;

import com.finsight.api.security.JwtToScopeConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {

    private static final String ISSUER_URI = "https://dev-ugadnr0ui0vziqee.us.auth0.com/";
    private static final String AUDIENCE   = "https://finsight-api";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {

        /* Build a JwtAuthenticationConverter that pulls authorities from the
           space‑delimited “scope” claim. */
        JwtAuthenticationConverter jwtAuthConv = new JwtAuthenticationConverter();
        jwtAuthConv.setJwtGrantedAuthoritiesConverter(JwtToScopeConverter.INSTANCE);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(req -> {
                    CorsConfiguration cfg = new CorsConfiguration();
                    cfg.setAllowedOrigins(List.of("https://app.finsight.com"));
                    cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
                    cfg.setAllowedHeaders(List.of("Authorization","Content-Type"));
                    cfg.setMaxAge(Duration.ofHours(1));
                    return cfg;
                }))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        /* actuator health stays public */
                        .requestMatchers("/actuator/health").permitAll()
                        /* pre‑flight */
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        /* all API + another actuator endpoints need scope */
                        .requestMatchers("/api/**", "/actuator/**").hasAuthority("SCOPE_fin:app")
                        .anyRequest().denyAll())
                .headers(headers -> headers
                        /* Content‑Security‑Policy: scripts & styles only from same origin */
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; " +
                                        "script-src  'self'; " +
                                        "style-src   'self'"))
                        /* Strict‑Transport‑Security: one year, include sub‑domains, preload */
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000))    // 365 days
                        /* Referrer‑Policy: never send referrer header */
                        .referrerPolicy(rp -> rp.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthConv)));

        return http.build();
    }

    /** Validates iss, exp/nbf, aud *and* azp (authorised party) */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder =
                (NimbusJwtDecoder) JwtDecoders.fromOidcIssuerLocation(ISSUER_URI);

        OAuth2TokenValidator<Jwt> aud = new JwtClaimValidator<List<String>>(
                "aud", list -> list.contains(AUDIENCE));

        OAuth2TokenValidator<Jwt> issuer =
                JwtValidators.createDefaultWithIssuer(ISSUER_URI);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, aud));
        return decoder;
    }

}
