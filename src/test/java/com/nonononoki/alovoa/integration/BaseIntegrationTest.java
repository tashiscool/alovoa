package com.nonononoki.alovoa.integration;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using Testcontainers.
 * Provides a MariaDB container that starts automatically and
 * configures Spring Boot to use it.
 *
 * Usage:
 * <pre>
 * @SpringBootTest
 * @Transactional
 * public class MyIntegrationTest extends BaseIntegrationTest {
 *     @Test
 *     void testSomething() throws Exception {
 *         // test code using real database
 *     }
 * }
 * </pre>
 */
@Testcontainers
@EnabledIf("isDockerAvailable")
public abstract class BaseIntegrationTest {

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
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariaDBContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mariaDBContainer::getUsername);
        registry.add("spring.datasource.password", mariaDBContainer::getPassword);

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
