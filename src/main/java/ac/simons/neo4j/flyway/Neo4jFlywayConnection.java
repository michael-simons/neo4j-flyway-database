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

import org.flywaydb.core.internal.database.base.Connection;
import org.flywaydb.core.internal.database.base.Database;

/**
 * Access to Neo4j and it's schema and search path.
 *
 * @author Michael J. Simons
 */
final class Neo4jFlywayConnection extends Connection<Database<?>> {

	private volatile String schema;

	Neo4jFlywayConnection(Neo4jFlywayDatabase database, java.sql.Connection connection) {
		super(database, connection);
	}

	@Override
	protected String getCurrentSchemaNameOrSearchPath() throws SQLException {
		// This will always be public (at least, with Neo4j 5.x and 2025, but having it
		// derived from the connection is nice).
		String result = this.schema;
		if (result == null) {
			synchronized (this) {
				result = this.schema;
				if (result == null) {
					this.schema = getJdbcConnection().getSchema();
					result = this.schema;
				}
			}
		}
		return result;
	}

	@Override
	public Neo4jFlywaySchema getSchema(String name) {
		return new Neo4jFlywaySchema(getJdbcTemplate(), (Neo4jFlywayDatabase) super.database, name);
	}

}
