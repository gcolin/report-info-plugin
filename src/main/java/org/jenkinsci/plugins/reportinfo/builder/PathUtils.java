/*
 * The MIT License
 *
 * Copyright 2017 Gael COLIN.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.reportinfo.builder;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Gael COLIN
 */
public class PathUtils {

    public static Path getPath(AbstractBuild<?, ?> build, PrintStream logger) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            XPathFactory pathFactory = XPathFactory.newInstance();
            File config = new File(build.getParent().getRootDir(), "config.xml");
            Document doc = factory.newDocumentBuilder().parse(config);
            XPath xpath = pathFactory.newXPath();
            NodeList gradleBuild = (NodeList) xpath.evaluate("/project/builders/hudson.plugins.gradle.Gradle/rootBuildScriptDir", doc, XPathConstants.NODESET);
            if (gradleBuild.getLength() > 0) {
                return Paths.get(gradleBuild.item(0).getTextContent().trim());
            } else {
                NodeList rootPom = (NodeList) xpath.evaluate("/maven2-moduleset/rootPOM", doc, XPathConstants.NODESET);
                if (rootPom.getLength() > 0) {
                    File rootPomFile = new File(rootPom.item(0).getTextContent().trim());
                    if (rootPomFile.isAbsolute()) {
                        return rootPomFile.getParentFile().toPath();
                    }
                }
            }
            
            FilePath workspace = build.getWorkspace();
            if (workspace != null) {
                URI uri = workspace.toURI();
                if (uri != null) {
                    return new File(uri.toURL().getFile()).toPath();
                }
            }
        } catch (InterruptedException| XPathExpressionException | IOException | SAXException | ParserConfigurationException ex) {
            ex.printStackTrace(logger);
        }

        return null;
    }

}
