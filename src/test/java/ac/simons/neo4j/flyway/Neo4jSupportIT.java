/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ac.simons.neo4j.flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.exception.FlywayMigrateException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SuppressWarnings("SqlNoDataSourceInspection")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jSupportIT {

	@SuppressWarnings("resource") // On purpose to reuse this
	protected final Neo4jContainer<?> neo4j = new Neo4jContainer<>(System.getProperty("neo4j-jdbc.default-neo4j-image"))
		.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
		.waitingFor(Neo4jContainer.WAIT_FOR_BOLT)
		.withReuse(true);

	@BeforeAll
	void startNeo4j() {
		this.neo4j.start();
	}

	@BeforeEach
	void prepareData() throws SQLException {
		try (var con = getConnection(false); var stmt = con.createStatement()) {
			stmt.executeUpdate("DROP CONSTRAINT unique_version___Neo4jMigration IF EXISTS");
			stmt.executeUpdate("DROP INDEX repeated_at__Neo4jMigration IF EXISTS");
			stmt.executeUpdate("DROP CONSTRAINT __Neo4jMigrationsLock__has_unique_id IF EXISTS");
			stmt.executeUpdate("DROP CONSTRAINT __Neo4jMigrationsLock__has_unique_name IF EXISTS");
			stmt.executeUpdate("MATCH (n) DETACH DELETE n");
		}
	}

	String getUrl(boolean enableSQLTranslation) {
		return "jdbc:neo4j://localhost:%d?enableSQLTranslation=%s".formatted(this.neo4j.getMappedPort(7687),
				enableSQLTranslation);
	}

	String getUsername() {
		return "neo4j";
	}

	String getPassword() {
		return this.neo4j.getAdminPassword();
	}

	Connection getConnection(boolean enableSQLTranslation) throws SQLException {
		return DriverManager.getConnection(getUrl(enableSQLTranslation), getUsername(), getPassword());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldMigrate(boolean enableSQLTranslation) throws SQLException {
		var flyway = Flyway.configure()
			.sqlMigrationSuffixes(".sql", ".cypher")
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.locations("migrations000")
			.load();

		flyway.migrate();
		// Should only migrate once
		flyway.migrate();
		flyway.migrate();
		assertCount(enableSQLTranslation,
				"/*+ NEO4J FORCE_CYPHER */ MATCH (n:V01__ThisIsABaseline|V02__Next) RETURN count(n) AS cnt", 2);

		var info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("02");
		assertThat(info.migrations).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldMigrateAdditional(boolean enableSQLTranslation) throws SQLException {
		var flyway = Flyway.configure()
			.sqlMigrationSuffixes(".sql", ".cypher")
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.locations("migrations000")
			.load();

		flyway.migrate();

		flyway = Flyway.configure()
			.sqlMigrationSuffixes(".sql", ".cypher")
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.locations("migrations000", "migrations003")
			.load();

		flyway.migrate();
		flyway.migrate();
		assertCount(enableSQLTranslation,
				"/*+ NEO4J FORCE_CYPHER */ MATCH (n:V01__ThisIsABaseline|V02__Next|V03__Third) RETURN count(n) AS cnt",
				3);

		var info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("03");
		assertThat(info.migrations).hasSize(3);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldBaselineOnMigrate(boolean enableSQLTranslation) throws SQLException {
		var flyway = Flyway.configure()
			.sqlMigrationSuffixes(".sql", ".cypher")
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.baselineOnMigrate(true)
			.locations("migrations001")
			.load();

		flyway.migrate();
		// Should only migrate once
		flyway.migrate();
		flyway.migrate();
		assertCount(enableSQLTranslation,
				"/*+ NEO4J FORCE_CYPHER */ MATCH (n:V01__ThisIsABaseline|V02__Next) RETURN count(n) AS cnt", 1);

		var info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("02");
		assertThat(info.migrations).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldBaseline(boolean enableSQLTranslation) throws SQLException {
		var flyway = Flyway.configure()
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.baselineVersion("123")
			.load();

		flyway.baseline();

		flyway = Flyway.configure().dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword()).load();

		var info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("123");
		assertThat(info.migrations).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldRepair(boolean enableSQLTranslation) throws SQLException {
		var flyway = Flyway.configure()
			.sqlMigrationSuffixes(".sql", ".cypher")
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.locations("migrations000", "migrations004")
			.load();

		assertThatExceptionOfType(FlywayMigrateException.class).isThrownBy(flyway::migrate);

		var info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("04");
		assertThat(info.migrations).hasSize(3);
		assertThat(info.migrations.get(2).state).isEqualTo("Failed");

		flyway.repair();

		info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("02");
		assertThat(info.migrations).hasSize(3);
		assertThat(info.migrations.get(2).state).isEqualTo("Pending");
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldMigrateWithIgnoredBaseline(boolean enableSQLTranslation) throws SQLException {
		var flyway = Flyway.configure()
			.sqlMigrationSuffixes(".sql", ".cypher")
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.locations("migrations000")
			.load();

		flyway.baseline();
		flyway.migrate();
		assertCount(enableSQLTranslation,
				"/*+ NEO4J FORCE_CYPHER */ MATCH (n:V01__ThisIsABaseline|V02__Next) RETURN count(n) AS cnt", 1);

		var info = flyway.info().getInfoResult();
		assertThat(info.schemaVersion).isEqualTo("02");
		assertThat(info.migrations).hasSize(3);
		assertThat(info.migrations.get(0).state).isEqualTo("Ignored (Baseline)");
		assertThat(info.migrations.get(0).type).isEqualTo("SQL");
		assertThat(info.migrations.get(1).state).isEqualTo("Baseline");
		assertThat(info.migrations.get(1).type).isEqualTo("BASELINE");
		assertThat(info.migrations.get(2).state).isEqualTo("Success");
		assertThat(info.migrations.get(2).type).isEqualTo("SQL");
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void shouldClean(boolean enableSQLTranslation) throws SQLException {

		var flyway = Flyway.configure()
			.dataSource(getUrl(enableSQLTranslation), getUsername(), getPassword())
			.cleanDisabled(false)
			.load();

		try (var connection = getConnection(enableSQLTranslation); var stmt = connection.createStatement()) {
			stmt.executeUpdate("/*+ NEO4J FORCE_CYPHER */ CREATE (:A)-[:RELATED_TO]->(:B)");
		}

		flyway.clean();
		assertCount(enableSQLTranslation, "/*+ NEO4J FORCE_CYPHER */ MATCH (n:A|B) RETURN count(n) AS cnt", 0);
	}

	void assertCount(boolean enableSQLTranslation, String query, long expected) throws SQLException {
		try (var connection = getConnection(enableSQLTranslation);
				var stmt = connection.createStatement();
				var rs = stmt.executeQuery(query)

		) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getLong("cnt")).isEqualTo(expected);
		}
	}

}
