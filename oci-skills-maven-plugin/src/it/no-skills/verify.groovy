String log = new File(basedir, "build.log").text
assert log.contains("no skills found in")
assert !new File(basedir, "target/skill-oci").exists()
