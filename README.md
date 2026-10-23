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
| `oci-skills-core`           | SkillCard validation, deterministic tar layer, OCI config/manifest/layout  |
| `oci-skills-registry-oras`  | `OciRegistryClient` implementation based on the ORAS java SDK              |
| `oci-skills-maven-plugin`   | the goals and the lifecycle of the packaging `skill`                       |

The core has no dependency on maven, nexus or ORAS.

## Usage

### Packaging `skill`

```xml
<packaging>skill</packaging>

<build>
    <plugins>
        <plugin>
            <groupId>de.eitco.cicd</groupId>
            <artifactId>oci-skills-maven-plugin</artifactId>
            <version>${oci-skills-maven-plugin.version}</version>
            <extensions>true</extensions>
            <configuration>
                <registry>nexus.eitco.de</registry>
                <repository>docker-cicd-prerelease/eitco/cicd</repository>
                <registryServerId>eitco-oci</registryServerId>
                <namespace>cicd</namespace>
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
project - so `install` and `deploy` put the skills into maven repositories as well (type `skill`). Local `mvn install`
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
            <groupId>de.eitco.cicd</groupId>
            <artifactId>oci-skills-maven-plugin</artifactId>
            <version>${oci-skills-maven-plugin.version}</version>
            <configuration>
                <registry>nexus.eitco.de</registry>
                <repository>docker-cicd-prerelease/eitco/cicd</repository>
                <registryServerId>eitco-oci</registryServerId>
                <namespace>cicd</namespace>
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
`nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test:0.0.1`.

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
  namespace: cicd                           # <namespace>
  version: 0.0.1                            # <version>, default ${project.version}
  description: Test skill for OCI registry  # SKILL.md frontmatter
spec:
  prompt: SKILL.md
```

The packaged `SKILL.md` shows the managed values as well - in `metadata` of the frontmatter, as intended by the agent
skills format:

```markdown
---
name: nexus-test
description: Test skill for OCI registry
metadata:
  namespace: cicd
  version: 0.0.1
---

# Nexus Test
...
```

Other frontmatter fields and `metadata` entries are kept in their order, everything after the closing `---` is kept
byte by byte. The frontmatter is written anew, so comments in it are not kept. `metadata.namespace` and
`metadata.version` must not be set in the source `SKILL.md`. A `status` in the frontmatter is kept like any other
field.

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
                    <groupId>de.eitco.cicd</groupId>
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
        <id>eitco-oci</id>
        <username>${env.NEXUS_USERNAME}</username>
        <password>${env.NEXUS_PASSWORD}</password>
    </server>
</servers>
```

Without `registryServerId` the push is anonymous.

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

Without packaging `skill` the tar file is attached with classifier `skills`.

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
