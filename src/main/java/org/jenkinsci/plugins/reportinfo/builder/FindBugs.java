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

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.SortedBugCollection;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.dom4j.DocumentException;
import org.jenkinsci.plugins.reportinfo.ReportInfo;
import org.jenkinsci.plugins.reportinfo.model.JobNotification;
import org.jenkinsci.plugins.reportinfo.model.NotificationDetail;
import org.jenkinsci.plugins.reportinfo.model.NotificationType;

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
        try {
            SortedBugCollection collection = new SortedBugCollection();
            collection.readXML(file.toFile());
            for (BugInstance warning : collection.getCollection()) {
                SourceLineAnnotation sourceLine = warning.getPrimarySourceLineAnnotation();
                jn.getList().add(new NotificationDetail(builder.job.getName(), NotificationType.FINDBUG, warning.getMessage() + " at " + sourceLine.toString() + " in " + sourceLine.getClassName()));
            }
        } catch (DocumentException | IOException ex) {
            ReportInfo.LOG.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public boolean accept(String fileName) {
        return false;
    }

}
