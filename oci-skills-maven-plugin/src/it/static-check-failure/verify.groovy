import groovy.json.JsonSlurper

def json = new JsonSlurper()
def layout = { String skill -> new File(basedir, "target/skill-oci/" + skill) }
def index = { String skill -> json.parse(new File(layout(skill), "index.json")) }
def tag = { String skill -> index(skill).manifests[0].annotations["org.opencontainers.image.ref.name"] }
def manifest = { String skill ->
    json.parse(new File(layout(skill), "blobs/sha256/" + index(skill).manifests[0].digest.substring(7)))
}
def status = { String skill -> manifest(skill).annotations["io.skillimage.status"] }
String log = new File(basedir, "build.log").text

assert log.contains("SKILL.md: link to references/missing.md - the file does not exist")
assert new File(basedir, "target/surefire-reports/TEST-skill.nexus-test.static.xml").text.contains("<failure")
assert !layout("nexus-test").exists()
