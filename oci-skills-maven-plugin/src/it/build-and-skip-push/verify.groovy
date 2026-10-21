String log = new File(basedir, "build.log").text
assert new File(basedir, "target/skill-oci/nexus-test/index.json").isFile()
assert log.contains("skipping push")
assert !log.contains("localhost:5000/skills/nexus-test")
