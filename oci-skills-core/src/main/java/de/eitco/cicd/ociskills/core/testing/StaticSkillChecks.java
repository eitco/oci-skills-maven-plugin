package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillTestException;
import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;
import de.eitco.cicd.ociskills.core.report.TestCase;
import de.eitco.cicd.ociskills.core.report.TestSuite;
import de.eitco.cicd.ociskills.core.skillcard.SkillCard;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import de.eitco.cicd.ociskills.core.skillcard.SkillFrontmatterReader;
import de.eitco.cicd.ociskills.core.skillcard.SkillMarkdown;
import de.eitco.cicd.ociskills.core.skillcard.SkillValidator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks a skill directory without running anything. Every check is reported as one test case:
 * <ul>
 *     <li>{@code frontmatter} - the rules of the skill image and the agent skills format, see
 *     {@link SkillValidator}</li>
 *     <li>{@code links} - relative links and images in markdown files point to files inside the skill that go into
 *     the image, symbolic links stay inside the skill</li>
 *     <li>{@code scripts} - files below {@code scripts/} are executable (warning) and executable text files start
 *     with a shebang</li>
 *     <li>{@code orphans} - files that no markdown file refers to (warning)</li>
 *     <li>{@code size} - {@code SKILL.md} has at most {@value #MAX_PROMPT_LINES} lines (warning)</li>
 * </ul>
 */
public class StaticSkillChecks {

    public static final List<String> CHECKS = List.of("frontmatter", "links", "scripts", "orphans", "size");

    /**
     * The recommended maximum length of {@code SKILL.md} in the agent skills format.
     */
    public static final int MAX_PROMPT_LINES = 500;

    private static final String SCRIPTS_DIRECTORY = "scripts/";

    /**
     * Inline links and images: {@code [text](target "title")}, {@code ![alt](<target with spaces>)}.
     */
    private static final Pattern INLINE_LINK =
            Pattern.compile("!?\\[[^]]*]\\(\\s*(<[^>]*>|[^)\\s]+)(?:\\s+(?:\"[^\"]*\"|'[^']*'))?\\s*\\)");

    /**
     * Link reference definitions: {@code [id]: target}.
     */
    private static final Pattern REFERENCE_DEFINITION =
            Pattern.compile("^ {0,3}\\[[^]]+]:\\s*(<[^>]*>|\\S+)", Pattern.MULTILINE);

    private static final Pattern INLINE_CODE = Pattern.compile("(`+)[\\s\\S]*?\\1");

    private static final Pattern URI_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    /**
     * @param skillDirectory the skill directory
     * @param selection      the files that go into the image
     * @param settings       the values of the SkillCard configured in the plugin
     * @return all problems found, ordered like {@link #CHECKS}
     */
    public List<Finding> check(Path skillDirectory, FileSelection selection, SkillCardSettings settings)
            throws SkillTestException {
        Map<String, List<Finding>> findings = new LinkedHashMap<>();
        CHECKS.forEach(check -> findings.put(check, new ArrayList<>()));

        checkFrontmatter(skillDirectory, settings, findings.get("frontmatter"));

        List<SkillFile> files;
        try {
            files = new SkillFileCollector(selection).collect(skillDirectory);
        } catch (SkillValidationException e) {
            e.problems().forEach(problem -> findings.get("links").add(Finding.error("links", problem)));
            files = List.of();
        } catch (SkillBuildException e) {
            throw new SkillTestException(e.getMessage(), e);
        }

        Set<String> paths = files.stream().map(SkillFile::path).collect(Collectors.toSet());
        Map<String, String> markdown = new LinkedHashMap<>();
        for (SkillFile file : files) {
            if (!file.directory() && file.path().toLowerCase(Locale.ROOT).endsWith(".md")) {
                markdown.put(file.path(), read(file.source()));
            }
        }

        Set<String> linked = new HashSet<>();
        markdown.forEach((path, content) -> checkLinks(skillDirectory, path, content, paths, linked,
                findings.get("links")));
        for (SkillFile file : files) {
            if (!file.directory() && file.path().startsWith(SCRIPTS_DIRECTORY)) {
                checkScript(file, findings.get("scripts"));
            }
        }
        for (SkillFile file : files) {
            if (!file.directory() && !file.path().equals(SkillCard.PROMPT_FILE) && !linked.contains(file.path())
                    && markdown.values().stream().noneMatch(content -> content.contains(file.path()))) {
                findings.get("orphans").add(Finding.warning("orphans",
                        file.path() + " is not referenced by any markdown file"));
            }
        }
        String prompt = markdown.get(SkillCard.PROMPT_FILE);
        if (prompt != null) {
            long lines = prompt.chars().filter(character -> character == '\n').count()
                    + (prompt.isEmpty() || prompt.endsWith("\n") ? 0 : 1);
            if (lines > MAX_PROMPT_LINES) {
                findings.get("size").add(Finding.warning("size", SkillCard.PROMPT_FILE + " has " + lines
                        + " lines - keep it below " + MAX_PROMPT_LINES
                        + " lines and move details into referenced files"));
            }
        }
        return findings.values().stream().flatMap(List::stream).toList();
    }

    /**
     * @param suiteName     the name of the test suite
     * @param findings      the findings of {@link #check}
     * @param failOnWarning whether warnings fail their check as well
     * @return one test case per check
     */
    public static TestSuite toSuite(String suiteName, List<Finding> findings, boolean failOnWarning) {
        List<TestCase> cases = new ArrayList<>();
        for (String check : CHECKS) {
            List<String> failing = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (Finding finding : findings) {
                if (!finding.check().equals(check)) {
                    continue;
                }
                if (finding.severity() == Finding.Severity.ERROR || failOnWarning) {
                    failing.add(finding.message());
                } else {
                    warnings.add("warning: " + finding.message());
                }
            }
            String output = warnings.isEmpty() ? null : String.join("\n", warnings);
            cases.add(failing.isEmpty() ? TestCase.passed(check, Duration.ZERO, output)
                    : TestCase.failed(check, Duration.ZERO, String.join("\n", failing), output));
        }
        return new TestSuite(suiteName, cases);
    }

    private static void checkFrontmatter(Path skillDirectory, SkillCardSettings settings, List<Finding> findings) {
        try {
            SkillMarkdown markdown = new SkillFrontmatterReader().read(skillDirectory);
            new SkillValidator().validate(markdown.frontmatter(), settings, skillDirectory);
        } catch (SkillValidationException e) {
            e.problems().forEach(problem -> findings.add(Finding.error("frontmatter", problem)));
        }
    }

    private static void checkLinks(Path skillDirectory, String file, String content, Set<String> paths,
                                   Set<String> linked, List<Finding> findings) {
        String text = withoutCode(content);
        List<String> targets = new ArrayList<>();
        for (Pattern pattern : List.of(INLINE_LINK, REFERENCE_DEFINITION)) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                targets.add(matcher.group(1));
            }
        }
        for (String rawTarget : targets) {
            String target = localTarget(rawTarget);
            if (target == null) {
                continue;
            }
            String problem = null;
            String resolved = resolve(file, target);
            if (resolved == null) {
                problem = "points outside of the skill directory";
            } else if (paths.contains(resolved) || paths.contains(resolved + "/") || resolved.isEmpty()) {
                linked.add(resolved);
            } else if (Files.exists(skillDirectory.resolve(resolved))) {
                problem = "the file is excluded from the image";
            } else {
                problem = "the file does not exist";
            }
            if (problem != null) {
                findings.add(Finding.error("links", file + ": link to " + target + " - " + problem));
            }
        }
    }

    /**
     * @return the path part of a link target pointing to a local file, {@code null} for urls and anchors
     */
    private static String localTarget(String rawTarget) {
        String target = rawTarget.startsWith("<") && rawTarget.endsWith(">")
                ? rawTarget.substring(1, rawTarget.length() - 1) : rawTarget;
        if (target.startsWith("#") || target.startsWith("//") || URI_SCHEME.matcher(target).find()) {
            return null;
        }
        int end = target.length();
        for (char delimiter : new char[]{'#', '?'}) {
            int index = target.indexOf(delimiter);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        target = target.substring(0, end);
        if (target.contains("%")) {
            try {
                target = URLDecoder.decode(target.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // not url encoded - keep it as it is
            }
        }
        return target.isBlank() ? null : target;
    }

    /**
     * @return the path relative to the skill directory, {@code null} if the target points outside of it
     */
    private static String resolve(String file, String target) {
        if (target.startsWith("/") || target.startsWith("\\")) {
            return null;
        }
        List<String> segments = new ArrayList<>(List.of(file.split("/")));
        segments.remove(segments.size() - 1);
        for (String segment : target.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }

    /**
     * Removes fenced code blocks and inline code, links in code are examples, not references.
     */
    private static String withoutCode(String content) {
        StringBuilder text = new StringBuilder();
        String fence = null;
        for (String line : content.split("\n", -1)) {
            String stripped = line.stripLeading();
            if (fence == null && (stripped.startsWith("```") || stripped.startsWith("~~~"))) {
                fence = stripped.substring(0, 3);
                text.append('\n');
                continue;
            }
            if (fence != null) {
                if (stripped.startsWith(fence)) {
                    fence = null;
                }
                text.append('\n');
                continue;
            }
            text.append(line).append('\n');
        }
        return INLINE_CODE.matcher(text).replaceAll("");
    }

    private static void checkScript(SkillFile file, List<Finding> findings) throws SkillTestException {
        if (!file.executable()) {
            findings.add(Finding.warning("scripts", file.path()
                    + " is not executable - set the executable bit or add it to <executables>"));
            return;
        }
        byte[] head;
        try (InputStream in = Files.newInputStream(file.source())) {
            head = in.readNBytes(8192);
        } catch (IOException e) {
            throw new SkillTestException("reading " + file.source() + " failed: " + e, e);
        }
        boolean binary = false;
        for (byte b : head) {
            if (b == 0) {
                binary = true;
                break;
            }
        }
        if (!binary && !(head.length >= 2 && head[0] == '#' && head[1] == '!')) {
            findings.add(Finding.error("scripts", file.path()
                    + " is executable but does not start with a shebang line ('#!')"));
        }
    }

    private static String read(Path file) throws SkillTestException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new SkillTestException("reading " + file + " failed: " + e, e);
        }
    }
}
