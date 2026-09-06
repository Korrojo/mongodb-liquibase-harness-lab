import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClients;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/** Authenticated, read-only lab check. Credentials are never printed or placed in a URI. */
public class AtlasConnectivityProbe {
    public static void main(String[] args) {
        Logger.getLogger("org.mongodb.driver").setLevel(Level.OFF);
        String password = System.getenv("ATLAS_PASSWORD");
        if (password == null || password.isEmpty()) {
            System.err.println("ATLAS_CONNECTIVITY_FAIL: required secret is empty");
            System.exit(2);
        }
        char[] chars = password.toCharArray();
        try {
            var settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(
                            "mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?retryWrites=true&w=majority"))
                    .credential(MongoCredential.createCredential("liquibase_lab_user", "admin", chars))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(15, TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS))
                    .applyToSslSettings(b -> b.enabled(true).invalidHostNameAllowed(false))
                    .build();
            try (var client = MongoClients.create(settings)) {
                var db = client.getDatabase("liquibase_lab");
                db.runCommand(new Document("ping", 1));
                int count = 0;
                try (var names = db.listCollectionNames().iterator()) {
                    while (names.hasNext()) { names.next(); count++; }
                }
                System.out.println("ATLAS_CONNECTIVITY_PASS: authenticated TLS connection; database=liquibase_lab; collections=" + count);
                System.out.println("Read-only probe completed; no collections or documents were created.");
            }
        } catch (Exception failure) {
            // Class names distinguish network/authentication failures without exposing secret-bearing messages.
            System.err.println("ATLAS_CONNECTIVITY_FAIL: " + failure.getClass().getSimpleName());
            System.exit(1);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
