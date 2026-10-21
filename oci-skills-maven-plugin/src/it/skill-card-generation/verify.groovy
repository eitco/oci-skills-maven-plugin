import groovy.json.JsonSlurper

import java.util.zip.GZIPInputStream

def json = new JsonSlurper()

def image = { String directory ->
    File layout = new File(basedir, "target/skill-oci/" + directory)
    def index = json.parse(new File(layout, "index.json"))
    def manifest = json.parse(new File(layout, "blobs/sha256/" + index.manifests[0].digest.substring(7)))
    // read skill.yaml from the layer: name at offset 0, size at offset 124
    byte[] tar = new GZIPInputStream(new FileInputStream(
            new File(layout, "blobs/sha256/" + manifest.layers[0].digest.substring(7)))).bytes
    String skillYaml = null
    String skillMd = null
    int offset = 0
    while (offset + 512 <= tar.length && tar[offset] != 0) {
        String name = new String(tar, offset, 100, "US-ASCII").replaceAll("\u0000.*", "")
        int size = Integer.parseInt(new String(tar, offset + 124, 11, "US-ASCII"), 8)
        if (name == "skill.yaml") {
            skillYaml = new String(tar, offset + 512, size, "UTF-8")
        }
        if (name == "SKILL.md") {
            skillMd = new String(tar, offset + 512, size, "UTF-8")
        }
        offset += 512 + ((size + 511).intdiv(512)) * 512
    }
    [tag: index.manifests[0].annotations["org.opencontainers.image.ref.name"], annotations: manifest.annotations,
     skillYaml: skillYaml, skillMd: skillMd]
}

def expected = [
        first : '''apiVersion: skillimage.io/v1alpha1
kind: SkillCard
metadata:
  name: first
  namespace: cicd
  version: 2.3.0-SNAPSHOT
  description: "Builds things: fast"
spec:
  prompt: SKILL.md
''',
        second: '''apiVersion: skillimage.io/v1alpha1
kind: SkillCard
metadata:
  name: second
  namespace: cicd
  version: 2.3.0-SNAPSHOT
  description: Second skill
spec:
  prompt: SKILL.md
''',
]
expected.each { skill, skillYaml ->
    def built = image(skill)
    assert new File(basedir, "target/generated-skills/" + skill + "/skill.yaml").text == skillYaml
    assert built.skillYaml == skillYaml
    assert built.tag == "2.3.0-SNAPSHOT-draft"
    assert built.annotations["org.opencontainers.image.title"] == skill
    assert built.annotations["org.opencontainers.image.vendor"] == "cicd"
    assert built.annotations["org.opencontainers.image.version"] == "2.3.0-SNAPSHOT"
    assert !new File(basedir, "src/main/skills/" + skill + "/skill.yaml").exists()
}
assert image("first").annotations["org.opencontainers.image.description"] == "Builds things: fast"

// SKILL.md: managed values added to the frontmatter, other fields and the body unchanged
def expectedSkillMd = [
        first : """---
name: first
description: "Builds things: fast"
license: MIT
metadata:
  namespace: cicd
  version: 2.3.0-SNAPSHOT
---

# First
""",
        second: """---
name: second
description: Second skill
metadata:
  namespace: cicd
  version: 2.3.0-SNAPSHOT
---

# Second
""",
]
for (def entry : expectedSkillMd) {
    assert new File(basedir, "target/generated-skills/" + entry.key + "/SKILL.md").text == entry.value
    assert image(entry.key).skillMd == entry.value
    assert !new File(basedir, "src/main/skills/" + entry.key + "/SKILL.md").text.contains("metadata")
}
