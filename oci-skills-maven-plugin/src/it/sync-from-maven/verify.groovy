import groovy.json.JsonSlurper

import java.security.MessageDigest
import java.util.zip.ZipFile

def json = new JsonSlurper()
File consumer = new File(basedir, "consumer")
File skills = new File(consumer, ".claude/skills")
String log = new File(basedir, "build.log").text

// the requested skills are installed with the generated SKILL.md and marked as managed
assert new File(skills, "alpha/SKILL.md").text.contains("name: alpha")
assert new File(skills, "alpha/skill.yaml").text.contains("namespace: cicd")
assert new File(skills, "alpha/scripts/hello.sh").canExecute()
def alpha = json.parse(new File(skills, "alpha/.oci-skill.json"))
assert alpha.id == "cicd/alpha"
assert alpha.version == "1.1.0"
assert alpha.status == "published"
assert alpha.source == "it-maven"
assert json.parse(new File(skills, "beta/.oci-skill.json")).status == "draft"

// the pulled skill is kept by the following sync, the skill written by hand is never touched
assert json.parse(new File(skills, "gamma/.oci-skill.json")).source == "de.eitco.cicd.it:cicd-skills:jar:gamma:1.0.0"
assert !new File(skills, "by-hand/.oci-skill.json").exists()
assert new File(skills, "by-hand/SKILL.md").text.contains("A skill written by hand")

// the lock file pins version and digest of the requested skills only
String lock = new File(consumer, "skills.lock.yaml").text
assert lock.contains("id: cicd/alpha")
assert lock.contains('version: "[1.0,2.0)"')
assert lock.contains("repository: it/cicd-skills")
assert lock.contains("id: cicd/beta")
assert lock.contains("status: draft")
assert lock.contains("digest: " + alpha.digest)
// the skill is pulled from a jar - its digest is the one of the manifest stored in the jar
File alphaJar = new File(localRepositoryPath, "de/eitco/cicd/it/cicd-skills/1.1.0/cicd-skills-1.1.0-alpha.jar")
byte[] alphaManifest = new ZipFile(alphaJar).withCloseable { zip ->
    zip.getInputStream(zip.getEntry("META-INF/oci-skills/alpha/manifest.json")).bytes
}
assert alpha.digest == "sha256:" + MessageDigest.getInstance("SHA-256").digest(alphaManifest).encodeHex().toString()
assert !lock.contains("gamma")

// the skill of the project solo is its main artifact - the classifier {name[:0]} is empty
def solo = json.parse(new File(skills, "solo-epsilon/.oci-skill.json"))
assert solo.id == "cicd/solo-epsilon"
assert solo.source == "it-maven-solo"
assert new File(skills, "solo-epsilon/SKILL.md").text.contains("name: solo-epsilon")
assert lock.contains("id: cicd/solo-epsilon")
assert lock.contains("digest: " + solo.digest)
File soloJar = new File(basedir, "target/skill-repository/de/eitco/cicd/it/epsilon/1.0.0/epsilon-1.0.0.jar")
byte[] soloManifest = new ZipFile(soloJar).withCloseable { zip ->
    zip.getInputStream(zip.getEntry("META-INF/oci-skills/solo-epsilon/manifest.json")).bytes
}
assert solo.digest == "sha256:" + MessageDigest.getInstance("SHA-256").digest(soloManifest).encodeHex().toString()

// the skill located by its maven coordinates is pulled from there - its location is part of the request
def delta = json.parse(new File(skills, "delta/.oci-skill.json"))
assert delta.id == "cicd/delta"
assert delta.version == "1.0.0"
assert delta.source == "de.eitco.cicd.it:cicd-skills:delta"
assert new File(skills, "delta/SKILL.md").text.contains("name: delta")
assert lock.contains("id: cicd/delta")
assert lock.contains("location: de.eitco.cicd.it:cicd-skills:delta")
assert lock.contains("digest: " + delta.digest)
assert lock.count("location:") == 1

// every installed skill ignores itself in git, the install directory and the skill written by hand stay untouched
["alpha", "beta", "gamma", "solo-epsilon", "delta"].each { assert new File(skills, it + "/.gitignore").readLines().contains("*") }
assert !new File(skills, ".gitignore").exists()
assert !new File(skills, "by-hand/.gitignore").exists()

// the second sync keeps the locked version, update installs the new version
int pulled = log.indexOf("Installed cicd/gamma")
int upToDate = log.indexOf("cicd/alpha 1.0.0 (published) from it-maven is up to date", pulled)
int keptAfterDeploy = log.indexOf("cicd/alpha 1.0.0 (published) from it-maven is up to date", upToDate + 1)
int updated = log.indexOf("Installed cicd/alpha 1.1.0 (published) from it-maven", keptAfterDeploy)
assert pulled > 0 && upToDate > pulled && keptAfterDeploy > upToDate && updated > keptAfterDeploy
assert log.indexOf("cicd/beta 1.0.0 (draft) from it-maven is up to date", updated) > 0
assert log.indexOf("cicd/delta 1.0.0 (published) from de.eitco.cicd.it:cicd-skills:delta is up to date", updated) > 0
