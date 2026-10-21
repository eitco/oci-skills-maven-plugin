String log = new File(basedir, "build.log").text
assert log.contains("metadata.version is managed by the plugin")
assert !log.contains("metadata.author")
assert !new File(basedir, "target/skill-oci/nexus-test").exists()
