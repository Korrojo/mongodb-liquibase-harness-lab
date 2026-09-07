import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;

/** Exercise the merge boundary using public GitHub response fixtures from a real reviewed merge. */
public class TestLabDeployGuard {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var json = LabDeployGuard.JSON;
        var pr = (ObjectNode) json.readTree(root.resolve("pr.json").toFile());
        var merge = (ObjectNode) json.readTree(root.resolve("merge.json").toFile());
        var head = (ObjectNode) json.readTree(root.resolve("head.json").toFile());
        var status = (ObjectNode) json.readTree(root.resolve("status.json").toFile());
        String sha = merge.path("sha").asText();
        LabDeployGuard.verify(pr, merge, head, status, sha);
        for (String scenario : List.of("unmerged", "wrong-commit", "fork", "wrong-repository", "wrong-branch", "squash", "different-head", "changed-merge-tree", "failed-check", "missing-check")) {
            ObjectNode p = pr.deepCopy(), m = merge.deepCopy(), h = head.deepCopy(), s = status.deepCopy();
            switch (scenario) {
                case "unmerged" -> p.putNull("merged_at");
                case "wrong-commit" -> p.put("merge_commit_sha", "a".repeat(40));
                case "fork" -> ((ObjectNode) p.at("/head/repo")).put("full_name", "someone/fork");
                case "wrong-repository" -> ((ObjectNode) p.at("/base/repo")).put("full_name", "someone/other");
                case "wrong-branch" -> ((ObjectNode) p.get("base")).put("ref", "other");
                case "squash" -> m.putArray("parents").add(merge.path("parents").get(0));
                case "different-head" -> ((ObjectNode) m.at("/parents/1")).put("sha", "a".repeat(40));
                case "changed-merge-tree" -> ((ObjectNode) m.at("/commit/tree")).put("sha", "a".repeat(40));
                case "failed-check" -> { for (var check : s.path("statuses")) ((ObjectNode) check).put("state", "failure"); }
                case "missing-check" -> s.putArray("statuses");
            }
            boolean rejected = false;
            try { LabDeployGuard.verify(p, m, h, s, sha); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError(scenario);
            System.out.println("REJECTED " + scenario);
        }
        System.out.println("DEPLOY_GUARD_PASS: real approved merge and ten rejection scenarios; no Atlas access");
    }
}
