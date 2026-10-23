package de.eitco.cicd.ociskills.core.report;

import de.eitco.cicd.ociskills.core.SkillTestException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * Writes a {@link TestSuite} in the XML format of the maven surefire plugin, so CI servers like Jenkins show skill
 * tests like any other test.
 */
public class JUnitXmlWriter {

    /**
     * Writes {@code TEST-<suite name>.xml} into the given directory.
     *
     * @return the written file
     */
    public Path write(TestSuite suite, Path reportsDirectory) throws SkillTestException {
        Path file = reportsDirectory.resolve("TEST-" + suite.name() + ".xml");
        try {
            Files.createDirectories(reportsDirectory);
            try (OutputStream out = Files.newOutputStream(file)) {
                XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
                xml.writeStartDocument("UTF-8", "1.0");
                xml.writeCharacters("\n");
                xml.writeStartElement("testsuite");
                xml.writeAttribute("name", clean(suite.name()));
                xml.writeAttribute("tests", Integer.toString(suite.cases().size()));
                xml.writeAttribute("failures", Long.toString(suite.count(TestStatus.FAILED)));
                xml.writeAttribute("errors", "0");
                xml.writeAttribute("skipped", Long.toString(suite.count(TestStatus.SKIPPED)));
                xml.writeAttribute("time", seconds(suite.cases().stream().map(TestCase::duration)
                        .reduce(Duration.ZERO, Duration::plus)));
                for (TestCase testCase : suite.cases()) {
                    writeCase(xml, suite, testCase);
                }
                xml.writeCharacters("\n");
                xml.writeEndElement();
                xml.writeEndDocument();
                xml.close();
            }
            return file;
        } catch (IOException | XMLStreamException e) {
            throw new SkillTestException("writing test report " + file + " failed: " + e, e);
        }
    }

    private static void writeCase(XMLStreamWriter xml, TestSuite suite, TestCase testCase) throws XMLStreamException {
        xml.writeCharacters("\n  ");
        xml.writeStartElement("testcase");
        xml.writeAttribute("name", clean(testCase.name()));
        xml.writeAttribute("classname", clean(suite.name()));
        xml.writeAttribute("time", seconds(testCase.duration()));
        if (testCase.status() == TestStatus.FAILED) {
            xml.writeStartElement("failure");
            xml.writeAttribute("message", clean(testCase.message()));
            xml.writeCharacters(clean(testCase.message()));
            xml.writeEndElement();
        } else if (testCase.status() == TestStatus.SKIPPED) {
            xml.writeEmptyElement("skipped");
            xml.writeAttribute("message", clean(testCase.message()));
        }
        if (testCase.output() != null) {
            xml.writeStartElement("system-out");
            xml.writeCharacters(clean(testCase.output()));
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private static String seconds(Duration duration) {
        return String.format(Locale.ROOT, "%.3f", duration.toMillis() / 1000.0);
    }

    /**
     * Replaces characters that are not allowed in XML 1.0, e.g. control characters in the output of a script.
     */
    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            boolean allowed = codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                    || (codePoint >= 0x20 && codePoint <= 0xD7FF) || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                    || codePoint >= 0x10000;
            cleaned.appendCodePoint(allowed ? codePoint : '?');
        });
        return cleaned.toString();
    }
}
