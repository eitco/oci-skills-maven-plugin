import groovy.json.JsonSlurper

def json = new JsonSlurper()
def index = { String skill -> json.parse(new File(basedir, "target/skill-oci/" + skill + "/index.json")) }
def tag = { String skill -> index(skill).manifests[0].annotations["org.opencontainers.image.ref.name"] }
def status = { String skill ->
    json.parse(new File(basedir, "target/skill-oci/" + skill + "/blobs/sha256/"
            + index(skill).manifests[0].digest.substring(7))).annotations["io.skillimage.status"]
}
String log = new File(basedir, "build.log").text

// the status of the frontmatter wins over the default
assert tag("alpha") == "1.0.0-draft"
assert status("alpha") == "draft"
assert log.contains("cicd/alpha:1.0.0-draft (draft)")
// without status in the frontmatter a skill is published
assert tag("beta") == "1.0.0"
assert status("beta") == "published"
assert log.contains("cicd/beta:1.0.0 (published)")
// the status stays in the packaged SKILL.md
assert new File(basedir, "target/generated-skills/alpha/SKILL.md").text.contains("status: draft")
