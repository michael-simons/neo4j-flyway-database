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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.BaseDatabaseType;
import org.flywaydb.core.internal.database.base.CommunityDatabaseType;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.parser.Parser;
import org.flywaydb.core.internal.parser.ParsingContext;

/**
 * Main entry point for Flyways plugin system.
 *
 * @author Michael J. Simons
 */
public final class Neo4jFlywayDatabaseType extends BaseDatabaseType implements CommunityDatabaseType {

	private static final String URL_REGEX = "(?i)^jdbc:neo4j(?:\\+(s(sc)?)?)?://";

	/**
	 * Needed for the plugin loader.
	 */
	public Neo4jFlywayDatabaseType() {
		Logger.getLogger("org.neo4j.jdbc").setLevel(Level.SEVERE);
	}

	@Override
	public String getName() {
		return "Neo4j";
	}

	@Override
	public int getNullType() {
		return 0;
	}

	@Override
	public boolean handlesJDBCUrl(String url) {
		return Pattern.compile(URL_REGEX).asPredicate().test(url);
	}

	@Override
	public String getDriverClass(String url, ClassLoader classLoader) {
		return "org.neo4j.jdbc.Neo4jDriver";
	}

	@Override
	public boolean handlesDatabaseProductNameAndVersion(String databaseProductName, String databaseProductVersion,
			Connection connection) {
		return databaseProductName.startsWith("Neo4j");
	}

	@Override
	public Database<?> createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
			StatementInterceptor statementInterceptor) {
		return new Neo4jFlywayDatabase(configuration, jdbcConnectionFactory, statementInterceptor);
	}

	@Override
	public Parser createParser(Configuration configuration, ResourceProvider resourceProvider,
			ParsingContext parsingContext) {
		return new Neo4jFlywayParser(configuration, parsingContext, 10);
	}

}
