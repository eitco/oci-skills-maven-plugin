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

// SNAPSHOTs are published like releases, the version itself marks them
assert tag("nexus-test") == "1.0.0-SNAPSHOT"
assert status("nexus-test") == "published"

// the tests ran and are reported like surefire tests
File staticReport = new File(basedir, "target/surefire-reports/TEST-skill.nexus-test.static.xml")
File scriptReport = new File(basedir, "target/surefire-reports/TEST-skill.nexus-test.scripts.xml")
assert staticReport.text.contains('tests="5" failures="0"')
assert scriptReport.text.contains('<testcase name="greets"')
assert scriptReport.text.contains('failures="0"')

// the goals of the lifecycle run in order
def goals = [":test (default-test)", ":build (default-build)", ":archive (default-archive)", "--- install:"]
def positions = goals.collect { log.indexOf(it) }
assert !positions.contains(-1): positions
assert positions == positions.sort(false)
assert !log.contains(":push (default-push)")
assert !log.contains(":eval") && !log.contains(":qualify")

// the skills are installed as jar artifact
File installed = new File(localRepositoryPath,
        "de/eitco/cicd/it/lifecycle-snapshot/1.0.0-SNAPSHOT/lifecycle-snapshot-1.0.0-SNAPSHOT.jar")
assert installed.isFile()
assert installed.bytes == new File(basedir, "target/lifecycle-snapshot-1.0.0-SNAPSHOT.jar").bytes
def names = new java.util.zip.ZipFile(installed).withCloseable { zip -> zip.entries().toList()*.name }
assert names.contains("nexus-test/SKILL.md")
assert names.contains("nexus-test/skill.yaml")
assert names.contains("META-INF/oci-skills/nexus-test/manifest.json")
assert names.contains("META-INF/oci-skills/nexus-test/config.json")
assert !names.contains("META-INF/MANIFEST.MF")
assert names.every { !it.contains("index.json") && !it.contains("blobs/") }
