/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.logging.logback.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class UnmigratedJaninoConditions extends DataTable<UnmigratedJaninoConditions.Row> {

    public UnmigratedJaninoConditions(Recipe recipe) {
        super(recipe,
                "Unmigrated Janino conditions",
                "Janino conditions that have no equivalent among the conditions shipped with logback-core, " +
                "and so have to be migrated by hand to a custom `PropertyCondition`.");
    }

    @Value
    public static class Row {

        @Column(displayName = "Source path",
                description = "The path of the logback configuration file containing the condition.")
        String sourcePath;

        @Column(displayName = "Condition",
                description = "The value of the `condition` attribute that was left unchanged.")
        String condition;
    }
}
