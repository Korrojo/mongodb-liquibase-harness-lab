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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/** Read-only assertions for the three isolated exercises; never repairs unexpected state. */
public class LabExerciseProbe {
    private static void require(boolean ok) {
        if (!ok) throw new IllegalStateException("Unexpected exercise state");
    }
    public static void main(String[] args) {
        try {
            require(args.length == 1);
            String password = System.getenv("ATLAS_PASSWORD");
            require(password != null && !password.isEmpty());
            if (args[0].equals("native-files")) {
                Path root = Path.of(System.getenv("LAB_RUN_DIR"));
                require(root.toString().startsWith("/opt/mongodb-lab/work/migration."));
                try (var files = Files.list(root.resolve("native-temp"))) { require(files.findAny().isEmpty()); }
                int count = 0;
                String encoded = URLEncoder.encode(password, StandardCharsets.UTF_8);
                try (var files = Files.walk(root.resolve("home/.mongodb/mongosh"))) {
                    for (var file : files.filter(Files::isRegularFile).toList()) {
                        String contents = Files.readString(file);
                        require(!contents.contains(password) && !contents.contains(encoded));
                        count++;
                    }
                }
                require(count > 0);
                System.out.println("NATIVE_EXERCISE_FILES_PASS: scripts removed; own logs contain no credentials");
                return;
            }
            int phase = Integer.parseInt(args[0]);
            require(phase >= 1 && phase <= 3);
            Logger.getLogger("org.mongodb.driver").setLevel(Level.OFF);
            var settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString("mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?retryWrites=true&w=majority"))
                    .credential(MongoCredential.createCredential("liquibase_lab_user", "admin", password.toCharArray()))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(15, TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS))
                    .applyToSslSettings(b -> b.enabled(true).invalidHostNameAllowed(false)).build();
            try (var client = MongoClients.create(settings)) {
                var db = client.getDatabase("liquibase_lab");
                var names = db.listCollectionNames().into(new ArrayList<>());
                require(Set.copyOf(names).equals(Set.of("lab_items", "DATABASECHANGELOG", "DATABASECHANGELOGLOCK")));
                var history = db.getCollection("DATABASECHANGELOG").find().sort(new Document("id", 1)).into(new ArrayList<>());
                require(history.size() == phase);
                List<String> ids = List.of("lab-001-create-collection", "lab-002-create-index", "lab-003-seed-data");
                List<String> paths = List.of("changelog/changes/001-create-collection.yaml", "changelog/changes/002-create-index.yaml", "changelog/changes/003-seed-data.yaml");
                for (int i = 0; i < phase; i++) {
                    var row = history.get(i);
                    require(ids.get(i).equals(row.getString("id")) && paths.get(i).equals(row.getString("fileName")));
                    require("Korrojo".equals(row.getString("author")) && "EXECUTED".equals(row.getString("execType")));
                    require(row.getString("md5sum") != null);
                }
                var trackingIndexes = db.getCollection("DATABASECHANGELOG").listIndexes().into(new ArrayList<>());
                require(trackingIndexes.stream().anyMatch(i -> Boolean.TRUE.equals(i.getBoolean("unique"))
                        && new Document("fileName", 1).append("author", 1).append("id", 1).equals(i.get("key"))));
                for (var lock : db.getCollection("DATABASECHANGELOGLOCK").find()) require(Boolean.FALSE.equals(lock.getBoolean("locked")));
                var indexes = db.getCollection("lab_items").listIndexes().into(new ArrayList<>());
                require(indexes.size() == (phase == 1 ? 1 : 2));
                require(indexes.stream().anyMatch(i -> "_id_".equals(i.getString("name"))));
                if (phase >= 2) require(indexes.stream().anyMatch(i -> "lab_sku_unique".equals(i.getString("name"))
                        && Boolean.TRUE.equals(i.getBoolean("unique")) && new Document("sku", 1).equals(i.get("key"))));
                var items = db.getCollection("lab_items").find().sort(new Document("_id", 1)).into(new ArrayList<>());
                require(items.size() == (phase == 3 ? 3 : 0));
                List<String> fixtureNames = List.of("Synthetic notebook", "Synthetic pencil", "Synthetic folder");
                for (int i = 0; i < items.size(); i++) {
                    String suffix = String.format("%03d", i + 1);
                    require(items.get(i).equals(new Document("_id", "lab-" + suffix).append("sku", "LAB-" + suffix)
                            .append("name", fixtureNames.get(i)).append("labFixture", "mongodb-liquibase-harness")));
                }
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest((history.toString() + indexes + items).getBytes(StandardCharsets.UTF_8)));
                System.out.printf("LAB_EXERCISE_STATE_PASS phase=%d history=%d documents=%d fingerprint=%s%n", phase, history.size(), items.size(), fingerprint);
            }
        } catch (Exception failure) {
            System.err.println("LAB_EXERCISE_STATE_FAIL: " + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
