import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.ext.mongodb.database.MongoClientDriver;
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.tools.MongoshRunner;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Real authenticated native read, with credential transport and cleanup assertions. No database writes. */
public class NativeAtlasProbe {
    private static Path generatedScript;
    private static boolean inspected;
    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Native acceptance assertion failed");
    }

    public static void main(String[] args) {
        MongoConnection connection = new MongoConnection();
        try {
            Logger.getLogger("org.mongodb.driver").setLevel(Level.OFF);
            String password = System.getenv("ATLAS_PASSWORD");
            require(password != null && !password.isEmpty());
            String encoded = URLEncoder.encode(password, StandardCharsets.UTF_8);
            Properties credentials = new Properties();
            credentials.setProperty("user", "liquibase_lab_user");
            credentials.setProperty("password", password);
            connection.open("mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?authSource=admin&serverSelectionTimeoutMS=15000&connectTimeoutMS=10000",
                    new MongoClientDriver(), credentials);
            credentials.clear();
            MongoLiquibaseDatabase database = new MongoLiquibaseDatabase() {
                @Override public MongoConnection getConnection() { return connection; }
            };
            String uri = connection.getConnectionString().getConnectionString();
            String script = "if (db.getName() !== 'liquibase_lab') throw new Error('Wrong database');"
                    + "if (process.env.LIQUIBASE_MONGOSH_CONNECTION_URI !== undefined) throw new Error('URI environment not cleared');"
                    + "if (db.runCommand({ping:1}).ok !== 1) throw new Error('Ping failed');"
                    + "db.getCollectionNames(); print('LIVE_NATIVE_READ_ONLY_PASS');";
            MongoshRunner runner = new MongoshRunner(new ChangeSet(new DatabaseChangeLog()),
                    new Sql[]{new UnparsedSql(script)}) {
                @Override protected ProcessBuilder createProcessBuilder(Database db) {
                    ProcessBuilder builder = super.createProcessBuilder(db);
                    String command = String.join(" ", builder.command());
                    require(!command.contains(password) && !command.contains(encoded) && !command.contains(uri));
                    require(builder.command().contains("--nodb"));
                    require(uri.equals(builder.environment().get("LIQUIBASE_MONGOSH_CONNECTION_URI")));
                    int fileIndex = builder.command().indexOf("--file");
                    require(fileIndex >= 0);
                    generatedScript = Path.of(builder.command().get(fileIndex + 1));
                    try {
                        require(Files.getPosixFilePermissions(generatedScript).equals(PosixFilePermissions.fromString("rw-------")));
                        String contents = Files.readString(generatedScript);
                        require(!contents.contains(password) && !contents.contains(encoded) && !contents.contains(uri));
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Cannot inspect native script");
                    }
                    inspected = true;
                    return builder;
                }
            };
            runner.executeCommand(database);
            require(inspected && generatedScript != null && !Files.exists(generatedScript));
            Path logs = Path.of(System.getenv("HOME"), ".mongodb", "mongosh");
            require(Files.isDirectory(logs));
            int logCount = 0;
            try (var paths = Files.walk(logs)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String contents = Files.readString(file);
                    require(!contents.contains(password) && !contents.contains(encoded) && !contents.contains(uri));
                    logCount++;
                }
            }
            require(logCount > 0);
            System.out.println("NATIVE_ATLAS_ACCEPTANCE_PASS: authenticated read, safe arguments/script, cleanup and own logs");
        } catch (Exception failure) {
            System.err.println("NATIVE_ATLAS_ACCEPTANCE_FAIL: " + failure.getClass().getSimpleName());
            System.exit(1);
        } finally {
            try { connection.close(); } catch (Exception ignored) { }
        }
    }
}
