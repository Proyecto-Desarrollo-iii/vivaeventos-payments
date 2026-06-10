package co.empresa.vivaeventos.payments.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JwtUtil.class)
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@TestPropertySource(properties = "jwt.secret=dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw")
class JwtUtilTest {

    private static final String SECRET = "dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    @Test
    void validateToken_withValidToken_returnsClaims() {
        String token = Jwts.builder()
                .subject("user@test.com")
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        // We need the actual JwtUtil bean from context - use a new instance
        JwtUtil jwtUtil = new JwtUtil();
        java.lang.reflect.Field field;
        try {
            field = JwtUtil.class.getDeclaredField("secretKey");
            field.setAccessible(true);
            field.set(jwtUtil, SECRET);
            jwtUtil.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Optional<Claims> result = jwtUtil.validateToken(token);
        assertThat(result).isPresent();
        assertThat(result.get().getSubject()).isEqualTo("user@test.com");
        assertThat(result.get().get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void validateToken_withExpiredToken_returnsEmpty() throws InterruptedException {
        String token = Jwts.builder()
                .subject("user@test.com")
                .claim("userId", UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(signingKey)
                .compact();

        JwtUtil jwtUtil = new JwtUtil();
        java.lang.reflect.Field field;
        try {
            field = JwtUtil.class.getDeclaredField("secretKey");
            field.setAccessible(true);
            field.set(jwtUtil, SECRET);
            jwtUtil.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Optional<Claims> result = jwtUtil.validateToken(token);
        assertThat(result).isEmpty();
    }

    @Test
    void validateToken_withInvalidSignature_returnsEmpty() {
        String token = Jwts.builder()
                .subject("user@test.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwYWJjZGVmZ2hpamtsbW5vcA==".getBytes()))
                .compact();

        JwtUtil jwtUtil = new JwtUtil();
        java.lang.reflect.Field field;
        try {
            field = JwtUtil.class.getDeclaredField("secretKey");
            field.setAccessible(true);
            field.set(jwtUtil, SECRET);
            jwtUtil.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Optional<Claims> result = jwtUtil.validateToken(token);
        assertThat(result).isEmpty();
    }

    @Test
    void extractUserId_returnsUserId() {
        String userId = UUID.randomUUID().toString();
        Claims claims = Jwts.claims()
                .subject("user@test.com")
                .add("userId", userId)
                .build();

        JwtUtil jwtUtil = new JwtUtil();
        assertThat(jwtUtil.extractUserId(claims)).isEqualTo(userId);
    }

    @Test
    void extractEmail_returnsSubject() {
        Claims claims = Jwts.claims()
                .subject("user@test.com")
                .build();

        JwtUtil jwtUtil = new JwtUtil();
        assertThat(jwtUtil.extractEmail(claims)).isEqualTo("user@test.com");
    }

    @Test
    void extractRole_returnsRole() {
        Claims claims = Jwts.claims()
                .subject("user@test.com")
                .add("role", "ADMIN")
                .build();

        JwtUtil jwtUtil = new JwtUtil();
        assertThat(jwtUtil.extractRole(claims)).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_withMalformedToken_returnsEmpty() {
        JwtUtil jwtUtil = new JwtUtil();
        java.lang.reflect.Field field;
        try {
            field = JwtUtil.class.getDeclaredField("secretKey");
            field.setAccessible(true);
            field.set(jwtUtil, SECRET);
            jwtUtil.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Optional<Claims> result = jwtUtil.validateToken("malformed.token.here");
        assertThat(result).isEmpty();
    }
}
