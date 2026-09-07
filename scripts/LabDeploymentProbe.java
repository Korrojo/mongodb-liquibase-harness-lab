import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClients;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.DirectoryResourceAccessor;
import org.bson.Document;

/** Read-only acceptance for the lab's retained fixtures, history and incremental index. */
public class LabDeploymentProbe {
    static void require(boolean ok) { if (!ok) throw new IllegalStateException("Unexpected deployment state"); }
    public static void main(String[] args) {
        try {
            require(args.length == 1 && Set.of("before", "after").contains(args[0]));
            var expected = new HashSet<String>();
            try (var resources = new DirectoryResourceAccessor(Path.of(".").toRealPath())) {
                String master = "changelog/db.changelog-master.yaml";
                var log = ChangeLogParserFactory.getInstance().getParser(master, resources).parse(master, new ChangeLogParameters(), resources);
                for (var change : log.getChangeSets()) {
                    require(expected.add(change.getId() + "|" + change.getAuthor() + "|" + change.getFilePath()));
                }
            }
            require(expected.size() >= 3);
            String password = System.getenv("ATLAS_PASSWORD");
            require(password != null && !password.isEmpty());
            Logger.getLogger("org.mongodb.driver").setLevel(Level.OFF);
            var settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?retryWrites=true&w=majority"))
                .credential(MongoCredential.createCredential("liquibase_lab_user", "admin", password.toCharArray()))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(15, TimeUnit.SECONDS))
                .applyToSocketSettings(b -> b.connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS))
                .applyToSslSettings(b -> b.enabled(true).invalidHostNameAllowed(false)).build();
            try (var client = MongoClients.create(settings)) {
                var db = client.getDatabase("liquibase_lab");
                require(Set.copyOf(db.listCollectionNames().into(new ArrayList<>())).equals(Set.of("lab_items", "DATABASECHANGELOG", "DATABASECHANGELOGLOCK")));
                var history = db.getCollection("DATABASECHANGELOG").find().sort(new Document("id", 1)).into(new ArrayList<>());
                var actual = new HashSet<String>();
                for (var row : history) {
                    require(actual.add(row.getString("id") + "|" + row.getString("author") + "|" + row.getString("fileName")));
                    require("EXECUTED".equals(row.getString("execType")) && row.getString("md5sum") != null);
                }
                require(actual.size() >= 3 && expected.containsAll(actual));
                if (args[0].equals("after")) require(actual.equals(expected));
                for (var lock : db.getCollection("DATABASECHANGELOGLOCK").find()) require(Boolean.FALSE.equals(lock.getBoolean("locked")));
                var indexes = db.getCollection("lab_items").listIndexes().into(new ArrayList<>());
                indexes.sort(java.util.Comparator.comparing(i -> i.getString("name")));
                require(indexes.stream().anyMatch(i -> "lab_sku_unique".equals(i.getString("name")) && Boolean.TRUE.equals(i.getBoolean("unique")) && new Document("sku", 1).equals(i.get("key"))));
                if (args[0].equals("after") && expected.stream().anyMatch(s -> s.startsWith("lab-004-fixture-index|"))) {
                    require(indexes.stream().anyMatch(i -> "lab_fixture_lookup".equals(i.getString("name")) && new Document("labFixture", 1).equals(i.get("key"))));
                }
                var items = db.getCollection("lab_items").find().sort(new Document("_id", 1)).into(new ArrayList<>());
                require(items.size() == 3);
                List<String> names = List.of("Synthetic notebook", "Synthetic pencil", "Synthetic folder");
                for (int i = 0; i < 3; i++) {
                    String suffix = String.format("%03d", i + 1);
                    require(items.get(i).equals(new Document("_id", "lab-" + suffix).append("sku", "LAB-" + suffix)
                        .append("name", names.get(i)).append("labFixture", "mongodb-liquibase-harness")));
                }
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((history.toString() + indexes + items).getBytes(StandardCharsets.UTF_8)));
                System.out.printf("DEPLOYMENT_STATE_PASS history=%d documents=3 fingerprint=%s%n", history.size(), fingerprint);
            }
        } catch (Exception e) {
            System.err.println("DEPLOYMENT_STATE_FAILED: " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
