import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Read-only GitHub checks performed by an installed, trusted class before Atlas access. */
public class LabDeployGuard {
    static final String REPO = "Korrojo/mongodb-liquibase-harness-lab";
    static final String BRANCH = "setup/lab-foundation";
    static final ObjectMapper JSON = new ObjectMapper();
    static void require(boolean ok) { if (!ok) throw new IllegalArgumentException("Unapproved merge"); }
    static JsonNode get(String suffix) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + suffix))
            .timeout(Duration.ofSeconds(30)).header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + System.getenv("LAB_GITHUB_TOKEN"))
            .header("User-Agent", "mongodb-lab-harness").GET().build();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == 200);
        return JSON.readTree(response.body());
    }
    static void verify(JsonNode pr, JsonNode merge, JsonNode head, JsonNode status, String sha) {
        require(pr.path("merged_at").isTextual());
        require(sha.equals(pr.path("merge_commit_sha").asText()));
        require(REPO.equals(pr.at("/base/repo/full_name").asText()));
        require(REPO.equals(pr.at("/head/repo/full_name").asText()));
        require(BRANCH.equals(pr.at("/base/ref").asText()));
        String reviewed = pr.at("/head/sha").asText();
        require(reviewed.matches("[0-9a-f]{40}"));
        // This lab uses normal merge commits, with an up-to-date PR branch.
        require(merge.path("parents").size() == 2);
        require(reviewed.equals(merge.at("/parents/1/sha").asText()));
        require(head.at("/commit/tree/sha").asText().matches("[0-9a-f]{40}"));
        require(head.at("/commit/tree/sha").asText().equals(merge.at("/commit/tree/sha").asText()));
        boolean passed = false;
        for (var check : status.path("statuses")) {
            if ("mongodb-lab/pr-preflight".equals(check.path("context").asText())) {
                passed = "success".equals(check.path("state").asText());
                break;
            }
        }
        require(passed);
    }
    public static void main(String[] args) {
        try {
            require(args.length == 0);
            String sha = System.getenv("LAB_COMMIT");
            require(sha != null && sha.matches("[0-9a-f]{40}"));
            require(sha.equals(get("/git/ref/heads/" + BRANCH).at("/object/sha").asText()));
            JsonNode merge = get("/commits/" + sha);
            boolean accepted = false;
            for (var pr : get("/commits/" + sha + "/pulls")) {
                if (!sha.equals(pr.path("merge_commit_sha").asText())) continue;
                String reviewed = pr.at("/head/sha").asText();
                require(reviewed.matches("[0-9a-f]{40}"));
                verify(pr, merge, get("/commits/" + reviewed), get("/commits/" + reviewed + "/status"), sha);
                System.out.printf("APPROVED_MERGE PR=%d COMMIT=%s%n", pr.path("number").asInt(), sha);
                accepted = true;
                break;
            }
            require(accepted);
        } catch (Exception e) {
            System.err.println("DEPLOYMENT_REJECTED: stale, unrelated, unreviewed or unverifiable commit (" + e.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }
}
