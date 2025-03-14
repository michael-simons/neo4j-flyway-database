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

import java.sql.SQLException;

import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;

/**
 * Representation of a "table" in Neo4j: We grab all tables from the JDBC driver, except
 * the virtual relationship tables. Locking of tables in Neo4j is problematic: There is no
 * such thing as a single table, and we are trying to do this via creating a constrained
 * node.
 *
 * @author Michael J. Simons
 */
final class Neo4jFlywayTable extends Table<Neo4jFlywayDatabase, Neo4jFlywaySchema> {

	Neo4jFlywayTable(JdbcTemplate jdbcTemplate, Neo4jFlywayDatabase database, Neo4jFlywaySchema schema, String name) {
		super(jdbcTemplate, database, schema, name);
	}

	@Override
	protected boolean doExists() throws SQLException {

		return this.jdbcTemplate.queryForBoolean(
				"/*+ NEO4J FORCE_CYPHER */ MATCH (n) WHERE any(l IN labels(n) WHERE l = $1) RETURN count(n) > 0",
				this.name);
	}

	@Override
	protected void doLock() throws SQLException {
		var currentTx = getCurrentTransactionId();
		if (!this.jdbcTemplate.queryForBoolean(
				"/*+ NEO4J FORCE_CYPHER */ MATCH (l:__Neo4jMigrationsLock {id: $1, name: $2}) RETURN count(l) > 0",
				currentTx, name)) {
			this.jdbcTemplate.execute("""
					/*+ NEO4J FORCE_CYPHER */
					CREATE (l:__Neo4jMigrationsLock {id: $1, name: $2})
					FINISH
					""", currentTx, name);
		}
	}

	private String getCurrentTransactionId() throws SQLException {
		var currentUser = this.getDatabase().getJdbcMetaData().getUserName();
		return this.jdbcTemplate.queryForString("""
				/*+ NEO4J FORCE_CYPHER */
				SHOW TRANSACTIONS YIELD transactionId, username, currentQuery WHERE username = $1 AND currentQuery
				CONTAINS 'SHOW TRANSACTIONS YIELD transactionId, username, currentQuery WHERE username = $1'
				RETURN transactionId
				""", currentUser);
	}

	@Override
	protected void doUnlock() throws SQLException {
		this.jdbcTemplate.execute("""
				/*+ NEO4J FORCE_CYPHER */
				MATCH (l:__Neo4jMigrationsLock {id: $1, name: $2})
				DELETE l
				FINISH
				""", getCurrentTransactionId(), name);
	}

	@Override
	protected void doDrop() throws SQLException {

		this.jdbcTemplate.execute("""
				/*+ NEO4J FORCE_CYPHER */
				MATCH (n:%s)
				OPTIONAL MATCH (l:__Neo4jMigrationsLock {name: $1})
				DETACH DELETE n, l
				""".formatted(SchemaNames.sanitize(this.name).orElseThrow()), name);
	}

}
