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
package org.jenkinsci.plugins.reportinfo;

import org.jenkinsci.plugins.reportinfo.model.NotificationBox;
import org.jenkinsci.plugins.reportinfo.model.NotificationDetail;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.ViewDescriptor;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.jenkinsci.plugins.reportinfo.builder.AllNotificationBuilder;
import org.jenkinsci.plugins.reportinfo.model.JobNotification;
import org.jenkinsci.plugins.reportinfo.model.NotificationType;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The new View.
 *
 * @author Gael COLIN
 */
public class ReportInfo extends ListView {

    private static JAXBContext CONTEXT;
    public static final Logger LOG = Logger.getLogger(ReportInfo.class.getName());
    /**
     * Lock for read/write
     */
    private static Map<String, ReadWriteLock> locks = new HashMap<>();
    private static ReentrantLock masterLock = new ReentrantLock();

    static {
        try {
            CONTEXT = JAXBContext.newInstance(JobNotification.class);
        } catch (JAXBException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ReportInfo(String name) {
        super(name);
    }

    public ReadWriteLock getLock(Job job) {
        masterLock.lock();
        try {
            ReadWriteLock lock = locks.get(job.getName());
            if (lock == null) {
                lock = new ReentrantReadWriteLock();
                locks.put(job.getName(), lock);
            }
            return lock;
        } finally {
            masterLock.unlock();
        }
    }

    public List<NotificationBox> getNotifications() {
        List<NotificationBox> notifications = new ArrayList<>();

        for (NotificationType type : NotificationType.values()) {
            notifications.add(new NotificationBox(type));
        }

        for (TopLevelItem item : getItems()) {
            if (!(item instanceof Job)) {
                continue;
            }
            Job job = (Job) item;
            File cache = new File(job.getRootDir(), "reportinfo.xml");
            long last = getLastStart(job);
            if (last == 0L) {
                continue;
            }
            JobNotification notification;
            if (cache.exists()) {
                try {
                    Lock lock = getLock(job).readLock();
                    lock.lock();
                    try {
                        notification = (JobNotification) CONTEXT.createUnmarshaller().unmarshal(cache);
                    } finally {
                        lock.unlock();
                    }
                    if (last > notification.getLastModified()) {
                        notification = refresh(job, last);
                    }
                } catch (JAXBException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                    notification = refresh(job, last);
                }
            } else {
                notification = refresh(job, last);
            }

            for (NotificationDetail detail : notification.getList()) {
                detail.setJob(job.getName());
                notifications.get(detail.getType().ordinal()).getDetails().add(detail);
            }
        }

        for (int i = notifications.size() - 1; i >= 0; i--) {
            if (notifications.get(i).getDetails().isEmpty()) {
                notifications.remove(i);
            }
        }
        return notifications;
    }

    private long getLastStart(Job job) {
        Run run = job.getLastBuild();
        if (run == null) {
            return 0L;
        }
        while (run != null && run.isBuilding()) {
            run = run.getPreviousBuild();
        }
        if (run == null) {
            return 0L;
        }
        return run.getStartTimeInMillis();
    }

    @SuppressWarnings("null")
    private JobNotification refresh(Job job, long last) {
        JobNotification n = new JobNotification();
        n.setLastModified(last);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            XPathFactory pathFactory = XPathFactory.newInstance();
            File config = new File(job.getRootDir(), "config.xml");
            Document doc = factory.newDocumentBuilder().parse(config);
            XPath xpath = pathFactory.newXPath();
            NodeList gradleBuild = (NodeList) xpath.evaluate("/project/builders/hudson.plugins.gradle.Gradle/rootBuildScriptDir", doc, XPathConstants.NODESET);
            if (gradleBuild.getLength() > 0) {
                Path path = Paths.get(gradleBuild.item(0).getTextContent().trim());
                new AllNotificationBuilder(n, path).start();
            } else {
                NodeList rootPom = (NodeList) xpath.evaluate("/maven2-moduleset/rootPOM", doc, XPathConstants.NODESET);
                boolean find = false;
                if (rootPom.getLength() > 0) {
                    File rootPomFile = new File(rootPom.item(0).getTextContent().trim());
                    if (rootPomFile.isAbsolute()) {
                        find = true;
                        new AllNotificationBuilder(n, rootPomFile.getParentFile().toPath()).start();
                    } else if (job.getLastBuild() instanceof AbstractBuild) {
                        find = true;
                        AbstractBuild build = (AbstractBuild) job.getLastBuild();
                        FilePath workspace = build.getWorkspace();
                        if (workspace != null) {
                            URI uri = workspace.toURI();
                            if (uri != null) {
                                rootPomFile = new File(new File(uri.toURL().getFile()), rootPom.item(0).getTextContent().trim());
                                new AllNotificationBuilder(n, rootPomFile.getParentFile().toPath()).start();
                            }
                        }
                    }
                }
                if (!find) {
                    rootPom = (NodeList) xpath.evaluate("/maven2-moduleset/rootModule", doc, XPathConstants.NODESET);
                    if (rootPom.getLength() > 0) {
                        if (job.getLastBuild() instanceof AbstractBuild) {
                            AbstractBuild build = (AbstractBuild) job.getLastBuild();
                            FilePath workspace = build.getWorkspace();
                            if (workspace != null) {
                                URI uri = workspace.toURI();
                                if (uri != null) {
                                    new AllNotificationBuilder(n, new File(uri.toURL().getFile()).toPath()).start();
                                }
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | XPathExpressionException | IOException | SAXException | ParserConfigurationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }

        Lock lock = getLock(job).writeLock();
        lock.lock();
        try {
            CONTEXT.createMarshaller().marshal(n, new File(job.getRootDir(), "reportinfo.xml"));
        } catch (JAXBException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } finally {
            lock.unlock();
        }

        return n;
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {

        @Override
        public String getDisplayName() {
            return "ReportInfo";
        }
    }
}
