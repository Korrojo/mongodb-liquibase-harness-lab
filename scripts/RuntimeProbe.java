import com.mongodb.ConnectionString;
import liquibase.change.Change;
import liquibase.change.ChangeFactory;
import liquibase.changelog.ChangeLogParameters;
import liquibase.database.DatabaseFactory;
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.DirectoryResourceAccessor;
import java.nio.file.Path;

/** Offline checks only: never opens a database connection. */
class RuntimeProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Provide the repository directory");
        var databases = DatabaseFactory.getInstance().getImplementedDatabases().stream()
                .filter(db -> db.getShortName().equals("mongodb")).toList();
        if (databases.size() != 1) throw new IllegalStateException("Expected exactly one MongoDB database implementation");
        System.out.println("MongoDB database implementation: " + databases.get(0).getClass().getName());
        for (String name : new String[]{"createCollection", "createIndex", "mongoFile"}) {
            Change change = ChangeFactory.getInstance().create(name);
            if (change == null || !change.getClass().getName().startsWith("liquibase.ext.mongodb.")) {
                throw new IllegalStateException("MongoDB change type not loaded: " + name);
            }
            System.out.println("Registered: " + name + " -> " + change.getClass().getName());
        }
        try (var resources = new DirectoryResourceAccessor(Path.of(args[0]))) {
            for (String file : new String[]{"changelog/db.changelog-master.yaml",
                    "changelog/changes/002-create-index.yaml", "changelog/changes/003-seed-data.yaml"}) {
                var parser = ChangeLogParserFactory.getInstance().getParser(file, resources);
                var changelog = parser.parse(file, new ChangeLogParameters(), resources);
                if (changelog.getChangeSets().size() != 1) throw new IllegalStateException("Unexpected changeset count: " + file);
                System.out.println("Parsed one changeset: " + file);
            }
        }
        // Fake credentials exercise visible-URL redaction without connecting.
        String canary = "OFFLINE_ONLY_password_canary_916";
        var connection = new MongoConnection();
        connection.setConnectionString(new ConnectionString("mongodb://offline_user:" + canary + "@127.0.0.1:1/liquibase_lab"));
        if (connection.getVisibleUrl().contains(canary)) throw new IllegalStateException("Visible URL exposes the password");
        System.out.println("Visible URL masks the synthetic password.");
        System.out.println("Offline runtime checks passed; Atlas and native execution remain untested.");
    }
}
