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

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * See <a href=
 * "https://github.com/neo4j/cypher-dsl/blob/main/neo4j-cypher-dsl-schema-name-support/src/main/java/org/neo4j/cypherdsl/support/schema_name/SchemaNames.java">Cypher-DSL
 * SchemaNames.java</a>.
 *
 * @author Michael J. Simons
 * @since 2022.8.0
 */
final class SchemaNames {

	private static final String ESCAPED_UNICODE_BACKTICK = "\\u0060";

	private static final Pattern PATTERN_ESCAPED_4DIGIT_UNICODE = Pattern.compile("\\\\u+(\\p{XDigit}{4})");

	private static final Pattern PATTERN_LABEL_AND_TYPE_QUOTATION = Pattern.compile("(?<!`)`(?:`{2})*(?!`)");

	private static final List<String[]> SUPPORTED_ESCAPE_CHARS = List.of(new String[] { "\\b", "\b" },
			new String[] { "\\f", "\f" }, new String[] { "\\n", "\n" }, new String[] { "\\r", "\r" },
			new String[] { "\\t", "\t" }, new String[] { "\\`", "``" });

	private static final int CACHE_SIZE = 128;

	private static final Map<CacheKey, SchemaName> CACHE = Collections
		.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE / 4, 0.75f, true) {
			@Serial
			private static final long serialVersionUID = -8109893585632797360L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<CacheKey, SchemaName> eldest) {
				return size() >= CACHE_SIZE;
			}
		});

	static Optional<String> sanitize(String value) {

		if (value == null || value.isEmpty()) {
			return Optional.empty();
		}

		CacheKey cacheKey = new CacheKey(value, -1, -1);
		SchemaName escapedValue = CACHE.computeIfAbsent(cacheKey, SchemaNames::sanitze);

		if (!escapedValue.needsQuotation) {
			return Optional.of(escapedValue.value);
		}

		return Optional.of(String.format(Locale.ENGLISH, "`%s`", escapedValue.value));
	}

	private static SchemaName sanitze(CacheKey key) {

		String workingValue = key.value;

		// Replace current and future escaped chars
		for (String[] pair : SUPPORTED_ESCAPE_CHARS) {
			workingValue = workingValue.replace(pair[0], pair[1]);
		}
		workingValue = workingValue.replace(ESCAPED_UNICODE_BACKTICK, "`");

		// Replace escaped octal hex
		// Excluding the support for 6 digit literals, as this contradicts the overall
		// example in CIP-59r
		Matcher matcher = PATTERN_ESCAPED_4DIGIT_UNICODE.matcher(workingValue);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String replacement = Character.toString((char) Integer.parseInt(matcher.group(1), 16));
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		workingValue = sb.toString();

		if (substituteRemainingEscapedUnicodeLiteral(key.major, key.minor)) {
			workingValue = workingValue.replace("\\u", "\\u005C\\u0075");
		}

		matcher = PATTERN_LABEL_AND_TYPE_QUOTATION.matcher(workingValue);
		workingValue = matcher.replaceAll("`$0");

		if (unescapeEscapedBackslashes(key.major)) {
			workingValue = workingValue.replace("\\\\", "\\");
		}

		return new SchemaName(workingValue, !isIdentifier(workingValue));
	}

	private static boolean substituteRemainingEscapedUnicodeLiteral(int major, int minor) {
		if (major == -1) {
			return true;
		}
		return major >= 4 && major <= 5 && (minor == -1 || minor >= 2);
	}

	private static boolean unescapeEscapedBackslashes(int major) {
		return major <= 5;
	}

	private static boolean isIdentifier(CharSequence name) {

		String id = name.toString();
		int cp = id.codePointAt(0);
		if (!Character.isJavaIdentifierStart(cp) || '$' == cp) {
			return false;
		}
		for (int i = Character.charCount(cp); i < id.length(); i += Character.charCount(cp)) {
			cp = id.codePointAt(i);
			if (!Character.isJavaIdentifierPart(cp) || '$' == cp) {
				return false;
			}
		}

		return true;
	}

	private SchemaNames() {
	}

	private record CacheKey(String value, int major, int minor) {
	}

	private record SchemaName(String value, boolean needsQuotation) {
	}

}
