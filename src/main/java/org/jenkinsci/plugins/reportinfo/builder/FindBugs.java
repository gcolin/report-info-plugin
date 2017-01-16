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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import org.jenkinsci.plugins.reportinfo.model.JobNotification;
import org.jenkinsci.plugins.reportinfo.model.NotificationDetail;
import org.jenkinsci.plugins.reportinfo.model.NotificationType;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * FindBugs XML reader.
 * 
 * @author Gael COLIN
 */
public class FindBugs implements NotificationBuilder {

    private static final Map<String, List<String>> FILENAMES = new HashMap<>();

    static {
        FILENAMES.put("main.xml", Arrays.asList("findbugs", "reports", "build"));
        FILENAMES.put("test.xml", Arrays.asList("findbugs", "reports", "build"));
        List<String> elist = Collections.emptyList();
        FILENAMES.put("findbugs.xml", elist);
    }

    @Override
    public Map<String, List<String>> getFileNames() {
        return FILENAMES;
    }

    @Override
    public void parse(Path file, JobNotification jn, AllNotificationBuilder builder) {
        ResourceBundle rb = ResourceBundle.getBundle("findbugsmessages");
        try {
            Document doc = builder.factory.newDocumentBuilder().parse(file.toFile());
            XPath xpath = builder.pathFactory.newXPath();
            NodeList errors = doc.getElementsByTagName("BugInstance");
            for (int i = 0; i < errors.getLength(); i++) {
                Node error = errors.item(i);
                NamedNodeMap attr = error.getAttributes();
                StringBuilder message = new StringBuilder();
                Node abbrevNode = attr.getNamedItem("abbrev");
                String type = attr.getNamedItem("type").getNodeValue();
                if(abbrevNode != null) {
                    message.append(abbrevNode.getNodeValue());
                } else {
                    message.append(type);
                }
                message.append(": ");
                if(rb.containsKey(type)) {
                    message.append(rb.getString(type));
                } else {
                    message.append(type);
                }
                NodeList sources = (NodeList) xpath.evaluate("SourceLine", error, XPathConstants.NODESET);
                if(sources.getLength() > 0) {
                    Node source = sources.item(0);
                    NamedNodeMap sattr = source.getAttributes();
                    message.append(" in ");
                    message.append(sattr.getNamedItem("classname").getNodeValue());
                    message.append(" at [line ");
                    message.append(sattr.getNamedItem("start").getNodeValue());
                    message.append("]");
                } else {
                    Node parent = error.getParentNode();
                    if("file".equals(parent.getNodeName())) {
                        Node classname = parent.getAttributes().getNamedItem("classname");
                        if(classname != null) {
                            message.append(" in ");
                            message.append(classname.getNodeValue());
                        }
                    }
                    Node lineNumber = attr.getNamedItem("lineNumber");
                    if(lineNumber != null) {
                        message.append(" at [line ");
                        message.append(lineNumber.getNodeValue());
                        message.append("]");
                    }
                }
                
                jn.getList().add(new NotificationDetail(NotificationType.FINDBUG,
                        message.toString()));
            }
        } catch (XPathExpressionException | IOException | SAXException | ParserConfigurationException ex) {
            ex.printStackTrace(builder.logger);
        }
    }

    @Override
    public boolean accept(String fileName) {
        return false;
    }

}
