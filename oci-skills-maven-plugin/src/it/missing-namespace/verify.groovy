String log = new File(basedir, "build.log").text
assert log.contains("'namespace'")
assert !new File(basedir, "target/skill-oci").exists()
