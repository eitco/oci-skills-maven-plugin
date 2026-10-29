import groovy.json.JsonSlurper

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
assert json.parse(new File(skills, "gamma/.oci-skill.json")).source == "de.eitco.cicd.it:cicd-skills:tar:gamma:1.0.0"
assert !new File(skills, "by-hand/.oci-skill.json").exists()
assert new File(skills, "by-hand/SKILL.md").text.contains("A skill written by hand")

// the lock file pins version and digest of the requested skills only
String lock = new File(consumer, "skills.lock.yaml").text
assert lock.contains("id: cicd/alpha")
assert lock.contains('version: "[1.0,2.0)"')
assert lock.contains("id: cicd/beta")
assert lock.contains("status: draft")
assert lock.contains("digest: " + alpha.digest)
assert !lock.contains("gamma")

// every installed skill ignores itself in git, the install directory and the skill written by hand stay untouched
["alpha", "beta", "gamma"].each { assert new File(skills, it + "/.gitignore").readLines().contains("*") }
assert !new File(skills, ".gitignore").exists()
assert !new File(skills, "by-hand/.gitignore").exists()

// the second sync keeps the locked version, update installs the new version
int pulled = log.indexOf("Installed cicd/gamma")
int upToDate = log.indexOf("cicd/alpha 1.0.0 (published) from it-maven is up to date", pulled)
int keptAfterDeploy = log.indexOf("cicd/alpha 1.0.0 (published) from it-maven is up to date", upToDate + 1)
int updated = log.indexOf("Installed cicd/alpha 1.1.0 (published) from it-maven", keptAfterDeploy)
assert pulled > 0 && upToDate > pulled && keptAfterDeploy > upToDate && updated > keptAfterDeploy
assert log.indexOf("cicd/beta 1.0.0 (draft) from it-maven is up to date", updated) > 0
