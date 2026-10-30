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
| `oci-skills-core`           | SkillCard validation, deterministic tar layer, OCI config/manifest/layout, installing skills and the lock file |
| `oci-skills-registry-oras`  | `OciRegistryClient` implementation based on the ORAS java SDK - push and pull |
| `oci-skills-maven-plugin`   | the goals and the lifecycle of the packaging `skill`                       |

The core has no dependency on maven, nexus or ORAS.

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
| `deploy`           | `oci-skills:push`, then `maven-deploy-plugin:deploy`                   |

`oci-skills:archive` packs the OCI image layouts of all skills into `target/<finalName>.tar`, the main artifact of the
project - so `install` and `deploy` put the skills into maven repositories as well (type `skill`). Every skill is
attached on its own as well (type `tar`), so it can be [installed](#installing-skills) by other projects - its
classifier is the skill name, suffixed like the tag, e.g. `nexus-test` or `nexus-test-draft`. Local `mvn install`
calls never push, since the push is bound to `deploy`. `-Dskill.push.skip=true` skips the push in any case.

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
                <groupId>com.example.{namespace}</groupId>
                <artifactId>{namespace}-skills</artifactId>
            </source>
            <source>
                <id>my-registry</id>
                <type>oci</type>
                <registry>registry.example.com</registry>
                <repository>skills/{namespace}</repository>
                <registryServerId>my-registry</registryServerId>
            </source>
        </sources>
        <skills>
            <skill>
                <id>acme/nexus-test</id>
                <version>[1.0,2.0)</version>
            </skill>
            <skill>
                <id>acme/release-notes</id>
                <version>2.1.0</version>
                <status>testing</status>
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

A skill is identified by `<namespace>/<name>` of its SkillCard, independent of where it is stored. Every source maps
it to its own coordinates - `{namespace}` and `{name}` are replaced in `groupId`, `artifactId` and `repository`:

| `type`  | the skill is                                                             | as created by        |
|---------|--------------------------------------------------------------------------|----------------------|
| `maven` | the artifact `<groupId>:<artifactId>:tar:<name>[-<status>]:<version>`    | `oci-skills:archive` |
| `oci`   | the image `<registry>/<repository>/<name>:<version>[-<status>]`          | `oci-skills:push`    |

Maven sources use the repositories, mirrors and credentials of the build, including the local repository and the
reactor. OCI sources take their credentials from the `<server>` with the id `registryServerId` - the versions of a
skill are taken from the tags of `<registry>/<repository>/<name>`, so the credentials must be allowed to list them.

Every `<source>` needs an `id` unique among the sources. Placeholders other than `{namespace}` and `{name}` are a
configuration error.

**Resolving.** `version` is a maven version range or an exact version - unlike maven, `1.2.0` matches only `1.2.0`.
`status` is the least mature status accepted, default `published`. The sources are asked in their order, the first
source knowing a matching version wins, `<source>` in a `<skill>` asks that source only. The highest matching version
with an accepted status is taken - if a version has several statuses, the most mature one.

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
  version: 1.1.0
  status: published
  digest: sha256:4f2a...
  source: my-maven
```

`requested` is the request the entry was resolved for - a request with another `version`, `status` or `source` is
resolved again. The entries are sorted by id and the file is only written if its content changes. An invalid lock file
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
mvn oci-skills:pull -Dskill=acme/nexus-test:1.2.0
mvn oci-skills:pull -Dskill.artifact=com.example.acme:acme-skills:1.2.0:nexus-test
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

### `oci-skills:test` (default phase `test`)

| parameter             | property                      | default                                  | description                                        |
|-----------------------|-------------------------------|------------------------------------------|----------------------------------------------------|
| `skillTestsDirectory` | `skill.testsDirectory`        | `${project.basedir}/src/test/skills`     | directory with one subdirectory per skill          |
| `failOnWarning`       | `skill.test.failOnWarning`    | `false`                                  | let warnings of the static checks fail             |
| `reportsDirectory`    | `skill.test.reportsDirectory` | `${project.build.directory}/surefire-reports` | the surefire reports are written to it        |
| `skip`                | `skill.test.skip`             | `false`                                  | skip the tests - `skipTests` and `maven.test.skip` work as well |

`includes`, `excludes`, `executables`, `namespace` and `version` are the same as for `oci-skills:build`.

### `oci-skills:archive` (default phase `verify`)

| parameter     | property             | default                                              | description                       |
|---------------|----------------------|------------------------------------------------------|-----------------------------------|
| `archiveFile` | `skill.archive.file` | `${project.build.directory}/${project.build.finalName}.tar` | the tar file of all image layouts |

Without packaging `skill` the tar file is attached with classifier `skills`. Every skill is attached with classifier
`<name>` - suffixed with `-draft` or `-testing` unless its status is `published`.

### `oci-skills:sync` (default phase `initialize`, not bound)

| parameter          | property                 | default                                    | description                                          |
|--------------------|--------------------------|--------------------------------------------|------------------------------------------------------|
| `sources`          |                          |                                            | the sources to pull from, see [Installing skills](#installing-skills) |
| `skills`           |                          |                                            | the skills to install: `id`, `version`, `status`, `source` |
| `installDirectory` | `skill.installDirectory` | `${project.basedir}/.claude/skills`        | the skills are installed into `<dir>/<name>`         |
| `lockFile`         | `skill.lockFile`         | `${project.basedir}/skills.lock.yaml`      | the lock file                                        |
| `workDirectory`    |                          | `${project.build.directory}/skill-pull`    | downloaded images - deleted after the goal           |
| `skip`             | `skill.sync.skip`        | `false`                                    | skip installing skills                               |

A `<source>` has an `id`, a `type` (`maven` or `oci`) and `groupId` and `artifactId` (`maven`) or `registry`,
`repository`, `registryServerId`, `caCertificates` and `allowPlainHttp` (`oci`).

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
| `artifact`         | `skill.artifact`            |             | `<groupId>:<artifactId>:<version>:<classifier>`                |
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
