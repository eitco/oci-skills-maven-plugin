import groovy.json.JsonSlurper

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream

File layout = new File(basedir, "target/skill-oci/nexus-test")
File blobs = new File(layout, "blobs/sha256")
def json = new JsonSlurper()

assert json.parse(new File(layout, "oci-layout")).imageLayoutVersion == "1.0.0"

def index = json.parse(new File(layout, "index.json"))
assert index.manifests.size() == 1
assert index.manifests[0].annotations["org.opencontainers.image.ref.name"] == "1.0.0"
assert blobs.listFiles().length == 3

def sha256 = { File file -> "sha256:" + MessageDigest.getInstance("SHA-256").digest(file.bytes).encodeHex().toString() }

File manifestFile = new File(blobs, index.manifests[0].digest.substring(7))
assert sha256(manifestFile) == index.manifests[0].digest
def manifest = json.parse(manifestFile)
assert manifest.mediaType == "application/vnd.oci.image.manifest.v1+json"
assert manifest.config.mediaType == "application/vnd.oci.image.config.v1+json"
assert manifest.layers.size() == 1
assert manifest.layers[0].mediaType == "application/vnd.oci.image.layer.v1.tar+gzip"
// the image is stamped with the start time of the maven build
Instant created = Instant.parse(manifest.annotations["org.opencontainers.image.created"])
assert Duration.between(created, Instant.now()) < Duration.ofMinutes(10)
assert !created.isAfter(Instant.now())
assert manifest.annotations.findAll { it.key != "org.opencontainers.image.created" } == [
        "io.skillimage.status"                : "published",
        "io.skillimage.wordcount"             : "11",
        "org.opencontainers.image.description": "Test skill for OCI registry",
        "org.opencontainers.image.title"      : "nexus-test",
        "org.opencontainers.image.vendor"     : "cicd",
        "org.opencontainers.image.version"    : "1.0.0",
]

File layer = new File(blobs, manifest.layers[0].digest.substring(7))
assert sha256(layer) == manifest.layers[0].digest
assert layer.length() == manifest.layers[0].size

// read the tar headers: name at offset 0, mode at offset 100
byte[] tar = new GZIPInputStream(new FileInputStream(layer)).bytes
def entries = [:]
def contents = [:]
int offset = 0
while (offset + 512 <= tar.length && tar[offset] != 0) {
    String name = new String(tar, offset, 100, "US-ASCII").replaceAll("\u0000.*", "")
    String mode = new String(tar, offset + 100, 7, "US-ASCII")
    long size = Long.parseLong(new String(tar, offset + 124, 11, "US-ASCII"), 8)
    entries[name] = mode
    contents[name] = new String(tar, offset + 512, (int) size, "UTF-8")
    offset += 512 + ((size + 511).intdiv(512)) * 512
}
assert entries == [
        "SKILL.md"           : "0000644",
        "references/"        : "0000755",
        "references/guide.md": "0000644",
        "scripts/"           : "0000755",
        "scripts/run.sh"     : "0000755",
        "skill.yaml"         : "0000644",
]

// the generated skill card is put into the layer
File skillCard = new File(basedir, "target/generated-skills/nexus-test/skill.yaml")
assert contents["skill.yaml"] == skillCard.text
assert skillCard.text == """apiVersion: skillimage.io/v1alpha1
kind: SkillCard
metadata:
  name: nexus-test
  namespace: cicd
  version: 1.0.0
  description: Test skill for OCI registry
spec:
  prompt: SKILL.md
"""
assert !new File(basedir, "src/main/skills/nexus-test/skill.yaml").exists()

// the generated SKILL.md with the managed values in the frontmatter is put into the layer, the source is unchanged
File skillMd = new File(basedir, "target/generated-skills/nexus-test/SKILL.md")
assert contents["SKILL.md"] == skillMd.text
assert skillMd.text == """---
name: nexus-test
description: Test skill for OCI registry
namespace: cicd
version: 1.0.0
---

# Nexus Test

This skill verifies publishing to an OCI registry.
"""
assert !new File(basedir, "src/main/skills/nexus-test/SKILL.md").text.contains("metadata")

def config = json.parse(new File(blobs, manifest.config.digest.substring(7)))
assert config.architecture == "amd64"
assert config.os == "linux"
assert config.rootfs.diff_ids.size() == 1

String log = new File(basedir, "build.log").text
assert log.contains("nexus-test:1.0.0 (published)")
assert log.contains(index.manifests[0].digest)
// push is bound to deploy and must not run in a package build
assert !log.contains("oci-skills:push") && !log.contains("push-skill-image")
