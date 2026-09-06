import com.mongodb.ConnectionString;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.ext.mongodb.database.MongoConnection;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.tools.MongoshRunner;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Run inside the candidate container with --network none; uses synthetic credentials only. */
class NativeFailureProbe {
    private static final String CANARY = "LAB_NATIVE_CONTAINER_CANARY_916";
    private static Path generatedScript;
    private static boolean inspected;

    public static void main(String[] args) throws Exception {
        String uri = "mongodb://offline_user:" + CANARY
                + "@127.0.0.1:1/liquibase_lab?serverSelectionTimeoutMS=500&connectTimeoutMS=500";
        MongoConnection connection = new MongoConnection();
        connection.setConnectionString(new ConnectionString(uri));
        MongoLiquibaseDatabase database = new MongoLiquibaseDatabase() {
            @Override
            public MongoConnection getConnection() { return connection; }
        };
        MongoshRunner runner = new MongoshRunner(new ChangeSet(new DatabaseChangeLog()),
                new Sql[]{new UnparsedSql("throw new Error('MIGRATION_MUST_NOT_RUN')")}) {
            @Override
            protected ProcessBuilder createProcessBuilder(Database db) {
                ProcessBuilder builder = super.createProcessBuilder(db);
                String command = String.join(" ", builder.command());
                if (command.contains(CANARY) || !builder.command().contains("--nodb")) {
                    throw new IllegalStateException("Unsafe native command arguments");
                }
                if (!uri.equals(builder.environment().get("LIQUIBASE_MONGOSH_CONNECTION_URI"))) {
                    throw new IllegalStateException("Native environment handoff is missing");
                }
                int fileIndex = builder.command().indexOf("--file");
                if (fileIndex < 0) throw new IllegalStateException("Missing native script");
                generatedScript = Path.of(builder.command().get(fileIndex + 1));
                try {
                    if (!Files.getPosixFilePermissions(generatedScript).equals(
                            PosixFilePermissions.fromString("rw-------"))) {
                        throw new IllegalStateException("Native script permissions are incorrect");
                    }
                    if (Files.readString(generatedScript).contains(CANARY)) {
                        throw new IllegalStateException("Native script contains connection credentials");
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Cannot inspect native script", e);
                }
                inspected = true;
                return builder;
            }
        };
        boolean expectedFailure = false;
        try {
            runner.executeCommand(database);
        } catch (Exception error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                String message = String.valueOf(cause.getMessage());
                if (message.contains(CANARY) || message.contains("MIGRATION_MUST_NOT_RUN")) {
                    throw new IllegalStateException("Unexpected or unsafe native failure");
                }
                expectedFailure |= message.contains("Native MongoDB connection failed");
            }
        }
        if (!inspected || !expectedFailure || generatedScript == null || Files.exists(generatedScript)) {
            throw new IllegalStateException("Native failure or immediate cleanup check did not pass");
        }
        System.out.println("NATIVE_CONTAINER_FAILURE_PASS: arguments, environment, script permissions, expected failure, cleanup");
    }
}
