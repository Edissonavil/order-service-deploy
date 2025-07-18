// src/main/java/com/aec/ordsrv/config/SecurityConfig.java
package com.aec.ordsrv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}") private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthConverter) throws Exception {

        http
          .csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth

              // 1) CART sólo para CLIENTE (y *antes* de la regla genérica)
              .requestMatchers(HttpMethod.GET , "/api/orders/cart/**").hasAuthority("ROL_CLIENTE")
              .requestMatchers(HttpMethod.POST, "/api/orders/cart/**").hasAuthority("ROL_CLIENTE")

              // 2) Estadísticas: colaborador/admin
              .requestMatchers("/api/orders/stats/creator/**").hasAuthority("ROL_COLABORADOR")
              .requestMatchers("/api/orders/stats/admin/**")  .hasAuthority("ROL_ADMIN")

              // 3) Resto de órdenes
              .requestMatchers("/api/orders/admin/**")             .hasAuthority("ROL_ADMIN")
              .requestMatchers("/api/orders/completed/**")         .hasAuthority("ROL_ADMIN")
              .requestMatchers("/api/orders/manual")               .hasAuthority("ROL_CLIENTE")
              .requestMatchers("/api/orders/{orderId}/capture")    .hasAuthority("ROL_CLIENTE")
              .requestMatchers("/api/orders/{orderId}/download")   .hasAuthority("ROL_CLIENTE")
              .requestMatchers("/api/orders/{orderId}/receipt")    .hasAuthority("ROL_CLIENTE")
              .requestMatchers("/api/orders/pending-receipts")     .hasAuthority("ROL_ADMIN")
              .requestMatchers("/api/orders/my-orders/latest")     .authenticated()

              // 4) Genérica para cualquier otra ruta bajo /api/orders/**
              .requestMatchers("/api/orders/**")
                .hasAnyAuthority("ROL_CLIENTE","ROL_ADMIN","ROL_COLABORADOR")

              // 5) Cualquier otra cosa...
              .anyRequest().authenticated()
          )
          .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .oauth2ResourceServer(oauth2 -> oauth2
              .jwt(jwt -> jwt
                  .decoder(jwtDecoder())
                  .jwtAuthenticationConverter(jwtAuthConverter)
              )
          );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        return NimbusJwtDecoder
                 .withSecretKey(new SecretKeySpec(keyBytes,"HmacSHA256"))
                 .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();
        delegate.setAuthoritiesClaimName("role");
        delegate.setAuthorityPrefix(""); // leemos "ROL_CLIENTE" tal cual

        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(jwt -> 
              delegate.convert(jwt).stream()
                      .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                      .collect(Collectors.toList())
        );
        return conv;
    }
}
