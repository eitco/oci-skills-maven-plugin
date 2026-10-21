String log = new File(basedir, "build.log").text
assert log.contains("has no frontmatter")
assert !new File(basedir, "target/skill-oci/nexus-test").exists()
