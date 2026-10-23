import groovy.json.JsonSlurper

File output = new File(basedir, "target/skill-oci")
def json = new JsonSlurper()

assert output.list().sort() == ["alpha", "beta"]
["alpha": "alpha", "beta": "beta"].each { directory, name ->
    def index = json.parse(new File(output, directory + "/index.json"))
    assert index.manifests.size() == 1
    def manifest = json.parse(new File(output, directory + "/blobs/sha256/" + index.manifests[0].digest.substring(7)))
    assert manifest.annotations["org.opencontainers.image.title"] == name
}

String log = new File(basedir, "build.log").text
assert log.contains("cicd/alpha:1.0.0 (published)")
assert log.contains("cicd/beta:1.0.0 (published)")
assert log.contains("Built 2 skill images")
assert !log.contains("cicd/ignored")
