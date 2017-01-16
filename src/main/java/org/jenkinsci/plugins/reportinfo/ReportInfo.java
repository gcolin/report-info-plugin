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
package org.jenkinsci.plugins.reportinfo;

import org.jenkinsci.plugins.reportinfo.model.NotificationBox;
import org.jenkinsci.plugins.reportinfo.model.NotificationDetail;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.TopLevelItem;
import hudson.model.ViewDescriptor;
import java.io.File;
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
import org.jenkinsci.plugins.reportinfo.model.JobNotification;
import org.jenkinsci.plugins.reportinfo.model.NotificationType;

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

    public static ReadWriteLock getLock(Job job) {
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
            if (!cache.exists()) {
                continue;
            }
            JobNotification notification = null;
            Lock lock = getLock(job).readLock();
            lock.lock();
            try {
                notification = (JobNotification) CONTEXT.createUnmarshaller().unmarshal(cache);
            } catch (JAXBException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } finally {
                lock.unlock();
            }
            if (notification != null) {
                for (NotificationDetail detail : notification.getList()) {
                    detail.setJob(job.getName());
                    notifications.get(detail.getType().ordinal()).getDetails().add(detail);
                }
            }
        }

        for (int i = notifications.size() - 1; i >= 0; i--) {
            if (notifications.get(i).getDetails().isEmpty()) {
                notifications.remove(i);
            }
        }
        return notifications;
    }

    public static void write(JobNotification notif, Job job) {
        Lock lock = getLock(job).writeLock();
        lock.lock();
        try {
            CONTEXT.createMarshaller().marshal(notif, new File(job.getRootDir(), "reportinfo.xml"));
        } catch (JAXBException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } finally {
            lock.unlock();
        }
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.ReportInfo_description();
        }
    }
}
