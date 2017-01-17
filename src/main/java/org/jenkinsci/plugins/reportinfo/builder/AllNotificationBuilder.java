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
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.jenkinsci.plugins.reportinfo.ReportInfo;
import org.jenkinsci.plugins.reportinfo.model.JobNotification;

/**
 *
 * @author Gael COLIN
 */
public class AllNotificationBuilder extends SimpleFileVisitor<Path> {

    protected JobNotification jn;
    protected Path path;
    private final NotificationBuilder[] all = {new Checkstyle(), new FindBugs(), new PMD(), new Tests()};
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    XPathFactory pathFactory = XPathFactory.newInstance();
    private Set<String> ignoreFolders = new HashSet<>();
    PrintStream logger;

    public AllNotificationBuilder(JobNotification jn, Path path, String excludeFolders, PrintStream logger) {
        this.jn = jn;
        this.path = path;
        this.logger = logger;
        for(String folder: excludeFolders.split(",")) {
            String ftrim = folder.trim();
            if(ftrim.isEmpty()) {
                continue;
            }
            ignoreFolders.add(ftrim);
        }
    }

    public void start() {
        try {
            Files.walkFileTree(path, this);
        } catch (IOException ex) {
            ex.printStackTrace(logger);
        }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path filenamePath = dir.getFileName();
        if(filenamePath != null && ignoreFolders.contains(filenamePath.toString())) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path filenamePath = file.getFileName();
        if (filenamePath == null || !filenamePath.toString().endsWith(".xml")) {
            return FileVisitResult.CONTINUE;
        }
        for (int i = 0; i < all.length; i++) {
            NotificationBuilder builder = all[i];
            String filename = filenamePath.toString();
            if (builder.getFileNames().containsKey(filename)) {
                List<String> paths = builder.getFileNames().get(filename);
                Path cpath = file.getParent();
                boolean ok = true;
                for (int j = 0; j < paths.size(); j++) {
                    if (cpath == null) {
                        ok = false;
                        break;
                    } else {
                        Path fpath = cpath.getFileName();
                        if (fpath == null || !fpath.toString().equals(paths.get(j))) {
                            ok = false;
                            break;
                        }
                    }
                    cpath = cpath.getParent();
                }
                if (ok) {
                    builder.parse(file, jn, this);
                    return FileVisitResult.CONTINUE;
                }
            } else if (builder.accept(filename)) {
                builder.parse(file, jn, this);
                return FileVisitResult.CONTINUE;
            }
        }

        return FileVisitResult.CONTINUE;
    }

}
