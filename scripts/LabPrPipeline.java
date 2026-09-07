import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs only from a pinned trusted checkout; proposed PR code is never executed. */
class LabPrPipeline {
    private static final String REPO = "Korrojo/mongodb-liquibase-harness-lab";
    private static final String BRANCH = "setup/lab-foundation";
    private static final String CONTEXT = "mongodb-lab/pr-preflight";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static String phase = "environment";

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Lab PR boundary check failed");
    }

    private static String env(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank());
        return value;
    }

    private static JsonNode api(String path, JsonNode payload) throws Exception {
        require(path.startsWith("/repos/" + REPO + "/"));
        var request = HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + env("LAB_GITHUB_TOKEN"))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "mongodb-lab-harness");
        if (payload == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
        var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != (payload == null ? 200 : 201)) {
            System.err.println("GITHUB_API_HTTP=" + response.statusCode());
        }
        require(response.statusCode() == (payload == null ? 200 : 201));
        return JSON.readTree(response.body());
    }

    private static String baseCommit(JsonNode pr, String head) {
        require("open".equals(pr.path("state").asText()));
        require(REPO.equals(pr.path("base").path("repo").path("full_name").asText()));
        require(REPO.equals(pr.path("head").path("repo").path("full_name").asText()));
        require(BRANCH.equals(pr.path("base").path("ref").asText()));
        require(head.equals(pr.path("head").path("sha").asText()));
        String base = pr.path("base").path("sha").asText();
        require(base.matches("[0-9a-f]{40}"));
        return base;
    }

    private static void checkExecutionUrl(String url) {
        require(url.startsWith("https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/")
                || url.startsWith("https://app.harness.io/ng/#/account/7WPs0XUoT4CnMpX3j28V4g/"));
    }

    private static void status(String sha, String state) throws Exception {
        String url = env("LAB_EXECUTION_URL");
        checkExecutionUrl(url);
        ObjectNode payload = JSON.createObjectNode();
        payload.put("state", state).put("context", CONTEXT).put("target_url", url);
        payload.put("description", switch (state) {
            case "pending" -> "Harness is inspecting this exact PR revision";
            case "success" -> "Trusted offline changelog preflight passed";
            default -> "Preflight failed or PR changed; inspect Harness execution";
        });
        api("/repos/" + REPO + "/statuses/" + sha, payload);
    }

    private static void run(Path directory, Path log, List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().keySet().removeIf(key -> {
            String name = key.toUpperCase();
            return name.contains("PASSWORD") || name.contains("TOKEN") || name.contains("SECRET");
        });
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IllegalStateException("Preflight subprocess timeout");
        }
        require(process.exitValue() == 0);
    }

    private static void selfTest() throws Exception {
        String sha = "a".repeat(40);
        ObjectNode pr = JSON.createObjectNode().put("state", "open");
        pr.putObject("head").put("sha", sha).putObject("repo").put("full_name", REPO);
        pr.putObject("base").put("sha", "b".repeat(40)).put("ref", BRANCH).putObject("repo").put("full_name", REPO);
        require(baseCommit(pr, sha).equals("b".repeat(40)));
        for (String scenario : List.of("closed", "head", "fork", "branch", "repository", "invalid-base")) {
            ObjectNode changed = pr.deepCopy();
            switch (scenario) {
                case "closed" -> changed.put("state", "closed");
                case "head" -> ((ObjectNode) changed.get("head")).put("sha", "c".repeat(40));
                case "fork" -> ((ObjectNode) changed.get("head").get("repo")).put("full_name", "someone/fork");
                case "branch" -> ((ObjectNode) changed.get("base")).put("ref", "other");
                case "repository" -> ((ObjectNode) changed.get("base").get("repo")).put("full_name", "someone/other");
                default -> ((ObjectNode) changed.get("base")).put("sha", "not-a-sha");
            }
            boolean rejected = false;
            try { baseCommit(changed, sha); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected);
        }
        checkExecutionUrl("https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/");
        checkExecutionUrl("https://app.harness.io/ng/#/account/7WPs0XUoT4CnMpX3j28V4g/cd/");
        boolean rejected = false;
        try { checkExecutionUrl("https://example.invalid/ng/account/7WPs0XUoT4CnMpX3j28V4g/"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected);
        System.out.println("PR_EVENT_BOUNDARY_PASS: ten metadata and URL scenarios, no network access");
    }

    public static void main(String[] args) {
        String head = null;
        boolean pending = false;
        try {
            if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
            require(args.length == 0);
            head = env("LAB_COMMIT");
            require(head.matches("[0-9a-f]{40}"));
            String number = env("LAB_PR_NUMBER");
            require(number.matches("[1-9][0-9]{0,8}"));
            Path directory = Path.of(env("LAB_RUN_DIR")).toRealPath();
            require(directory.startsWith(Path.of("/opt/mongodb-lab/work")));
            String endpoint = "/repos/" + REPO + "/pulls/" + number;
            phase = "verify-current-pr";
            String base = baseCommit(api(endpoint, null), head);
            phase = "publish-pending";
            status(head, "pending");
            pending = true;
            phase = "fetch-exact-commits";
            Path git = Files.createDirectory(directory.resolve("git"));
            run(git, directory.resolve("init.log"), List.of("git", "init", "--quiet"));
            run(git, directory.resolve("fetch.log"), List.of("git", "-c", "core.hooksPath=/dev/null", "fetch", "--quiet", "--depth=1", "https://github.com/" + REPO + ".git", base, head));
            for (String name : List.of("base", "candidate")) {
                run(git, directory.resolve(name + "-checkout.log"), List.of("git", "-c", "core.hooksPath=/dev/null", "worktree", "add", "--quiet", "--detach", directory.resolve(name).toString(), name.equals("base") ? base : head));
            }
            phase = "inspect-changelog";
            run(directory, directory.resolve("preflight.log"), List.of("java", "-Xmx256m", "-cp", "/opt/mongodb-lab/lib/*:/opt/mongodb-lab/probes", "LabChangelogCheck", directory.resolve("base").toString(), directory.resolve("candidate").toString()));
            phase = "recheck-pr-identity";
            require(base.equals(baseCommit(api(endpoint, null), head)));
            phase = "publish-success";
            status(head, "success");
            pending = false;
            System.out.printf("PR_PREFLIGHT_PASS PR=%s HEAD=%s BASE=%s; no Atlas access%n", number, head, base);
        } catch (Exception failure) {
            if (pending) {
                try { status(head, "failure"); }
                catch (Exception ignored) { System.err.println("STATUS_UPDATE_FAILED: pending status must not be treated as success"); }
            }
            System.err.println("PR_PIPELINE_FAILED phase=" + phase + " (" + failure.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }
}
