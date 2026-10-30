def git = ["git", "init", "--bare", "--initial-branch=main", new File(basedir, "skills.git").absolutePath].execute()
git.waitForProcessOutput(System.out, System.err)
assert git.exitValue() == 0
return true
