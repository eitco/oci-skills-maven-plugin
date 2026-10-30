File repository = new File(basedir, "skills.git")
def git = { String... args ->
    def process = (["git", "--git-dir=" + repository.absolutePath] + args.toList()).execute()
    def out = new StringBuilder()
    def err = new StringBuilder()
    process.waitForProcessOutput(out, err)
    assert process.exitValue() == 0: err.toString()
    return out.toString().trim()
}
String log = new File(basedir, "build.log").text

// the release is committed once and tagged
assert git("rev-list", "--count", "main") == "1"
assert git("rev-parse", "git-deploy-1.0.0^{commit}") == git("rev-parse", "main")
assert git("cat-file", "-t", "git-deploy-1.0.0") == "tag"
String message = git("log", "-1", "--format=%B", "main")
assert message.startsWith("release git-deploy 1.0.0")
assert message =~ /nexus-test sha256:[0-9a-f]{64}/

// the files of the image - including the generated skill.yaml - with their executable bits, the draft is skipped
List<String> files = git("ls-tree", "-r", "--name-only", "main").readLines()
assert files.contains("skills/nexus-test/SKILL.md")
assert files.contains("skills/nexus-test/references/guide.md")
assert files.contains("skills/nexus-test/skill.yaml")
assert files.every { !it.startsWith("skills/draft-skill/") }
assert git("ls-tree", "main", "skills/nexus-test/scripts/run.sh").startsWith("100755")
assert git("show", "main:skills/nexus-test/skill.yaml").contains("version: 1.0.0")

// the second run changes nothing and only warns about the existing tag
assert log.contains("Tagged ")
assert log.contains("[WARNING] The tag git-deploy-1.0.0 exists already")
assert log.contains("The skills are unchanged")
