import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.DirectoryResourceAccessor;
import org.bson.Document;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Trusted PR preflight: parse files without executing JavaScript or connecting to Atlas. */
class LabChangelogCheck {
    private static final String MASTER = "changelog/db.changelog-master.yaml";
    private static final Set<String> TYPES = Set.of("createCollection", "createIndex", "runCommand", "mongoFile");

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static Path inside(Path root, Path path) throws Exception {
        Path resolved = path.toAbsolutePath().normalize();
        require(resolved.startsWith(root) && Files.isRegularFile(resolved), "Missing or outside-checkout file");
        for (Path cursor = resolved; !cursor.equals(root); cursor = cursor.getParent()) {
            require(!Files.isSymbolicLink(cursor), "Symlinks are not allowed in changelogs");
        }
        require(Files.size(resolved) <= 1_048_576, "Changelog file exceeds lab size limit");
        return resolved;
    }

    private static Map<?, ?> mapping(Object value) {
        require(value instanceof Map<?, ?>, "Expected a YAML mapping");
        return (Map<?, ?>) value;
    }

    private static List<?> sequence(Object value) {
        require(value instanceof List<?>, "Expected a YAML list");
        return (List<?>) value;
    }

    private static String text(Object value) {
        require(value instanceof String && !((String) value).isBlank(), "Expected nonempty text");
        return (String) value;
    }

    private static void only(Map<?, ?> value, Set<String> keys) {
        require(keys.containsAll(value.keySet()), "Unsupported YAML field in lab preflight");
    }

    private static List<?> document(Path root, Path path) throws Exception {
        String content = Files.readString(inside(root, path));
        require(!content.contains("${"), "Parameter substitution is not supported in this lab");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(1_048_576);
        Map<?, ?> doc = mapping(new Yaml(new SafeConstructor(options)).load(content));
        only(doc, Set.of("databaseChangeLog"));
        return sequence(doc.get("databaseChangeLog"));
    }

    private static void changes(Path root, Path file, Object value, boolean nativeExecutor) throws Exception {
        List<?> changes = sequence(value);
        require(!changes.isEmpty(), "Empty changes or rollback list");
        for (Object item : changes) {
            Map<?, ?> change = mapping(item);
            require(change.size() == 1 && TYPES.containsAll(change.keySet()), "Unsupported change type");
            String type = (String) change.keySet().iterator().next();
            Map<?, ?> spec = mapping(change.get(type));
            if (type.equals("mongoFile")) {
                only(spec, Set.of("path", "relativeToChangelogFile"));
                require(nativeExecutor && Boolean.TRUE.equals(spec.get("relativeToChangelogFile")), "mongoFile requires explicit mongosh and relative path");
                Path script = inside(root, file.getParent().resolve(text(spec.get("path"))));
                require(script.startsWith(root.resolve("changelog/scripts")) && script.toString().endsWith(".js"), "Native file must be a lab JavaScript file");
            } else {
                require(!nativeExecutor, "Native executor is reserved for mongoFile changes");
                if (type.equals("runCommand")) {
                    only(spec, Set.of("command"));
                    require(!Document.parse(text(spec.get("command"))).isEmpty(), "Empty MongoDB command");
                } else {
                    only(spec, type.equals("createIndex") ? Set.of("collectionName", "keys", "options") : Set.of("collectionName", "options"));
                    text(spec.get("collectionName"));
                    if (type.equals("createIndex")) require(!Document.parse(text(spec.get("keys"))).isEmpty(), "Missing index keys");
                    if (spec.containsKey("options")) Document.parse(text(spec.get("options")));
                }
            }
        }
    }

    private static void check(Path base, Path candidate) throws Exception {
        try (var files = Files.walk(base.resolve("changelog"))) {
            for (Path source : files.filter(path -> !Files.isDirectory(path)).toList()) {
                Path relative = base.relativize(source);
                if (relative.toString().equals(MASTER)) continue;
                require(Files.mismatch(inside(base, source), inside(candidate, candidate.resolve(relative))) == -1,
                        "Existing changelog and native files must remain byte-for-byte unchanged");
            }
        }
        List<?> before = document(base, base.resolve(MASTER));
        List<?> after = document(candidate, candidate.resolve(MASTER));
        require(after.size() >= before.size() && after.subList(0, before.size()).equals(before), "Master includes must be append-only");
        Set<String> included = new HashSet<>();
        Set<String> identities = new HashSet<>();
        int count = 0;
        for (Object entry : after) {
            Map<?, ?> includeEntry = mapping(entry);
            only(includeEntry, Set.of("include"));
            Map<?, ?> include = mapping(includeEntry.get("include"));
            only(include, Set.of("file", "relativeToChangelogFile"));
            String name = text(include.get("file"));
            require(name.matches("changes/[A-Za-z0-9_-]+\\.yaml") && included.add(name), "Invalid or duplicate include");
            require(Boolean.TRUE.equals(include.get("relativeToChangelogFile")), "Includes must be relative");
            Path file = candidate.resolve("changelog").resolve(name);
            List<?> entries = document(candidate, file);
            require(!entries.isEmpty(), "Included file has no changesets");
            for (Object item : entries) {
                Map<?, ?> entryMap = mapping(item);
                only(entryMap, Set.of("changeSet"));
                Map<?, ?> changeSet = mapping(entryMap.get("changeSet"));
                only(changeSet, Set.of("id", "author", "comment", "runWith", "changes", "rollback"));
                String identity = text(changeSet.get("id")) + "\u0000" + text(changeSet.get("author"));
                require(identities.add(identity), "Duplicate changeset identity");
                boolean nativeExecutor = changeSet.containsKey("runWith");
                require(!nativeExecutor || "mongosh".equals(changeSet.get("runWith")), "Unsupported native executor");
                changes(candidate, file, changeSet.get("changes"), nativeExecutor);
                if (changeSet.containsKey("rollback")) changes(candidate, file, changeSet.get("rollback"), nativeExecutor);
                count++;
            }
        }
        try (var resources = new DirectoryResourceAccessor(candidate)) {
            var parser = ChangeLogParserFactory.getInstance().getParser(MASTER, resources);
            var parsed = parser.parse(MASTER, new ChangeLogParameters(), resources);
            require(parsed.getChangeSets().size() == count, "Liquibase parser changeset count mismatch");
        }
        System.out.printf("PR_PREFLIGHT_PASS: %d changesets; existing files unchanged; no database or JavaScript execution%n", count);
    }

    public static void main(String[] args) {
        try {
            require(args.length == 2, "Provide base and candidate checkout paths");
            check(Path.of(args[0]).toRealPath(), Path.of(args[1]).toRealPath());
        } catch (Exception failure) {
            // Parser exception messages may include attacker-supplied text. Do not echo them into pipeline logs.
            System.err.println("PR_PREFLIGHT_FAILED: malformed, unsupported, missing, or modified changelog content (" + failure.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }
}
