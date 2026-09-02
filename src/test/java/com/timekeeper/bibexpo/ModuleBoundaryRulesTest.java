package com.timekeeper.bibexpo;

import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the modular-monolith boundaries. Every direct sub-package of {@code com.timekeeper.bibexpo}
 * is one module; this class is the machine-readable statement of what a module may reach.
 *
 * <p>Two policies shape the rules and are deliberate, not oversights:
 * <ul>
 *   <li><b>Types are shared, behaviour is not.</b> Reading another module's {@code ..model..} or
 *       {@code ..exception..} types is legal - 15 modules read the {@code User} entity. Only
 *       behavioural edges (ports, services, components) carry direction, so the cycle and layer
 *       rules ignore type-only edges.</li>
 *   <li><b>Three back-edges are accepted, named and frozen</b> below. Every other cycle fails.</li>
 * </ul>
 */
@AnalyzeClasses(packages = ModuleBoundaryRulesTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryRulesTest {

    static final String BASE = "com.timekeeper.bibexpo";

    /**
     * The module registry, in dependency order: a module may only reach modules in a lower layer.
     * Layer 0 is the kernel; layer 7 is the composition root and the adapters nothing may reach.
     */
    private static final Map<String, Integer> LAYERS = Map.ofEntries(
            Map.entry("shared", 0),
            Map.entry("audit", 1),
            Map.entry("notification", 1),
            Map.entry("storage", 1),
            Map.entry("demo", 1),
            Map.entry("organization", 2),
            Map.entry("event", 3),
            Map.entry("user", 4),
            Map.entry("participant", 4),
            Map.entry("billing", 4),
            Map.entry("identity", 5),
            Map.entry("importer", 5),
            Map.entry("inventory", 5),
            Map.entry("messaging", 5),
            Map.entry("participantaccess", 5),
            Map.entry("reporting", 5),
            Map.entry("distribution", 6),
            Map.entry("invitation", 6),
            Map.entry("passwordreset", 6),
            Map.entry("ai", 7),
            Map.entry("bootstrap", 7));

    /**
     * Back-edges that exist today and are tolerated, as {@code "<from>-><to>"}. Each is a port call
     * that would need a design change, not a move, to remove; they are listed so a fourth one fails
     * the build instead of quietly joining them.
     */
    private static final Set<String> ACCEPTED_BACK_EDGES = Set.of(
            "organization->event",      // OrganizationServiceImpl -> EventStore
            "organization->user",       // OrganizationService(+Impl, Controller) -> CurrentActor
            "notification->user");      // NotificationRecipientResolver -> UserDirectory

    private static final String[] INTERNAL_SUFFIXES = {"repository", "service.impl", "service.cache", "store"};

    @ArchTest
    static final ArchRule every_class_belongs_to_a_declared_module = classes()
            .that().resideOutsideOfPackage(BASE)
            .should().resideInAnyPackage(modulePackages())
            .because("a new top-level package is a new module and must be declared in LAYERS");

    @ArchTest
    static final ArchRule the_shared_kernel_is_a_leaf = noClasses()
            .that().resideInAPackage(pkg("shared"))
            .should().dependOnClassesThat().resideInAnyPackage(modulePackagesExcept("shared"))
            .because("shared is the kernel every module builds on, so it may know none of them");

    @ArchTest
    static final ArchRule nothing_depends_on_the_composition_root_or_adapters = noClasses()
            .that().resideOutsideOfPackages(pkg("bootstrap"), pkg("ai"))
            .should().dependOnClassesThat().resideInAnyPackage(pkg("bootstrap"), pkg("ai"))
            .because("bootstrap wires the application and ai adapts it; both are terminal");

    @ArchTest
    static final ArchRule nothing_but_the_adapters_depends_on_reporting = noClasses()
            .that().resideOutsideOfPackages(pkg("reporting"), pkg("bootstrap"), pkg("ai"))
            .should().dependOnClassesThat().resideInAPackage(pkg("reporting"))
            .because("reporting only reads other modules; a module that read it back would invert that");

    @ArchTest
    static final ArchRule modules_are_free_of_behavioural_cycles = slices()
            .matching(BASE + ".(*)..")
            .should().beFreeOfCycles()
            .ignoreDependency(alwaysTrue(), resideInAnyPackage("..model..", "..exception.."))
            .ignoreDependency(resideInAPackage(pkg("organization")), equivalentTo(EventStore.class))
            .ignoreDependency(resideInAPackage(pkg("organization")), equivalentTo(CurrentActor.class))
            .ignoreDependency(resideInAPackage(pkg("notification")), equivalentTo(UserDirectory.class))
            .because("a cycle between modules means neither can be understood, tested or moved alone");

    @ArchTest
    static void module_internals_are_unreachable_from_outside(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            String from = moduleOf(origin);
            if (from == null) {
                continue;
            }
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                String to = moduleOf(target);
                if (to == null || to.equals(from) || !isInternal(target, to)) {
                    continue;
                }
                violations.add(from + " -> " + to + ": " + dependency.getDescription());
            }
        }
        report(violations, "Repositories, implementations, caches and stores are a module's own "
                + "business, but they are reached from outside");
    }

    @ArchTest
    static void module_dependencies_only_point_downwards(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            String from = moduleOf(origin);
            if (from == null) {
                continue;
            }
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                String to = moduleOf(target);
                if (to == null || to.equals(from) || isTypeOnly(target)
                        || ACCEPTED_BACK_EDGES.contains(from + "->" + to)) {
                    continue;
                }
                if (LAYERS.get(to) >= LAYERS.get(from)) {
                    violations.add(String.format("%s (layer %d) -> %s (layer %d): %s",
                            from, LAYERS.get(from), to, LAYERS.get(to), dependency.getDescription()));
                }
            }
        }
        report(violations, "Module dependencies must point to a lower layer");
    }

    private static void report(List<String> violations, String headline) {
        if (violations.isEmpty()) {
            return;
        }
        violations.sort(String::compareTo);
        throw new AssertionError(headline + ", but found " + violations.size() + ":\n  "
                + String.join("\n  ", violations));
    }

    private static String moduleOf(JavaClass type) {
        String name = type.getPackageName();
        if (!name.startsWith(BASE + ".")) {
            return null;
        }
        String module = name.substring(BASE.length() + 1).split("\\.")[0];
        return LAYERS.containsKey(module) ? module : null;
    }

    private static boolean isInternal(JavaClass type, String owningModule) {
        String tail = type.getPackageName().substring((BASE + "." + owningModule).length());
        for (String suffix : INTERNAL_SUFFIXES) {
            if (tail.contains("." + suffix + ".") || tail.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTypeOnly(JavaClass type) {
        String name = type.getPackageName();
        return name.contains(".model.") || name.endsWith(".model")
                || name.contains(".exception.") || name.endsWith(".exception");
    }

    private static String pkg(String module) {
        return BASE + "." + module + "..";
    }

    private static String[] modulePackages() {
        return LAYERS.keySet().stream().map(ModuleBoundaryRulesTest::pkg).toArray(String[]::new);
    }

    private static String[] modulePackagesExcept(String module) {
        return LAYERS.keySet().stream()
                .filter(other -> !other.equals(module))
                .map(ModuleBoundaryRulesTest::pkg)
                .toArray(String[]::new);
    }
}
