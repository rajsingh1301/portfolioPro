package com.portfoliopro;

import com.portfoliopro.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Starting the context is itself an assertion: Flyway applies every migration to a
 * real MySQL and `ddl-auto=validate` then checks the entities still match the schema
 * it produced, so a migration that drifts from an entity fails here.
 */
@IntegrationTest
@DisplayName("Application")
class ServerApplicationTests {

	@Test
	@DisplayName("the context starts and the migrations match the entities")
	void contextLoads() {
	}

}
