package com.nonononoki.alovoa.integration;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for integration tests using Testcontainers.
 * Provides MariaDB, MinIO, and MailHog (SMTP) containers that start automatically
 * and configure Spring Boot to use them.
 *
 * Uses singleton containers to ensure they are shared across all test classes
 * and remain running for the entire test suite.
 *
 * Usage:
 * <pre>
 * @SpringBootTest
 * @Transactional
 * public class MyIntegrationTest extends BaseIntegrationTest {
 *     @Test
 *     void testSomething() throws Exception {
 *         // test code using real database, S3, and SMTP
 *     }
 * }
 * </pre>
 */
@EnabledIf("isDockerAvailable")
public abstract class BaseIntegrationTest {

    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin";
    private static final int MINIO_PORT = 9000;
    private static final int SMTP_PORT = 1025;
    private static final int SMTP_WEB_PORT = 8025;

    /**
     * Run integration tests when Docker is available.
     * Testcontainers manages all containers (no GitHub Actions service containers needed).
     */
    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Singleton containers - started once and shared across all tests
    // Use static initializer block to start containers once for entire test suite
    static MariaDBContainer<?> mariaDBContainer;
    static GenericContainer<?> minioContainer;
    static GenericContainer<?> mailhogContainer;

    static {
        // Only start containers if Docker is available
        if (isDockerAvailable()) {
            mariaDBContainer = new MariaDBContainer<>("mariadb:10.11")
                    .withDatabaseName("alovoa_test")
                    .withUsername("alovoa_test")
                    .withPassword("alovoa_test")
                    .withUrlParam("serverTimezone", "UTC")
                    .withUrlParam("useLegacyDatetimeCode", "false")
                    .withStartupTimeout(Duration.ofMinutes(3));
            mariaDBContainer.start();

            minioContainer = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                    .withExposedPorts(MINIO_PORT)
                    .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
                    .withCommand("server /data")
                    .waitingFor(Wait.forHttp("/minio/health/live")
                            .forPort(MINIO_PORT)
                            .withStartupTimeout(Duration.ofMinutes(2)));
            minioContainer.start();

            // MailHog SMTP server for email testing
            // Provides SMTP server that catches all emails without authentication
            // Simpler than GreenMail and matches the E2E setup
            mailhogContainer = new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:latest"))
                    .withExposedPorts(SMTP_PORT, SMTP_WEB_PORT)
                    .waitingFor(Wait.forHttp("/")
                            .forPort(SMTP_WEB_PORT)
                            .withStartupTimeout(Duration.ofMinutes(2)));
            mailhogContainer.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database configuration
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.datasource.url", mariaDBContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mariaDBContainer::getUsername);
        registry.add("spring.datasource.password", mariaDBContainer::getPassword);

        // HikariCP connection pool settings for test stability
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "600000");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "1800000");

        // S3/MinIO configuration
        registry.add("app.storage.s3.enabled", () -> "true");
        registry.add("app.storage.s3.endpoint", () ->
                String.format("http://%s:%d", minioContainer.getHost(), minioContainer.getMappedPort(MINIO_PORT)));
        registry.add("app.storage.s3.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("app.storage.s3.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("app.storage.s3.bucket", () -> "alovoa-test");
        registry.add("app.storage.s3.region", () -> "us-east-1");

        // SMTP/Mail configuration (MailHog)
        // MailHog doesn't require authentication
        registry.add("spring.mail.host", () -> mailhogContainer.getHost());
        registry.add("spring.mail.port", () -> mailhogContainer.getMappedPort(SMTP_PORT).toString());
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.test-connection", () -> "false");

        // Test encryption keys
        registry.add("app.text.key", () -> "bqupWgmhCj3fedLxYdNAy2QFA2bS9XJX");
        registry.add("app.text.salt", () -> "sFRKQAhwdrZq44FQ");
        registry.add("app.text.encrypt.return-null-on-fail", () -> "true");

        // Admin credentials
        registry.add("app.admin.email", () -> "admin@alovoa.com");
        registry.add("app.admin.key", () -> "password");

        // Disable scheduling for tests
        registry.add("app.scheduling.enabled", () -> "false");

        // Disable cache and rate limiting
        registry.add("spring.cache.type", () -> "NONE");
        registry.add("bucket4j.enabled", () -> "false");

        // Mock OAuth2 credentials
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test");
        registry.add("spring.security.oauth2.client.registration.facebook.client-id", () -> "test");
        registry.add("spring.security.oauth2.client.registration.facebook.client-secret", () -> "test");
    }
}
