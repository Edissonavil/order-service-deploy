// src/main/java/com/aec/prodsrv/security/CustomUserDetailsService.java
package com.aec.ordsrv.security;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.aec.ordsrv.controller.OrderController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Carga los detalles del usuario (y sus roles) desde el microservicio users-service.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final RestTemplate restTemplate;
    private final JwtUtils jwtUtils;
            private static final Logger log = LoggerFactory.getLogger(OrderController.class);


    /**
     * URL base de tu users-service, p.ej. http://localhost:8081 
     * o bien podrías usar Eureka/Consul:
     *   @Value("${users.service.url}") 
     */
    @Value("${user.service.url}")
    private String usersServiceUrl;

    @Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String url = String.format("%s/api/users/%s", usersServiceUrl, username);
    UserDto user;
    try {
        user = restTemplate.getForObject(url, UserDto.class);
    } catch (RestClientException ex) {
        throw new UsernameNotFoundException("Usuario no encontrado: " + username, ex);
    }
    if (user == null) {
        throw new UsernameNotFoundException("Usuario no encontrado: " + username);
    }
    // Log de roles
    log.info("Roles obtenidos para el usuario {}: {}", username, user.getRoles());

    List<GrantedAuthority> authorities = user.getRoles().stream()
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());

    return User.builder()
               .username(user.getUsername())
               .password("") // Sin usar contraseña porque es validado por JWT
               .authorities(authorities)
               .accountExpired(false)
               .accountLocked(false)
               .credentialsExpired(false)
               .disabled(false)
               .build();
}


    /**
     * DTO interno para mapear la respuesta de users-service.
     */
    public static class UserDto {
        private String username;
        private List<String> roles;
        // getters y setters (o usa Lombok @Data)

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    
}
