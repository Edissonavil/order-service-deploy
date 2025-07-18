package com.aec.ordsrv.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtils {

    private final Key key;
    private final long accessMs;
    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

        public JwtUtils(@Value("${jwt.secret}") String secretBase64, @Value("${jwt.accessMs}") long accessMs) {
        // Decodifica la clave Base64 ANTES de usarla
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessMs = accessMs;
    }

    /** Genera un token con el subject=username, rol y fecha de expiración */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessMs);
        return Jwts.builder()
                   .setSubject(username)
                   .claim("role", role)  // Agregar claim role
                   .setIssuedAt(now)
                   .setExpiration(exp)
                   .signWith(key, SignatureAlgorithm.HS256)
                   .compact();
    }

    /** Genera un token solo con username (para compatibilidad) */
    public String generateToken(String username) {
        return generateToken(username, "ROL_CLIENTE"); // Rol por defecto
    }

    /** Extrae el username (subject) de un JWT válido */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                   .setSigningKey(key)
                   .build()
                   .parseClaimsJws(token)
                   .getBody()
                   .getSubject();
    }

    /** Extrae el rol de un JWT válido */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.get("role", String.class);  // Asegúrate de que esté extrayendo correctamente el rol
        } catch (Exception e) {
            log.error("Error obteniendo rol del token: {}", e.getMessage());
            return null;
        }
    }

    /** Valida firma y expiración */
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

            Date expiration = claims.getExpiration();
            Date now = new Date();

            log.debug("Token expiration: {}", expiration);
            log.debug("Current time: {}", now);
            log.debug("Token expired: {}", expiration.before(now));

            return !expiration.before(now);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    /** Extrae todos los claims del token */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                   .setSigningKey(key)
                   .build()
                   .parseClaimsJws(token)
                   .getBody();
    }

    /** Método temporal para debug */
    public void debugToken(String token) {
        log.info("=== DEBUG TOKEN ===");
        log.info("Token: {}", token);

        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                log.info("Token tiene 3 partes: ✓");

                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                log.info("Payload decodificado: {}", payload);

                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(getKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                log.info("Claims parseados exitosamente:");
                log.info("  - Subject: {}", claims.getSubject());
                log.info("  - Role: {}", claims.get("role"));
                log.info("  - Issued At: {}", claims.getIssuedAt());
                log.info("  - Expiration: {}", claims.getExpiration());
                log.info("  - Current time: {}", new Date());
                log.info("  - Is expired: {}", claims.getExpiration().before(new Date()));
            } else {
                log.error("Token no tiene 3 partes: {}", parts.length);
            }
        } catch (Exception e) {
            log.error("Error en debug del token: {}", e.getMessage(), e);
        }
        log.info("=== FIN DEBUG TOKEN ===");
    }

    /** Genera la clave secreta para firmar tokens */
    public Key getKey() {
        return key;
    }
}
