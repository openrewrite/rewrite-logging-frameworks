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
package org.openrewrite.java.logging.slf4j;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.tree.*;

import java.util.*;

class Slf4jFluentProviders {
    private static final GroupArtifact LOGBACK = new GroupArtifact("ch.qos.logback", "logback-classic");
    private static final GroupArtifact LOG4J_BINDING = new GroupArtifact("org.apache.logging.log4j", "log4j-slf4j-impl");
    private static final GroupArtifact SLF4J_JCL = new GroupArtifact("org.slf4j", "slf4j-jcl");
    private static final GroupArtifact SPRING_BOOT = new GroupArtifact("org.springframework.boot", "spring-boot");
    private static final Set<String> SLF4J_BINDINGS = new HashSet<>(Arrays.asList(
            "slf4j-simple", "slf4j-jdk14", "slf4j-nop", "slf4j-reload4j", "slf4j-log4j12"));

    private final Step<?> slf4j;
    private final Step<?> log4j12;
    private final Step<?> log4jBinding;
    private final Step<?> log4j;
    private final List<Step<?>> logbackJava8;
    private final List<Step<?>> logback;
    private final List<Step<?>> all;
    private final Map<JavaProject, Module> modules = new HashMap<>();
    private final Map<JavaProject, Integer> javaVersions = new HashMap<>();

    Slf4jFluentProviders(ExecutionContext ctx) {
        slf4j = new Step<>(new UpgradeDependencyVersion("org.slf4j", "*", "2.x", null, true, null), ctx);
        log4j12 = new Step<>(new ChangeDependency("org.slf4j", "slf4j-log4j12", null, "slf4j-reload4j", "2.x", null, true, true), ctx);
        log4jBinding = new Step<>(new ChangeDependency(LOG4J_BINDING.getGroupId(), LOG4J_BINDING.getArtifactId(), null, "log4j-slf4j2-impl", "2.x", null, true, true), ctx);
        log4j = new Step<>(new UpgradeDependencyVersion("org.apache.logging.log4j", "log4j-*", "2.x", null, true, null), ctx);
        logbackJava8 = Arrays.asList(
                new Step<>(new UpgradeDependencyVersion("ch.qos.logback", "logback-classic", "1.3.x", null, true, null), ctx),
                new Step<>(new UpgradeDependencyVersion("ch.qos.logback", "logback-core", "1.3.x", null, true, null), ctx));
        logback = Arrays.asList(
                new Step<>(new UpgradeDependencyVersion("ch.qos.logback", "logback-classic", "1.5.x", null, true, null), ctx),
                new Step<>(new UpgradeDependencyVersion("ch.qos.logback", "logback-core", "1.5.x", null, true, null), ctx));
        all = new ArrayList<>(Arrays.asList(slf4j, log4j12, log4jBinding, log4j));
        all.addAll(logbackJava8);
        all.addAll(logback);
    }

    TreeVisitor<?, ExecutionContext> scanner() {
        List<TreeVisitor<?, ExecutionContext>> scanners = new ArrayList<>();
        for (Step<?> step : all) {
            scanners.add(step.scanner());
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                for (TreeVisitor<?, ExecutionContext> scanner : scanners) {
                    if (scanner.isAcceptable(sourceFile, ctx)) {
                        scanner.visit(sourceFile, ctx);
                    }
                }
                sourceFile.getMarkers().findFirst(JavaProject.class).ifPresent(project -> record(project, sourceFile));
                return tree;
            }
        };
    }

    private void record(JavaProject project, SourceFile sourceFile) {
        if (sourceFile instanceof J.CompilationUnit) {
            sourceFile.getMarkers().findFirst(JavaVersion.class).ifPresent(version ->
                    javaVersions.merge(project, version.getMajorReleaseVersion(), Math::min));
            return;
        }
        Optional<MavenResolutionResult> maven = sourceFile.getMarkers().findFirst(MavenResolutionResult.class);
        if (maven.isPresent()) {
            Module module = modules.computeIfAbsent(project, p -> new Module());
            ResolvedPom pom = maven.get().getPom();
            module.self = new GroupArtifact(pom.getGroupId(), pom.getArtifactId());
            for (List<ResolvedDependency> dependencies : maven.get().getDependencies().values()) {
                dependencies.forEach(module::resolved);
            }
            Map<String, String> properties = pom.getRequested().getProperties();
            for (Dependency dependency : pom.getRequested().getDependencies()) {
                module.declared(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), properties);
            }
            for (ManagedDependency managed : pom.getRequested().getDependencyManagement()) {
                module.declared(managed.getGroupId(), managed.getArtifactId(), managed.getVersion(), properties);
            }
            return;
        }
        Optional<GradleProject> gradle = sourceFile.getMarkers().findFirst(GradleProject.class);
        if (gradle.isPresent()) {
            Module module = modules.computeIfAbsent(project, p -> new Module());
            module.self = new GroupArtifact(gradle.get().getGroup(), gradle.get().getName());
            for (GradleDependencyConfiguration configuration : gradle.get().getConfigurations()) {
                for (Dependency dependency : configuration.getRequested()) {
                    module.declared(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), Collections.emptyMap());
                }
                if (configuration.isCanBeResolved() && configuration.getName().toLowerCase(Locale.ROOT).endsWith("runtimeclasspath")) {
                    configuration.getResolved().forEach(module::resolved);
                }
            }
        }
    }

    Set<JavaProject> affected(Set<JavaProject> fluentProjects) {
        Set<GroupArtifact> fluentModules = new HashSet<>();
        for (JavaProject project : fluentProjects) {
            Module module = modules.get(project);
            if (module != null && module.self != null) {
                fluentModules.add(module.self);
            }
        }
        Set<JavaProject> affected = new HashSet<>(fluentProjects);
        for (Map.Entry<JavaProject, Module> module : modules.entrySet()) {
            if (!Collections.disjoint(module.getValue().resolved.keySet(), fluentModules)) {
                affected.add(module.getKey());
            }
        }
        return affected;
    }

    Map<JavaProject, String> problems(Set<JavaProject> affected) {
        Map<JavaProject, String> problems = new HashMap<>();
        for (JavaProject project : affected) {
            Module module = modules.get(project);
            if (module == null) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            for (Map.Entry<GroupArtifact, String> dependency : module.resolved.entrySet()) {
                GroupArtifact ga = dependency.getKey();
                String coordinates = ga.getGroupId() + ":" + ga.getArtifactId() + ":" + dependency.getValue();
                if (SPRING_BOOT.equals(ga) && older(dependency.getValue(), "3")) {
                    reasons.add(coordinates + " requires SLF4J 1.x; upgrade to Spring Boot 3 first");
                } else if (SLF4J_JCL.equals(ga)) {
                    reasons.add(coordinates + " has no SLF4J 2.x release");
                } else if (isLegacyProvider(ga, dependency.getValue()) && !module.declared.contains(ga)) {
                    reasons.add(coordinates + " is a SLF4J 1.x provider whose version is not declared in this build file");
                }
            }
            if (!reasons.isEmpty()) {
                Collections.sort(reasons);
                problems.put(project, "The slf4j-fluent migration requires SLF4J 2.x, but " + String.join("; ", reasons));
            }
        }
        return problems;
    }

    static boolean isBuildFile(SourceFile sourceFile) {
        return sourceFile.getMarkers().findFirst(MavenResolutionResult.class).isPresent() ||
                sourceFile.getMarkers().findFirst(GradleProject.class).isPresent();
    }

    TreeVisitor<?, ExecutionContext> visitor() {
        Map<Step<?>, TreeVisitor<?, ExecutionContext>> visitors = new HashMap<>();
        for (Step<?> step : all) {
            visitors.put(step, step.visitor());
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                for (Step<?> step : steps(s)) {
                    TreeVisitor<?, ExecutionContext> visitor = visitors.get(step);
                    if (visitor.isAcceptable(s, ctx)) {
                        s = (SourceFile) visitor.visitNonNull(s, ctx);
                    }
                }
                return s;
            }
        };
    }

    private List<Step<?>> steps(SourceFile sourceFile) {
        JavaProject project = sourceFile.getMarkers().findFirst(JavaProject.class).orElse(null);
        Module module = project == null ? null : modules.get(project);
        List<Step<?>> steps = new ArrayList<>(Arrays.asList(slf4j, log4j12, log4jBinding));
        if (module != null && module.resolved.containsKey(LOG4J_BINDING)) {
            steps.add(log4j);
        }
        if (module != null && isLegacyProvider(LOGBACK, module.resolved.get(LOGBACK))) {
            Integer java = javaVersions.get(project);
            steps.addAll(java == null || java < 11 ? logbackJava8 : logback);
        }
        return steps;
    }

    private static boolean isLegacyProvider(GroupArtifact ga, @Nullable String version) {
        if (version == null) {
            return false;
        }
        if (LOGBACK.equals(ga)) {
            return older(version, "1.3");
        }
        if (LOG4J_BINDING.equals(ga)) {
            return true;
        }
        return "org.slf4j".equals(ga.getGroupId()) && SLF4J_BINDINGS.contains(ga.getArtifactId()) && older(version, "2");
    }

    private static boolean older(String version, String than) {
        return new Version(version).compareTo(new Version(than)) < 0;
    }

    private static class Module {
        @Nullable
        GroupArtifact self;

        final Map<GroupArtifact, String> resolved = new HashMap<>();
        final Set<GroupArtifact> declared = new HashSet<>();

        void resolved(ResolvedDependency dependency) {
            resolved.merge(new GroupArtifact(dependency.getGroupId(), dependency.getArtifactId()), dependency.getVersion(),
                    (a, b) -> older(a, b) ? a : b);
        }

        void declared(@Nullable String groupId, String artifactId, @Nullable String version, Map<String, String> properties) {
            if (groupId == null || version == null) {
                return;
            }
            if (version.startsWith("${") && !properties.containsKey(version.substring(2, version.length() - 1))) {
                return;
            }
            declared.add(new GroupArtifact(groupId, artifactId));
        }
    }

    private static class Step<T> {
        private final ScanningRecipe<T> recipe;
        private final T acc;

        Step(ScanningRecipe<T> recipe, ExecutionContext ctx) {
            this.recipe = recipe;
            this.acc = recipe.getInitialValue(ctx);
        }

        TreeVisitor<?, ExecutionContext> scanner() {
            return recipe.getScanner(acc);
        }

        TreeVisitor<?, ExecutionContext> visitor() {
            return recipe.getVisitor(acc);
        }
    }
}
