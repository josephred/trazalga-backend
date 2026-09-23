package com.trazalga.api.security;

import com.trazalga.api.models.UsuarioModel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {

    @Value("${trazalga.jwt.secret}")
    private String secret;

    @Value("${trazalga.jwt.expiration}")
    private Long jwtExpiration;

    // 1. Generar el token cuando el usuario se loguea
    public String generateToken(UsuarioModel usuario) {
        Map<String, Object> claims = new HashMap<>();
        // Guardamos datos útiles dentro del token
        claims.put("rut", usuario.getRut());
        claims.put("perfil", usuario.getPerfil() != null ? usuario.getPerfil().getNombre() : null);
        claims.put("nombre", usuario.getNombres());
        claims.put("usuarioId", usuario.getId());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(usuario.getRut())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. Extraer el RUT del token
    public String extractRut(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // 2.1 Extraer el Perfil del token
    public String extractPerfil(String token) {
        return extractClaim(token, claims -> (String) claims.get("perfil"));
    }

    // 2.2 Extraer el Nombre del token
    public String extractNombre(String token) {
        return extractClaim(token, claims -> (String) claims.get("nombre"));
    }

    // 2.3 Extraer el usuarioId del token
    public Long extractUsuarioId(String token) {
        return extractClaim(token, claims -> {
            Object id = claims.get("usuarioId");
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
            return null;
        });
    }

    // 3. Validar si el token es correcto y no ha expirado
    public Boolean validateToken(String token, String rut) {
        final String tokenRut = extractRut(token);
        return (tokenRut.equals(rut) && !isTokenExpired(token));
    }

    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // Métodos de apoyo internos
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }

    private Boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}