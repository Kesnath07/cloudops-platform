package io.cloudops.platform.support;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application against a real PostgreSQL server (embedded binaries, so no Docker
 * daemon is required). Flyway migrates the schema and Hibernate validates it on startup, so any
 * drift between migrations and entity mappings fails these tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    protected static final String PASSWORD = "integration-test-password";
    protected static final String ADMIN_EMAIL = "admin@cloudops.test";

    private static final EmbeddedPostgres POSTGRES = start();

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start embedded PostgreSQL", ex);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE incident_updates, incidents, deployments, workloads, teams, user_accounts CASCADE");
    }

    protected String registerAndLogin(String email, String displayName) {
        assertThat(postJson("/api/v1/auth/register", null, """
                {"email":"%s","displayName":"%s","password":"%s"}""".formatted(email, displayName, PASSWORD)))
                .hasStatus(HttpStatus.CREATED);
        return login(email);
    }

    protected String login(String email) {
        MvcTestResult result = postJson("/api/v1/auth/token", null, """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD));
        return read(result, "$.accessToken");
    }

    protected MvcTestResult postJson(String uri, String token, String body) {
        var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange();
    }

    protected MvcTestResult putJson(String uri, String token, String body) {
        return mvc.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    protected MvcTestResult get(String uri, String token) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    protected MvcTestResult delete(String uri, String token) {
        return mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    protected static <T> T read(MvcTestResult result, String jsonPath) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
