String log = new File(basedir, "build.log").text

assert log.contains("status 'released' is not supported, supported: draft, testing, published")
assert !new File(basedir, "target/skill-oci/nexus-test").exists()
