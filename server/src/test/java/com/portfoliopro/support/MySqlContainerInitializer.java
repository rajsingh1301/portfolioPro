package com.portfoliopro.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One MySQL 8.4 container for the whole test run. It matches the version used in
 * development, so Flyway runs the same MySQL-specific migrations the real database
 * gets and `ddl-auto=validate` checks the entities against the real schema.
 *
 * <p>The container is started manually and never stopped: Testcontainers' Ryuk
 * sidecar removes it when the JVM exits, and a static instance is reused across
 * every test class instead of being restarted for each one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerInitializer {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("portfoliopro_test")
                    .withReuse(true);

    static {
        MYSQL.start();
    }

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return MYSQL;
    }
}
