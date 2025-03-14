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

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resource.Resource;
import org.flywaydb.core.internal.parser.Parser;
import org.flywaydb.core.internal.parser.ParserContext;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.parser.PeekingReader;
import org.flywaydb.core.internal.parser.PositionTracker;
import org.flywaydb.core.internal.parser.Recorder;
import org.flywaydb.core.internal.sqlscript.SqlStatement;

/**
 * The parent parser ofc does only SQL, but it seems to be "good enough" to handle most
 * Cypher without burning down to the ground immediately. Neo4j can't deal with "comment
 * only" statements, so we filter those.
 *
 * @author Michael J. Simons
 */
final class Neo4jFlywayParser extends Parser {

	Neo4jFlywayParser(Configuration configuration, ParsingContext parsingContext, int peekDepth) {
		super(configuration, parsingContext, peekDepth);
	}

	@Override
	protected SqlStatement getNextStatement(Resource resource, PeekingReader reader, Recorder recorder,
			PositionTracker tracker, ParserContext context) {
		var nextStatement = super.getNextStatement(resource, reader, recorder, tracker, context);
		if (nextStatement == null) {
			return null;
		}
		var sql = nextStatement.getSql().trim();
		if (sql.lines().allMatch(l -> l.startsWith("//"))) {
			return null;
		}
		return nextStatement;
	}

}
