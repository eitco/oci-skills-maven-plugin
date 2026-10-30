# oci-skills-maven-plugin

A maven plugin that builds AI skills into OCI images and pushes them to OCI registries such as Sonatype Nexus
Repository. The image format is compatible with [skillctl](https://github.com/redhat-et/skillimage), but `skillctl`,
docker or podman are not needed.

With the packaging `skill` the plugin brings its own build lifecycle - skills are tested, built and pushed on
`deploy`:

```text
src/main/skills/<skill>/  --oci-skills:build-->  target/skill-oci/<skill>/ (OCI image layout)  --oci-skills:push-->  registry
```

## Modules

| module                      | content                                                                    |
|-----------------------------|----------------------------------------------------------------------------|
| `oci-skills-core`           | SkillCard validation, deterministic tar layer, OCI config/manifest/layout, skill jars, installing skills and the lock file |
| `oci-skills-registry-oras`  | `OciRegistryClient` implementation based on the ORAS java SDK - push and pull |
| `oci-skills-git-jgit`       | `SkillRepositoryPublisher` implementation based on JGit - publishing skills to git repositories |
| `oci-skills-maven-plugin`   | the goals and the lifecycle of the packaging `skill`                       |

The core has no dependency on maven, nexus, ORAS or JGit.

## Usage

### Packaging `skill`

```xml
<packaging>skill</packaging>

<build>
    <plugins>
        <plugin>
            <groupId>de.eitco.cicd.ociskills</groupId>
            <artifactId>oci-skills-maven-plugin</artifactId>
            <version>${oci-skills-maven-plugin.version}</version>
            <extensions>true</extensions>
            <configuration>
                <registry>registry.example.com</registry>
                <repository>skills/acme</repository>
                <registryServerId>my-registry</registryServerId>
                <namespace>acme</namespace>
            </configuration>
        </plugin>
    </plugins>
</build>
```

`<extensions>true</extensions>` is required - it makes the packaging known to maven. The lifecycle binds:

| phase              | goals                                                                  |
|--------------------|------------------------------------------------------------------------|
| `test`             | `oci-skills:test` - static checks and script tests                     |
| `package`          | `oci-skills:build`                                                     |
| `verify`           | `oci-skills:archive`                                                   |
| `install`          | `maven-install-plugin:install`                                         |
| `deploy`           | `oci-skills:push`, `oci-skills:git-deploy`, then `maven-deploy-plugin:deploy` |

`oci-skills:archive` packs all skills into the [skill jar](#skill-jar) `target/<finalName>.jar`, the main artifact of
the project - so `install` and `deploy` put the skills into maven repositories as well (type `skill`, extension
`jar`). Every skill is attached on its own as well (type `jar`), so it can be [installed](#installing-skills) by other
projects - its classifier is the skill name, suffixed like the tag, e.g. `nexus-test` or `nexus-test-draft`. Local
`mvn install` calls never push, since the push is bound to `deploy`. `-Dskill.push.skip=true` skips the push in any
case.

> **Changed in 0.0.3:** up to 0.0.2 the main artifact was a tar file containing the OCI image layouts. It is a jar
> with the skill files now - artifacts of 0.0.2 cannot be installed with `oci-skills:sync` or `oci-skills:pull`.

### Status

The status of a skill image is part of its tag and of the annotation `io.skillimage.status`. A skill is `published`
unless the frontmatter of its `SKILL.md` sets `status`:

```markdown
---
name: nexus-test
description: Test skill for OCI registry
status: draft
---
```

| `status` in the frontmatter | tag                 |
|-----------------------------|---------------------|
| - (default) or `published`  | `<version>`         |
| `testing`                   | `<version>-testing` |
| `draft`                     | `<version>-draft`   |

Other values are a validation error. SNAPSHOT versions are used as they are, e.g. the tag of `1.0.0-SNAPSHOT` is
`1.0.0-SNAPSHOT`, with status `draft` `1.0.0-SNAPSHOT-draft`.

### Testing skills

The tests of a skill are located in `src/test/skills/<skill>/` - next to the skill, not in it, so they are not part of
the image. Failing tests fail the build.

**Static checks** run for every skill without configuration and are reported as test cases of
`skill.<skill>.static`:

| check         | fails on                                                                                         |
|---------------|--------------------------------------------------------------------------------------------------|
| `frontmatter` | the rules for `SKILL.md` described below                                                         |
| `links`       | relative links and images in any `*.md` pointing to missing or excluded files or out of the skill |
| `scripts`     | executable text files in `scripts/` without shebang - not executable files are a warning         |
| `orphans`     | warning only: files no markdown file links to or mentions                                       |
| `size`        | warning only: `SKILL.md` with more than 500 lines                                               |

`-Dskill.test.failOnWarning=true` lets warnings fail as well.

**Script tests** in `tests.yaml` run commands against a copy of the files that go into the image (including the
executable bits). The working directory is the copied skill, which is also available as `$SKILL_DIR`:

```yaml
tests:
  - name: hello greets
    command: [scripts/hello.sh, world]   # a first element containing '/' is relative to the skill
    stdin: ""                            # optional
    env: { GREETING: Hello }             # optional
    timeout: 30s                         # optional, default 60s
    exitCode: 0                          # default 0
    stdout: { contains: [Hello], matches: ["^Hello, world$"], notMatches: [error] }
    stderr: { empty: true }
```

Static checks and script tests are reported in `target/surefire-reports` in the format of the surefire plugin, so CI
servers show them like any other test.

### Goals without packaging `skill`

The goals can be bound in any project as well:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>de.eitco.cicd.ociskills</groupId>
            <artifactId>oci-skills-maven-plugin</artifactId>
            <version>${oci-skills-maven-plugin.version}</version>
            <configuration>
                <registry>registry.example.com</registry>
                <repository>skills/acme</repository>
                <registryServerId>my-registry</registryServerId>
                <namespace>acme</namespace>
            </configuration>
            <executions>
                <execution>
                    <id>build-skill-image</id>
                    <goals>
                        <goal>build</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Bound in such a project, `oci-skills:archive` attaches the jar of all skills with classifier `skills` and every skill
with its own classifier, like with packaging `skill`.

### Skill directories

Every direct subdirectory of `src/main/skills` is a skill directory - hidden directories (`.` prefix) and files are
ignored. All skills are built and pushed by a single execution. A skill directory follows the
[agent skills](https://agentskills.io) format: it contains a `SKILL.md` with a YAML frontmatter holding at least
`name` and `description`, plus any further files, e.g. `src/main/skills/nexus-test/SKILL.md`:

```markdown
---
name: nexus-test
description: Test skill for OCI registry
---

# Nexus Test

This skill verifies publishing to an OCI registry.
```

* `name` must be a DNS label (lower case alphanumerics and `-`, at most 63 characters) and equal the directory name.
* `description` must not be empty and has at most 1024 characters.
* `status` is optional and one of `draft`, `testing` or `published`, see [Status](#status).
* further frontmatter fields (`license`, `allowed-tools`, ...) are allowed and put into the image unchanged.

`mvn package` creates one OCI image layout per skill in `target/skill-oci/<skill directory name>`. With the
configuration above and project version `0.0.1` `mvn oci-skills:push` pushes
`registry.example.com/skills/acme/nexus-test:0.0.1`.

### Generated files

Values that are the same for all skills of a build - `namespace` and `version` - are configured in the plugin only
and can be inherited from a parent POM. The plugin generates two files per skill into
`target/generated-skills/<skill>/` and puts them into the image instead of the source files; the sources are never
modified.

The image format requires a SkillCard (`skill.yaml`, `skillimage.io/v1alpha1`). It is not maintained by hand - a
`skill.yaml` in the skill directory is a validation error:

```yaml
apiVersion: skillimage.io/v1alpha1
kind: SkillCard
metadata:
  name: nexus-test                          # SKILL.md frontmatter
  namespace: acme                           # <namespace>
  version: 0.0.1                            # <version>, default ${project.version}
  description: Test skill for OCI registry  # SKILL.md frontmatter
spec:
  prompt: SKILL.md
```

The packaged `SKILL.md` shows the managed values as well - as top level fields of the frontmatter:

```markdown
---
name: nexus-test
description: Test skill for OCI registry
namespace: acme
version: 0.0.1
---

# Nexus Test
...
```

The managed fields are appended, other frontmatter fields - including `metadata` - are kept in their order,
everything after the closing `---` is kept byte by byte. The frontmatter is written anew, so comments in it are not
kept. `namespace` and `version` must not be set in the source `SKILL.md`; entries of `metadata` with these names are
the author's own and kept unchanged. A `status` in the frontmatter is kept like any other field.

### Pushing only in designated builds

Without the packaging `skill`, local `mvn package` or `mvn install` calls must never publish either. Bind
`oci-skills:push` in a profile that is only activated by the CI pipeline:

```xml
<profiles>
    <profile>
        <id>publish-skill</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>de.eitco.cicd.ociskills</groupId>
                    <artifactId>oci-skills-maven-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>push-skill-image</id>
                            <phase>deploy</phase>
                            <goals>
                                <goal>push</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

`mvn deploy -Ppublish-skill` builds and pushes. `-Dskill.push.skip=true` skips the push in any case.

### Credentials

Credentials are never configured in the POM. They are taken from the `<server>` with the id `registryServerId` in
`settings.xml` - encrypted passwords are supported:

```xml
<servers>
    <server>
        <id>my-registry</id>
        <username>${env.REGISTRY_USERNAME}</username>
        <password>${env.REGISTRY_PASSWORD}</password>
    </server>
</servers>
```

Without `registryServerId` the push is anonymous.

### Using skills with `npx skills`

[`npx skills`](https://github.com/vercel-labs/skills) installs skills from git repositories. `oci-skills:git-deploy`
publishes the built skills into such a repository - configured with `gitUrl`, otherwise the goal does nothing:

```xml
<configuration>
    <gitUrl>https://git.example.com/ai/skills.git</gitUrl>
    <gitServerId>my-git</gitServerId>
</configuration>
```

On `deploy` the goal clones the branch `main`, replaces the directories `skills/<skill>/` with the files of the images -
including the generated `skill.yaml` and the executable bits - and commits if anything changed. All other files of the
repository are kept, so several projects can publish into the same repository. The commit is tagged with
`<artifactId>-<version>`, so consumers can pin a release:

```shell
npx skills add https://git.example.com/ai/skills.git#acme-skills-1.2.0 --skill nexus-test   # pinned
npx skills add https://git.example.com/ai/skills.git --skill nexus-test                     # follows main
npx skills update                                                                           # for unpinned skills
```

On self-hosted git servers the url has to end with `.git` - otherwise `npx skills` does not take `#<tag>` as tag.

* Only skills with status `published` are deployed - `<gitStatuses>` selects others.
* Snapshots are only deployed if `gitSnapshotBranch` is configured - to that branch and without tag.
* If the tag exists already - e.g. a release build is repeated - it is kept unchanged with a warning, and the skills are
  committed to the branch anyway.
* A push rejected because another build pushed in the meantime is repeated on a fresh clone, up to `gitAttempts` times.
* The credentials are taken from the `<server>` with the id `gitServerId` in `settings.xml` - user name and password or
  access token. Only `https` and `file` urls are supported, no ssh.
* `-Dskill.git.skip=true` skips the goal. It is independent of `skill.push.skip`: a project publishing to git only sets
  `-Dskill.push.skip=true`.

### Installing skills

Projects using skills do not need to commit them - `oci-skills:sync` installs them from maven repositories or OCI
registries into `.claude/skills`:

```xml
<plugin>
    <groupId>de.eitco.cicd.ociskills</groupId>
    <artifactId>oci-skills-maven-plugin</artifactId>
    <version>${oci-skills-maven-plugin.version}</version>
    <configuration>
        <sources>
            <source>
                <id>my-maven</id>
                <type>maven</type>
                <groupId>com.example.{repository.parent}</groupId>
                <artifactId>{repository.last}</artifactId>
            </source>
            <source>
                <id>my-registry</id>
                <type>oci</type>
                <registry>registry.example.com</registry>
                <registryServerId>my-registry</registryServerId>
            </source>
        </sources>
        <skills>
            <skill>
                <id>acme/nexus-test</id>
                <version>[1.0,2.0)</version>
                <repository>teams/acme/skills</repository>
            </skill>
            <skill>
                <id>acme/release-notes</id>
                <version>2.1.0</version>
                <status>testing</status>
                <repository>products/notes/skills</repository>
            </skill>
        </skills>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>sync</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

A skill is identified by `<namespace>/<name>` of its SkillCard, independent of where it is stored. Where it is
stored is the `<repository>` of the `<skill>` - the OCI repository path it was pushed below, so a single source
covers a whole registry or maven repository. Every source maps it to its own coordinates:

| `type`  | the skill is                                                             | as created by        |
|---------|--------------------------------------------------------------------------|----------------------|
| `maven` | the artifact `<groupId>:<artifactId>:jar:<classifier>[-<status>]:<version>` | `oci-skills:archive` |
| `oci`   | the image `<registry>/<repository>/<name>:<version>[-<status>]`          | `oci-skills:push`    |

For OCI sources the `<repository>` of the skill is used as it is. `groupId`, `artifactId` and `classifier` of maven
sources - and the `<repository>` of OCI sources, which is used for skills without `<repository>` - are templates with
these placeholders, shown for `acme/nexus-admin-test` with the repository `teams/acme/skills`:

| placeholder                              | value                       | from                                     |
|------------------------------------------|-----------------------------|------------------------------------------|
| `{namespace}`                            | `acme`                      | the skill id                             |
| `{name}`                                 | `nexus-admin-test`          | the skill id                             |
| `{name[0]}`, `{name[1]}`, `{name[-1]}`   | `nexus`, `admin`, `test`    | a single part of the name                |
| `{name[1:]}`                             | `admin-test`                | the name without its first part          |
| `{name[:-1]}`                            | `nexus-admin`               | the name without its last part           |
| `{name[0:2]}`                            | `nexus-admin`               | the parts 0 and 1 of the name            |
| `{name[:0]}`                             | empty                       | no part at all                           |
| `{namespace[…]}`                         |                             | parts of the namespace, like `{name[…]}` |
| `{repository}`                           | `teams.acme.skills`         | the repository, `/` replaced by `.`      |
| `{repository[1:]}`                       | `acme.skills`               | parts of the repository, like `{name[…]}`|
| `{repository.parent}`, `{repository[:-1]}` | `teams.acme`              | the repository without its last segment  |
| `{repository.last}`, `{repository[-1]}`  | `skills`                    | the last segment of the repository       |

Namespace and name are split into parts at `-` and joined with `-`, the repository is split at `/` and joined with
`.`. Indices start at 0, negative indices count from the end, the end of a slice `[from:to]` is exclusive and both
bounds may be omitted - like in Python. A single index that does not exist fails, slices are cut to the existing
parts and may be empty. Repeated dots as well as dots and dashes at the start or end of the value are dropped -
`com.example.{repository.parent}` with the repository `skills` is `com.example`, `skills-{name[1:]}` for the skill
`acme/solo` is `skills`.

`classifier` defaults to `{name}`, the classifier `oci-skills:archive` attaches a skill with. Unless the status is
`published` it is suffixed with the status, like the tag of an image. With the example above `acme/nexus-admin-test`
is the artifact `com.example.teams.acme:skills:jar:nexus-admin-test` and the image
`registry.example.com/teams/acme/skills/nexus-admin-test`. A source that cannot locate a skill fails the build - e.g.
a template with a repository placeholder for a skill without `<repository>`, a `groupId` or `artifactId` expanding to
an empty value, or an OCI source without `<repository>`. Pin such skills to another source with `<source>`.

Different layouts are covered by splitting the name - the skill `acme/tools-delta` as `com.example.acme:tools:jar:delta`:

```xml
<source>
    <id>my-tools</id>
    <type>maven</type>
    <groupId>com.example.{namespace}</groupId>
    <artifactId>{name[0]}</artifactId>
    <classifier>{name[1:]}</classifier>
</source>
```

**One skill per project.** With packaging `skill` the main artifact of a project contains all of its skills - with a
single skill it can be pulled like the attached jar. A classifier expanding to an empty value selects the main
artifact; since `<classifier/>` is no value in maven, write `{name[:0]}`. The main artifact has no status in its
coordinates: it is taken only if the skill in it has the requested status. The skill `acme/solo-epsilon` as the main
artifact `com.example.acme:epsilon:jar`:

```xml
<source>
    <id>my-projects</id>
    <type>maven</type>
    <groupId>com.example.{namespace}</groupId>
    <artifactId>{name[1:]}</artifactId>
    <classifier>{name[:0]}</classifier>
</source>
```

**Default repository.** There is no plugin parameter for it - two ways cover it:

* *Per source*: skills without `<repository>` are located by the templates of the source alone, so these are the
  default of the source. A template without repository placeholders needs no `<repository>` - a fixed repository
  would only expand to fixed values, so write them into the template directly:

  ```xml
  <source>
      <id>my-registry</id>
      <type>oci</type>
      <registry>registry.example.com</registry>
      <repository>teams/{namespace}/skills</repository>
  </source>
  <source>
      <id>my-maven</id>
      <type>maven</type>
      <groupId>com.example.{namespace}</groupId>
      <artifactId>skills</artifactId>
  </source>
  ```

* *For all skills*: a property - which may be inherited from a parent POM - used as `<repository>` of every skill.
  Each source maps it to its own coordinates. Changing it resolves the skills again, as it is part of `requested`
  in the lock file:

  ```xml
  <properties>
      <skills.repository>teams/acme/skills</skills.repository>
  </properties>
  ...
  <skill>
      <id>acme/nexus-test</id>
      <version>[1.0,2.0)</version>
      <repository>${skills.repository}</repository>
  </skill>
  ```

Do not put `<repository>` into the plugin level `<configuration>`: it is the parameter of `oci-skills:push` and
`oci-skills:pull`, so it would set the push target as well.

Maven sources use the repositories, mirrors and credentials of the build, including the local repository and the
reactor. OCI sources take their credentials from the `<server>` with the id `registryServerId` - the versions of a
skill are taken from the tags of `<registry>/<repository>/<name>`, so the credentials must be allowed to list them.

Every `<source>` needs an `id` unique among the sources. Other placeholders are a configuration error.

**Direct location.** A `<skill>` may name where it is stored instead of using the sources - an OCI image without tag
or the coordinates of a maven artifact. It is resolved and fetched from there only, the sources are not asked, so no
`<sources>` are needed at all:

```xml
<skill>
    <id>acme/nexus-test</id>
    <version>[1.0,2.0)</version>
    <!-- the tags <version>[-<status>] of this repository -->
    <image>registry.example.com/teams/acme/skills/nexus-test</image>
</skill>
<skill>
    <id>acme/release-notes</id>
    <version>2.1.0</version>
    <!-- com.example.acme:acme-skills:jar:release-notes:2.1.0 -->
    <groupId>com.example.acme</groupId>
    <artifactId>acme-skills</artifactId>
</skill>
```

* `<image>` is `<registry>/<repository>` - literal, without tag or digest. The tag is the version suffixed with the
  status unless the status is `published`. `registryServerId`, `allowPlainHttp` and `caCertificates` of the `<skill>`
  take precedence; settings not given are taken from the first `oci` source with the same registry - without one the
  registry is accessed anonymously via TLS trusting the CAs of the JVM only.
* `<groupId>` and `<artifactId>` go together, `<classifier>` defaults to `{name}` and is suffixed with the status
  unless the status is `published` - like for maven sources. All three may contain the placeholders of the skill id,
  `<classifier>{name[:0]}</classifier>` selects the main artifact. Repository placeholders fail - there is no
  `<repository>`.
* `id` and `version` are still required: the image or artifact has to contain the skill `id`, it is installed into
  `.claude/skills/<name>`.
* A direct location cannot be combined with `<source>` or `<repository>`, `<image>` not with `<groupId>`/`<artifactId>`;
  `registryServerId`, `allowPlainHttp` and `caCertificates` need `<image>`, `classifier` needs `<groupId>`.

The `source` in the lock file and in `.oci-skill.json` is `<groupId>:<artifactId>[:<classifier>]` (expanded, without
status and without classifier for the main artifact) or `<registry>/<repository>`. It is locked as
`requested.location` - changing the location resolves the skill again:

```yaml
- id: acme/release-notes
  requested:
    version: 2.1.0
    status: published
    location: com.example.acme:acme-skills:release-notes
  version: 2.1.0
  status: published
  digest: sha256:9c1e...
  source: com.example.acme:acme-skills:release-notes
```

**Resolving.** `version` is a maven version range or an exact version - unlike maven, `1.2.0` matches only `1.2.0`.
`status` is the least mature status accepted, default `published`. The sources are asked in their order, the first
source knowing a matching version wins, `<source>` in a `<skill>` asks that source only. The highest matching version
with an accepted status is taken - if a version has several statuses, the most mature one. The image has to contain
the requested skill - namespace and name of its annotations must match the `id`.

**Lock file.** The resolved version and the digest of the image are written to `skills.lock.yaml` next to the POM.
Commit it: as long as the request of a skill does not change, `sync` installs exactly the locked image - newer versions
are only taken by `mvn oci-skills:update` (all skills) or `mvn oci-skills:update -Dskill=nexus-test`. A locked image
may come from any source, e.g. from the maven repository though it was resolved from the registry - the artifact and
the image of the same build have the same digest. The source it was resolved from is asked first, then all others. A
source delivering another digest fails the build - run `oci-skills:update` if the skill was rebuilt intentionally.

```yaml
# generated by oci-skills-maven-plugin - commit it, update it with mvn oci-skills:update
skills:
- id: acme/nexus-test
  requested:
    version: "[1.0,2.0)"
    status: published
    repository: teams/acme/skills
  version: 1.1.0
  status: published
  digest: sha256:4f2a...
  source: my-maven
```

`requested` is the request the entry was resolved for - a request with another `version`, `status`, `repository`,
`location` or `source` is resolved again. `repository` and `location` are only written for skills requested with
one. The entries are sorted by id and the file is only written if its content changes. An invalid lock file
fails the build - fix it, or delete it and run `oci-skills:update`.

**Installed skills.** Every skill is installed into `.claude/skills/<name>/` and marked with `.oci-skill.json`. Only
marked directories are replaced or removed, so skills written by hand can live in the same directory - a requested
skill with the name of such a directory fails the build. Installed skills are up to date if their digest is the locked
one - `sync` does not even access the sources then. Skills of the lock file that are no longer requested are removed.
Every installed skill contains a `.gitignore` with `*`, so it is never checked in - `.claude/skills/.gitignore`
is not touched. `.oci-skill.json` records where the skill comes from - `id`, `version`, `status`, `digest` and the
`source`, i.e. the id of the source or, for `oci-skills:pull`, the artifact coordinates or image reference.

Every skill is installed into the directory of its name, so a skill requested twice and two skills with the same name
in different namespaces fail the build.

`sync` is not bound by any lifecycle, its default phase is `initialize` - add an execution as shown above to run it
with every build. A single skill can be installed from the command line without lock file - `sync` keeps it:

```shell
mvn oci-skills:pull -Dskill=acme/nexus-test             # highest version from the configured sources
mvn oci-skills:pull -Dskill=acme/nexus-test:1.2.0 -Dskill.pull.repository=teams/acme/skills
mvn oci-skills:pull -Dskill.artifact=com.example.acme:acme-skills:1.2.0:nexus-test
mvn oci-skills:pull -Dskill.artifact=com.example.acme:epsilon:1.2.0   # the main artifact with a single skill
mvn oci-skills:pull -Dskill.image=registry.example.com/skills/acme/nexus-test:1.2.0
```

`-Dskill.artifact` and `-Dskill.image` work without project.

## Goals

### `oci-skills:build` (default phase `package`)

| parameter         | property                | default                                 | description                                                           |
|-------------------|-------------------------|-----------------------------------------|-----------------------------------------------------------------------|
| `skillsDirectory` | `skill.skillsDirectory` | `${project.basedir}/src/main/skills`    | directory with one subdirectory per skill                             |
| `outputDirectory` | `skill.outputDirectory` | `${project.build.directory}/skill-oci`  | the OCI image layouts are written to `<outputDirectory>/<skill>`      |
| `includes`        |                         | `**/*`                                  | ant patterns of files to put into the layer, relative to each skill   |
| `excludes`        |                         | `.git/**`, `target/**`                  | ant patterns of files to leave out - replaces the defaults if set     |
| `executables`     |                         |                                         | ant patterns of files written with mode `0755`                        |
| `namespace`       | `skill.namespace`       | - (required)                            | namespace of all skills - a DNS label                                 |
| `version`         | `skill.version`         | `${project.version}`                    | version of all skills, determines the tag                             |
| `generatedSkillsDirectory` | `skill.generatedSkillsDirectory` | `${project.build.directory}/generated-skills` | the generated `skill.yaml` and `SKILL.md` are written to `<dir>/<skill>/` |

### `oci-skills:push` (default phase `deploy`)

| parameter            | property                       | default                                | description                                          |
|----------------------|--------------------------------|----------------------------------------|------------------------------------------------------|
| `skillsDirectory`    | `skill.skillsDirectory`        | `${project.basedir}/src/main/skills`   | directory with one subdirectory per skill            |
| `outputDirectory`    | `skill.outputDirectory`        | `${project.build.directory}/skill-oci` | the OCI image layouts created by `oci-skills:build`  |
| `registry`           | `skill.registry`               |                                        | registry host, optionally with port                  |
| `repository`         | `skill.repository`             |                                        | repository path, the skill name is appended          |
| `registryServerId`   | `skill.registryServerId`       |                                        | id of the `<server>` in `settings.xml`               |
| `verifyDigest`       | `skill.push.verifyDigest`      | `true`                                 | fail if the registry confirms a different digest     |
| `caCertificates`     |                                |                                        | PEM files of additional trusted CAs                  |
| `trustStore`         | `skill.push.trustStore`        |                                        | trust store with additional trusted CAs              |
| `trustStorePassword` | `skill.push.trustStorePassword`|                                        | password of the trust store                          |
| `trustStoreType`     | `skill.push.trustStoreType`    | JVM default                            | type of the trust store, e.g. `PKCS12`               |
| `allowPlainHttp`     | `skill.push.allowPlainHttp`    | `false`                                | connect without TLS - for local test registries only |
| `skip`               | `skill.push.skip`              | `false`                                | skip the push                                        |

Additional CAs are trusted in addition to the JVM defaults. TLS verification cannot be disabled.

### `oci-skills:git-deploy` (default phase `deploy`)

| parameter            | property                       | default                                         | description                                        |
|----------------------|--------------------------------|-------------------------------------------------|----------------------------------------------------|
| `skillsDirectory`    | `skill.skillsDirectory`        | `${project.basedir}/src/main/skills`            | directory with one subdirectory per skill          |
| `outputDirectory`    | `skill.outputDirectory`        | `${project.build.directory}/skill-oci`          | the OCI image layouts created by `oci-skills:build` |
| `gitUrl`             | `skill.git.url`                |                                                 | url of the repository - without it the goal does nothing |
| `gitServerId`        | `skill.git.serverId`           |                                                 | id of the `<server>` in `settings.xml`             |
| `gitBranch`          | `skill.git.branch`             | `main`                                          | branch for releases - created if missing           |
| `gitSnapshotBranch`  | `skill.git.snapshotBranch`     |                                                 | branch for snapshots - without it snapshots are skipped |
| `gitDirectory`       | `skill.git.directory`          | `skills`                                        | directory in the repository containing the skills  |
| `gitTag`             | `skill.git.tag`                | `${project.artifactId}-${project.version}`      | tag of a release - empty for no tag                |
| `gitCommitMessage`   | `skill.git.commitMessage`      | `release ${project.artifactId} ${project.version}` | message of commit and tag                       |
| `gitStatuses`        |                                | `published`                                     | statuses of the skills to deploy                   |
| `gitAuthorName`      | `skill.git.authorName`         | `oci-skills-maven-plugin`                       | author, committer and tagger                       |
| `gitAuthorEmail`     | `skill.git.authorEmail`        | `oci-skills-maven-plugin@noreply.invalid`       | email of author, committer and tagger              |
| `gitAttempts`        | `skill.git.attempts`           | `3`                                             | attempts if the branch moved in the meantime       |
| `gitWorkDirectory`   | `skill.git.workDirectory`      | `${project.build.directory}/skill-git`          | the clone - deleted before cloning                 |
| `skip`               | `skill.git.skip`               | `false`                                         | skip the goal                                      |

### `oci-skills:test` (default phase `test`)

| parameter             | property                      | default                                  | description                                        |
|-----------------------|-------------------------------|------------------------------------------|----------------------------------------------------|
| `skillTestsDirectory` | `skill.testsDirectory`        | `${project.basedir}/src/test/skills`     | directory with one subdirectory per skill          |
| `failOnWarning`       | `skill.test.failOnWarning`    | `false`                                  | let warnings of the static checks fail             |
| `reportsDirectory`    | `skill.test.reportsDirectory` | `${project.build.directory}/surefire-reports` | the surefire reports are written to it        |
| `skip`                | `skill.test.skip`             | `false`                                  | skip the tests - `skipTests` and `maven.test.skip` work as well |

`includes`, `excludes`, `executables`, `namespace` and `version` are the same as for `oci-skills:build`.

### `oci-skills:archive` (default phase `verify`)

| parameter     | property             | default                                                     | description                 |
|---------------|----------------------|-------------------------------------------------------------|-----------------------------|
| `archiveFile` | `skill.archive.file` | `${project.build.directory}/${project.build.finalName}.jar` | the [skill jar](#skill-jar) of all skills |

Without packaging `skill` the jar is attached with classifier `skills`. Every skill is attached as jar with classifier
`<name>` - suffixed with `-draft` or `-testing` unless its status is `published` - next to `archiveFile`, e.g.
`target/<finalName>-nexus-test.jar`.

### `oci-skills:sync` (default phase `initialize`, not bound)

| parameter          | property                 | default                                    | description                                          |
|--------------------|--------------------------|--------------------------------------------|------------------------------------------------------|
| `sources`          |                          |                                            | the sources to pull from, see [Installing skills](#installing-skills) |
| `skills`           |                          |                                            | the skills to install: `id`, `version`, `status`, `source`, `repository` - or a [direct location](#installing-skills): `image` with `registryServerId`, `allowPlainHttp`, `caCertificates`, or `groupId`, `artifactId`, `classifier` |
| `installDirectory` | `skill.installDirectory` | `${project.basedir}/.claude/skills`        | the skills are installed into `<dir>/<name>`         |
| `lockFile`         | `skill.lockFile`         | `${project.basedir}/skills.lock.yaml`      | the lock file                                        |
| `workDirectory`    |                          | `${project.build.directory}/skill-pull`    | downloaded images - deleted after the goal           |
| `skip`             | `skill.sync.skip`        | `false`                                    | skip installing skills                               |

A `<source>` has an `id`, a `type` (`maven` or `oci`) and `groupId`, `artifactId` (`maven`, required) and
`classifier` (`maven`, default `{name}`) or `registry`, `repository` (optional), `registryServerId`,
`caCertificates` and `allowPlainHttp` (`oci`).

### `oci-skills:update`

Like `oci-skills:sync`, but resolves the skills again even if they are locked. `-Dskill=<ids or names>` - comma
separated - restricts this to the given skills, naming a skill that is not requested fails. Maven sources are checked
for new versions regardless of their update policy, like with `-U`.

### `oci-skills:pull`

| parameter          | property                    | default     | description                                                    |
|--------------------|-----------------------------|-------------|----------------------------------------------------------------|
| `skill`            | `skill`                     |             | `<namespace>/<name>[:<version>]`, resolved from the `sources`  |
| `status`           | `skill.status`              | `published` | with `skill`: the least mature status accepted                 |
| `source`           | `skill.source`              |             | with `skill`: the only source to ask                           |
| `repository`       | `skill.pull.repository`     |             | with `skill`: the repository of the skill, like `<repository>` of a `<skill>` |
| `artifact`         | `skill.artifact`            |             | `<groupId>:<artifactId>:<version>[:<classifier>]` - without classifier the main artifact |
| `image`            | `skill.image`               |             | `<registry>/<repository>:<tag>` or `<registry>/<repository>@<digest>` |
| `registryServerId` | `skill.registryServerId`    |             | with `image`: id of the `<server>` in `settings.xml`           |
| `allowPlainHttp`   | `skill.pull.allowPlainHttp` | `false`     | with `image`: connect without TLS - for local test registries only |

`sources` and `installDirectory` are the same as for `oci-skills:sync`. Exactly one of `skill`, `artifact` and
`image` must be set.

* `skill` needs `<sources>` and therefore a project. The version may be a range like for `<skill>` - without version
  the highest version is installed.
* `image` trusts the CAs of the JVM only - `caCertificates` are not supported.
* `skill.registryServerId` is the same property as for `oci-skills:push`.

## Image format

* one `tar+gzip` layer with all selected files, paths relative to the skill directory - `SKILL.md` replaced by the
  generated one, plus the generated `skill.yaml`
* normalized: entries sorted by UTF-8 path, uid/gid `0`, empty owner/group, mode `0644` (`0755` for directories and
  executables), all timestamps set to the start time of the maven build, gzip header without timestamp. The images are
  not reproducible - every build gets a new timestamp and therefore new digests.
* config `linux/amd64` with the layer's diffID - byte identical to skillctl
* manifest annotations:

| annotation                              | value                                            |
|-----------------------------------------|--------------------------------------------------|
| `io.skillimage.status`                  | `status` of the frontmatter, default `published` |
| `io.skillimage.wordcount`               | word count of `SKILL.md`, omitted if `0`         |
| `org.opencontainers.image.created`      | build timestamp, UTC, second precision           |
| `org.opencontainers.image.description`  | `description` of the `SKILL.md` frontmatter      |
| `org.opencontainers.image.title`        | `name` of the `SKILL.md` frontmatter             |
| `org.opencontainers.image.vendor`       | `namespace`                                      |
| `org.opencontainers.image.version`      | `version`                                        |

* tags: `draft` → `<version>-draft`, `testing` → `<version>-testing`, `published` → `<version>`. No `latest`.

The word count follows skillctl 0.7.2 exactly: a YAML frontmatter starting with `---` is removed up to and including
the next `\n---`, the rest is split at white space as defined by go's `unicode.IsSpace` and the fields are counted.

### Skill jar

`oci-skills:archive` stores the skills in maven repositories as plain jars - the jar of a single skill and the jar of
all skills have the same layout:

```
nexus-test/                                   # the skill files, exactly as in the layer
nexus-test/SKILL.md                           #   modes 0644, 0755 for directories and executables
nexus-test/skill.yaml
nexus-test/scripts/run.sh
META-INF/oci-skills/nexus-test/manifest.json  # the manifest of the image, byte identical
META-INF/oci-skills/nexus-test/config.json    # the config of the image, byte identical
```

* the directory is the `name` of the skill - unpacking a jar into `.claude/skills`, e.g. with `dependency:unpack`,
  results in `.claude/skills/nexus-test/SKILL.md`
* reproducible: entries sorted by path, all timestamps set to the `created` annotation of the image, no extra fields
  and no `META-INF/MANIFEST.MF`
* the digest of the image is kept: when a skill is installed from a jar, its digest is the SHA-256 of `manifest.json`.
  The jar is verified before - `config.json` has to match the config descriptor of the manifest, and the layer rebuilt
  from the skill files (with the `created` timestamp and the modes of the jar) has to have the diffID of the config.
  The compressed layer is not compared, its bytes depend on the JDK. So a skill locked from an OCI registry may be
  installed from a maven repository and vice versa.
