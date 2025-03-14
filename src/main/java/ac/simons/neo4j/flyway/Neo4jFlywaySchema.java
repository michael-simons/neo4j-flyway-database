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
import java.util.ArrayList;

import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;

/**
 * A schema for Neo4j databases.
 *
 * @author Michael J. Simons
 */
final class Neo4jFlywaySchema extends Schema<Neo4jFlywayDatabase, Neo4jFlywayTable> {

	Neo4jFlywaySchema(JdbcTemplate jdbcTemplate, Neo4jFlywayDatabase database, String name) {
		super(jdbcTemplate, database, name);
	}

	@Override
	protected boolean doExists() {
		return true;
	}

	@Override
	protected boolean doEmpty() throws SQLException {
		return this.jdbcTemplate.queryForBoolean("/*+ NEO4J FORCE_CYPHER */ MATCH (n) RETURN count(n) = 0");
	}

	@Override
	protected void doCreate() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void doDrop() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void doClean() throws SQLException {
		this.jdbcTemplate.execute("/*+ NEO4J FORCE_CYPHER */ MATCH (n) DETACH DELETE n");
	}

	@Override
	protected Neo4jFlywayTable[] doAllTables() throws SQLException {

		var result = new ArrayList<Neo4jFlywayTable>();
		var metaData = this.database.getMainConnection().getJdbcConnection().getMetaData();
		try (var rs = metaData.getTables(metaData.getConnection().getCatalog(), getName(), null,
				new String[] { "TABLE" })) {
			while (rs.next()) {
				var tableName = rs.getString("TABLE_NAME");
				if ("__Neo4jMigrationsLock".equals(tableName)) {
					continue;
				}
				result.add(new Neo4jFlywayTable(this.jdbcTemplate, this.database, this, tableName));
			}
		}

		return result.toArray(Neo4jFlywayTable[]::new);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	public Table getTable(String tableName) {
		return new Neo4jFlywayTable(super.jdbcTemplate, super.database, this, tableName);
	}

}
