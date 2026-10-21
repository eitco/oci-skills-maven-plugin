String log = new File(basedir, "build.log").text
assert log.contains("project.build.outputTimestamp is not set")
assert !new File(basedir, "target/skill-oci/nexus-test/index.json").exists()
