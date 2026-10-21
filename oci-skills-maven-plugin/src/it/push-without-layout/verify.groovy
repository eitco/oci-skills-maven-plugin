String log = new File(basedir, "build.log").text
assert log.contains("no OCI image layout found")
assert log.contains("run oci-skills:build before oci-skills:push")
