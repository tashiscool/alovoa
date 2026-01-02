package com.nonononoki.alovoa.integration;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for integration tests using Testcontainers.
 * Provides MariaDB and MinIO containers that start automatically
 * and configure Spring Boot to use them.
 *
 * Usage:
 * <pre>
 * @SpringBootTest
 * @Transactional
 * public class MyIntegrationTest extends BaseIntegrationTest {
 *     @Test
 *     void testSomething() throws Exception {
 *         // test code using real database and S3
 *     }
 * }
 * </pre>
 */
@Testcontainers
@EnabledIf("isDockerAvailable")
public abstract class BaseIntegrationTest {

    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin";
    private static final int MINIO_PORT = 9000;

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

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("alovoa_test")
            .withUsername("alovoa_test")
            .withPassword("alovoa_test")
            .withUrlParam("serverTimezone", "UTC")
            .withUrlParam("useLegacyDatetimeCode", "false")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static GenericContainer<?> minioContainer = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(MINIO_PORT)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server /data")
            .waitingFor(new HttpWaitStrategy()
                    .forPath("/minio/health/live")
                    .forPort(MINIO_PORT)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database configuration
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

        // Test encryption keys
        registry.add("app.text.key", () -> "bqupWgmhCj3fedLxYdNAy2QFA2bS9XJX");
        registry.add("app.text.salt", () -> "sFRKQAhwdrZq44FQ");
        registry.add("app.text.encrypt.return-null-on-fail", () -> "true");

        // Admin credentials
        registry.add("app.admin.email", () -> "admin@alovoa.com");
        registry.add("app.admin.key", () -> "password");

        // Disable scheduling for tests
        registry.add("app.scheduling.enabled", () -> "false");

        // Disable mail
        registry.add("spring.mail.test-connection", () -> "false");

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
