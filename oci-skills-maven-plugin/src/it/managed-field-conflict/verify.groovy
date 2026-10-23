String log = new File(basedir, "build.log").text
assert log.contains("SKILL.md frontmatter: version is managed by the plugin")
// metadata belongs to the author of the skill, even if a key is named like a managed field
assert !log.contains("metadata.version")
assert !log.contains("metadata.author")
assert !new File(basedir, "target/skill-oci/nexus-test").exists()
