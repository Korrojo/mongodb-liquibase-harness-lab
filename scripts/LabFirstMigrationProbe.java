import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClients;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/** Narrow read-only assertions for exercise 001; never repairs or changes database state. */
public class LabFirstMigrationProbe {
    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Unexpected lab state");
    }

    public static void main(String[] args) {
        try {
            require(args.length >= 1);
            String password = System.getenv("ATLAS_PASSWORD");
            require(password != null && !password.isEmpty());
            if (args[0].equals("sanitize")) {
                require(args.length == 2);
                Path file = Path.of(args[1]);
                if (Files.size(file) > 1024 * 1024) {
                    System.out.println("Detailed log suppressed: exceeds 1 MiB.");
                    return;
                }
                String log = Files.readString(file);
                log = log.replace(URLEncoder.encode(password, StandardCharsets.UTF_8), "[REDACTED]")
                        .replace(password, "[REDACTED]")
                        .replaceAll("mongodb(?:\\+srv)?://[^\\s/@]+@", "mongodb://[REDACTED]@");
                System.out.print(log);
                return;
            }
            require(args.length == 1 && Set.of("preflight", "verify").contains(args[0]));
            Logger.getLogger("org.mongodb.driver").setLevel(Level.OFF);
            var settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(
                            "mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?retryWrites=true&w=majority"))
                    .credential(MongoCredential.createCredential("liquibase_lab_user", "admin", password.toCharArray()))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(15, TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS))
                    .applyToSslSettings(b -> b.enabled(true).invalidHostNameAllowed(false)).build();
            try (var client = MongoClients.create(settings)) {
                var db = client.getDatabase("liquibase_lab");
                var names = db.listCollectionNames().into(new ArrayList<>());
                require(Set.of("lab_items", "DATABASECHANGELOG", "DATABASECHANGELOGLOCK").containsAll(names));
                var history = db.getCollection("DATABASECHANGELOG").find().sort(new Document("id", 1)).into(new ArrayList<>());
                require(history.size() <= 1);
                for (var entry : history) {
                    require("lab-001-create-collection".equals(entry.getString("id")));
                    require("Korrojo".equals(entry.getString("author")));
                    require("changelog/changes/001-create-collection.yaml".equals(entry.getString("fileName")));
                    require("EXECUTED".equals(entry.getString("execType")));
                    require(entry.getString("md5sum") != null);
                }
                require(db.getCollection("lab_items").countDocuments() == 0);
                require(names.contains("lab_items") == (history.size() == 1));
                for (var lock : db.getCollection("DATABASECHANGELOGLOCK").find()) {
                    require(!Boolean.TRUE.equals(lock.getBoolean("locked")));
                }
                var indexes = new ArrayList<Document>();
                if (names.contains("lab_items")) {
                    db.getCollection("lab_items").listIndexes().into(indexes);
                    require(indexes.size() == 1 && "_id_".equals(indexes.get(0).getString("name")));
                }
                if (args[0].equals("verify")) require(history.size() == 1);
                String stable = history.toString() + indexes.toString();
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(stable.getBytes(StandardCharsets.UTF_8)));
                System.out.printf("LAB_FIRST_STATE_PASS history=%d documents=0 fingerprint=%s%n", history.size(), fingerprint);
            }
        } catch (Exception failure) {
            System.err.println("LAB_FIRST_STATE_FAIL: " + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
