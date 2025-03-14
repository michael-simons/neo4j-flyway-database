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
import java.sql.SQLException;

import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.util.AbbreviationUtils;
import org.flywaydb.core.internal.util.Pair;

/**
 * Main entry point to Neo4j Flyway.
 *
 * @author Michael J. Simons
 */
final class Neo4jFlywayDatabase extends Database<Neo4jFlywayConnection> {

	Neo4jFlywayDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
			StatementInterceptor statementInterceptor) {
		super(configuration, jdbcConnectionFactory, statementInterceptor);
		try {
			jdbcTemplate.execute("""
					/*+ NEO4J FORCE_CYPHER */
					CREATE CONSTRAINT unique_version___Neo4jMigration IF NOT EXISTS
					FOR (n:__Neo4jMigration)
					REQUIRE (n.version, n.migrationTarget) IS UNIQUE
					""");
			jdbcTemplate.execute("""
					/*+ NEO4J FORCE_CYPHER */
					CREATE CONSTRAINT __Neo4jMigrationsLock__has_unique_id IF NOT EXISTS
					FOR (n:__Neo4jMigrationsLock)
					REQUIRE n.id IS UNIQUE
					""");
			jdbcTemplate.execute("""
					/*+ NEO4J FORCE_CYPHER */
					CREATE CONSTRAINT __Neo4jMigrationsLock__has_unique_name IF NOT EXISTS
					FOR (n:__Neo4jMigrationsLock)
					REQUIRE n.name IS UNIQUE
					""");
			jdbcTemplate.execute("""
					/*+ NEO4J FORCE_CYPHER */
					CREATE INDEX repeated_at__Neo4jMigration IF NOT EXISTS
					FOR ()-[r:REPEATED]-() ON (r.at)
					""");
		}
		catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	protected Neo4jFlywayConnection doGetConnection(Connection connection) {
		return new Neo4jFlywayConnection(this, connection);
	}

	@Override
	public void ensureSupported(Configuration configuration) {
	}

	@Override
	public boolean supportsDdlTransactions() {
		return false;
	}

	@Override
	public String getBooleanTrue() {
		return "true";
	}

	@Override
	public String getBooleanFalse() {
		return "false";
	}

	@Override
	public boolean catalogIsSchema() {
		return false;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	public String getRawCreateScript(Table table, boolean baseline) {
		var baselineStatement = getBaselineStatement(table);
		if (baseline) {
			return baselineStatement.replace("$rank", "1");
		}
		return baselineStatement.replace("$rank", "-1");
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	protected String getBaselineStatement(Table table) {
		// The baseline statement is not run as a prepared statement with parameters :(
		return """
				/*+ NEO4J FORCE_CYPHER */
				CREATE (p:__Neo4jMigration {flyway_installed_rank: $rank})
				SET p:%s,
					p.version = 'BASELINE',
					p.flyway_version = "%s",
					p.description = "%s",
					p.type = CASE "%s" WHEN 'SQL' THEN 'CYPHER' WHEN 'JDBC' THEN 'JAVA' ELSE NULL END,
					p.source = "%s",
					p.flyway_installed_on = datetime(),
					p.flyway_installed_by = "%s",
					p.flyway_execution_time = 0
				""".formatted(SchemaNames.sanitize(table.getName()).orElseThrow(),
				configuration.getBaselineVersion().toString().replace("\"", "\\\""),
				AbbreviationUtils.abbreviateDescription(configuration.getBaselineDescription()).replace("\"", "\\\""),
				CoreMigrationType.BASELINE,
				AbbreviationUtils.abbreviateScript(configuration.getBaselineDescription()).replace("\"", "\\\""),
				getInstalledBy().replace("\"", "\\\""));
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	public String getUpdateStatement(Table table) {
		return """
				/*+ NEO4J FORCE_CYPHER */
				MATCH (n:__Neo4jMigration:%s {flyway_installed_rank: $4})
				SET n.description = $1,
					n.type = CASE $2 WHEN 'SQL' THEN 'CYPHER' WHEN 'JDBC' THEN 'JAVA' ELSE $2 END,
					n.checksum = toString($3)
				FINISH
				""".formatted(SchemaNames.sanitize(table.getName()).orElseThrow());
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	public Pair<String, Object> getDeleteStatement(Table table, boolean version, String filter) {
		var predicate = version ? "c.version = $1" : "c.description = $1";
		var statement = """
				/*+ NEO4J FORCE_CYPHER */
				MATCH (c:__Neo4jMigration:%s)
				WHERE c.flyway_failed AND %s
				DETACH DELETE c
				""".formatted(SchemaNames.sanitize(table.getName()).orElseThrow(), predicate);
		return Pair.of(statement, filter);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	public String getSelectStatement(Table table) {
		return """
				/*+ NEO4J FORCE_CYPHER */
				MATCH (c:__Neo4jMigration:%s)
				OPTIONAL MATCH (p)-[r:MIGRATED_TO]->(c)
				ORDER BY r.at
				WITH collect([p,r,c]) AS rows
				UNWIND range(1, size(rows)) AS rank
				WITH rank, rows[rank-1] AS row
				WITH coalesce(row[2].flyway_installed_rank, rank) AS flyway_installed_rank, row[0] AS p, row[1] AS r, row[2] AS c
				WHERE flyway_installed_rank > $1
				AND (c.flyway_installed_rank IS NOT NULL OR c.version <> 'BASELINE')
				RETURN flyway_installed_rank AS installed_rank,
					coalesce(c.flyway_version,
					c.version
					) AS version,
					c.description AS description,
					CASE c.type WHEN 'CYPHER' THEN 'SQL' WHEN 'JAVA' THEN 'JDBC' ELSE coalesce(c.type, c.version) END AS type,
					coalesce(c.source, "") AS script,
					c.checksum AS checksum,
					coalesce(c.flyway_installed_on, r.at) AS installed_on,
					coalesce(c.flyway_installed_by, r.by + "/" + r.connectedAs) AS installed_by,
					coalesce(c.flyway_execution_time, r.in.milliseconds) AS execution_time,
					not(coalesce(c.flyway_failed, false)) AS success
				ORDER BY flyway_installed_rank
				"""
			.formatted(SchemaNames.sanitize(table.getName()).orElseThrow());
	}

	@Override
	@SuppressWarnings({ "rawtypes", "RedundantSuppression" })
	public String getInsertStatement(Table table) {
		return """
				/*+ NEO4J FORCE_CYPHER */
				CALL dbms.showCurrentUser() YIELD username
				WITH username, CASE $1 - 1 WHEN 0 THEN -1 ELSE $1 - 1 END as previous_rank
				MATCH (p:__Neo4jMigration:%1$s {flyway_installed_rank: previous_rank})
				CREATE (p) -[r:MIGRATED_TO]-> (c:__Neo4jMigration)
				SET c:%1$s,
					c.flyway_installed_rank = $1,
					c.version = $2,
					c.description = $3,
					c.type = CASE $4 WHEN 'SQL' THEN 'CYPHER' WHEN 'JDBC' THEN 'JAVA' ELSE $4 END,
					c.source = $5,
					c.checksum = toString($6),
					r.by = split($7, '/')[0],
					r.connectedAs = coalesce(split($7, '/')[1], username),
					r.at = datetime(),
					r.in =duration({milliseconds: $8}),
					c.flyway_failed = CASE $9 WHEN true THEN null ELSE true END
				""".formatted(SchemaNames.sanitize(table.getName()).orElseThrow());
	}

	@Override
	protected String doGetCurrentUser() throws SQLException {
		return System.getProperty("user.name") + "/" + super.doGetCurrentUser();
	}

}
