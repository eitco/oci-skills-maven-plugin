# Golden Master: skillctl 0.7.2

Reference image built with `skillctl` version 0.7.2 (https://github.com/redhat-et/skillimage, tag `v0.7.2`):

```bash
XDG_DATA_HOME=/tmp/gm/data skillctl build nexus-test
cp -a /tmp/gm/data/skillctl/store layout
```

- `nexus-test/` – the skill directory used as input. skillctl needed a hand written `skill.yaml` in it; it was
  removed afterwards, since the plugin generates it from the `SKILL.md` frontmatter and the settings
  (`namespace: cicd`, `version: 0.0.1`). The generated file is byte identical to the one in the skillctl layer.
- `layout/` – the OCI image layout written by skillctl (tag `cicd/nexus-test:0.0.1-draft`)

Known differences that are intentional (format compatibility, not byte identity):

- skillctl copies uid/gid/user names, file modes and mtimes from the file system, the plugin normalizes them.
- skillctl writes directory entries without trailing `/`, the plugin writes them with trailing `/`.
- skillctl uses `<namespace>/<name>:<tag>` as `org.opencontainers.image.ref.name` in `index.json`, the plugin uses the tag only.
- The plugin adds `namespace` and `version` to the frontmatter of the packaged `SKILL.md`, skillctl
  packages the file unchanged. All other files - including `skill.yaml` - and the word count are identical.
