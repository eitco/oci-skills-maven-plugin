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

assert tag("nexus-test") == "1.0.0"
assert new File(basedir, "target/deployment/de/eitco/cicd/it/lifecycle-deploy/1.0.0/lifecycle-deploy-1.0.0.tar").isFile()
int push = log.indexOf(":push (default-push)")
int deploy = log.indexOf("--- deploy:")
assert push >= 0 && deploy > push
assert log.contains("skill.push.skip is set - skipping push")
assert new File(basedir, "target/deployment/de/eitco/cicd/it/lifecycle-deploy/1.0.0/lifecycle-deploy-1.0.0-nexus-test.tar").isFile()
