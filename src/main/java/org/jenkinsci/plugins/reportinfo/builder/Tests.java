/*
 * The MIT License
 *
 * Copyright 2017 Admin.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import org.jenkinsci.plugins.reportinfo.ReportInfo;
import org.jenkinsci.plugins.reportinfo.model.JobNotification;
import org.jenkinsci.plugins.reportinfo.model.NotificationDetail;
import org.jenkinsci.plugins.reportinfo.model.NotificationType;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Surefire test reader.
 * 
 * @author Gael COLIN
 */
public class Tests implements NotificationBuilder {

    private static final Map<String, List<String>> FILENAMES = Collections.emptyMap();
    private static final Pattern PATTERN = Pattern.compile("TEST-.*\\.xml");

    @Override
    public Map<String, List<String>> getFileNames() {
        return FILENAMES;
    }

    @Override
    public void parse(Path file, JobNotification jn, AllNotificationBuilder builder) {
        try {
            Document doc = builder.factory.newDocumentBuilder().parse(file.toFile());
            XPath xpath = builder.pathFactory.newXPath();
            NodeList errors = (NodeList) xpath.evaluate("/testsuite/testcase/failure", doc, XPathConstants.NODESET);
            for (int i = 0; i < errors.getLength(); i++) {
                Node error = errors.item(i);
                NamedNodeMap attr = error.getAttributes();
                NamedNodeMap pattr = error.getParentNode().getAttributes();
                jn.getList().add(new NotificationDetail(builder.job.getName(), NotificationType.TEST,
                        attr.getNamedItem("message").getNodeValue()
                        + " in " + pattr.getNamedItem("classname").getNodeValue() + "." + pattr.getNamedItem("name").getNodeValue()));
            }
        } catch (XPathExpressionException | IOException | SAXException | ParserConfigurationException ex) {
            ReportInfo.LOG.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public boolean accept(String fileName) {
        return PATTERN.matcher(fileName).matches();
    }

}
