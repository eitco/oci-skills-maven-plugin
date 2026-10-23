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

// skipped tests do not change the status
assert tag("nexus-test") == "1.0.0"
assert status("nexus-test") == "published"
assert !new File(basedir, "target/surefire-reports").exists()
assert log.contains("skipTests is set - skipping skill tests")
