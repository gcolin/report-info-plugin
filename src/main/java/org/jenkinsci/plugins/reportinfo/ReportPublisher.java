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

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import java.nio.file.Path;
import org.jenkinsci.plugins.reportinfo.builder.AllNotificationBuilder;
import org.jenkinsci.plugins.reportinfo.builder.PathUtils;
import org.jenkinsci.plugins.reportinfo.model.JobNotification;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The post build action.
 * 
 * @author Gael COLIN
 */
public class ReportPublisher extends Recorder {
    
    private final String excludeFolders;
    
    @DataBoundConstructor
    public ReportPublisher(String excludeFolders) {
        this.excludeFolders = excludeFolders;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Generate report info");
        
        Path path = PathUtils.getPath(build, listener.getLogger());
        if(path == null) {
            listener.getLogger().println("Cannot generate report info because the root path of the project is not found.");
            listener.getLogger().println("In order to detect is in further release of the report-info, please open an issue with your job configuration (config.xml) to the project https://github.com/gcolin/report-info");
        } else {
            JobNotification jn = new JobNotification();
            jn.setLastModified(System.currentTimeMillis());
            new AllNotificationBuilder(jn, path, excludeFolders, listener.getLogger()).start();
            ReportInfo.write(jn, build.getParent());
        }
        
        return true;
    }

    public String getExcludeFolders() {
        return excludeFolders;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.ReportPublisher_description();
        }
    }
}
