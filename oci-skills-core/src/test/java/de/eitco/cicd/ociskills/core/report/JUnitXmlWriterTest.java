package de.eitco.cicd.ociskills.core.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JUnitXmlWriterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesSurefireCompatibleXml() throws Exception {
        TestSuite suite = new TestSuite("skill.nexus-test.static", List.of(
                TestCase.passed("links", Duration.ofMillis(1500), "warning: <unused> & \"odd\""),
                TestCase.failed("scripts", Duration.ZERO, "scripts/run.sh is not executable", "details\u0001"),
                TestCase.skipped("size", "not applicable")));

        Path file = new JUnitXmlWriter().write(suite, tempDirectory.resolve("surefire-reports"));

        assertThat(file).hasFileName("TEST-skill.nexus-test.static.xml");
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
        Element root = document.getDocumentElement();
        assertThat(root.getTagName()).isEqualTo("testsuite");
        assertThat(root.getAttribute("name")).isEqualTo("skill.nexus-test.static");
        assertThat(root.getAttribute("tests")).isEqualTo("3");
        assertThat(root.getAttribute("failures")).isEqualTo("1");
        assertThat(root.getAttribute("errors")).isEqualTo("0");
        assertThat(root.getAttribute("skipped")).isEqualTo("1");
        assertThat(root.getAttribute("time")).isEqualTo("1.500");

        NodeList cases = root.getElementsByTagName("testcase");
        assertThat(cases.getLength()).isEqualTo(3);
        Element links = (Element) cases.item(0);
        assertThat(links.getAttribute("name")).isEqualTo("links");
        assertThat(links.getAttribute("classname")).isEqualTo("skill.nexus-test.static");
        assertThat(links.getElementsByTagName("system-out").item(0).getTextContent())
                .isEqualTo("warning: <unused> & \"odd\"");

        Element failure = (Element) ((Element) cases.item(1)).getElementsByTagName("failure").item(0);
        assertThat(failure.getAttribute("message")).isEqualTo("scripts/run.sh is not executable");
        assertThat(((Element) cases.item(1)).getElementsByTagName("system-out").item(0).getTextContent())
                .isEqualTo("details?");

        Element skipped = (Element) ((Element) cases.item(2)).getElementsByTagName("skipped").item(0);
        assertThat(skipped.getAttribute("message")).isEqualTo("not applicable");
    }
}
