/**
 * Copyright 2014 Comcast Cable Communications Management, LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.comcast.zucchini;

import java.util.List;
import java.util.LinkedList;
import java.util.Properties;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import com.google.gson.JsonArray;

import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import net.masterthought.cucumber.Reportable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"WeakerAccess", "SynchronizeOnNonFinalField", "unused"})
class ZucchiniShutdownHook extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZucchiniShutdownHook.class);
    private static final String NAME_ENV_VAR = "ZUCCHINI_REPORT_NAME";

    private static ZucchiniShutdownHook instance = null;

    public static ZucchiniShutdownHook getDefault() {
        //double checked locking on the class instance to create singleton
        if(ZucchiniShutdownHook.instance == null) {
            synchronized(ZucchiniShutdownHook.class) {
                if(ZucchiniShutdownHook.instance == null) {
                    ZucchiniShutdownHook.instance = new ZucchiniShutdownHook();
                }
            }
        }

        //return singleton instance
        return ZucchiniShutdownHook.instance;
    }

    private List<String> zucchiniFailureCauses;

    private ZucchiniShutdownHook() {
        zucchiniFailureCauses = new LinkedList<>();
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public void run() {
        LOGGER.trace("Running ZucchiniShutdownHook");
        FileWriter writer = null;
        try {
            if (0 == AbstractZucchiniTest.featureSet.size()) {
                LOGGER.warn("There are 0 features run");
            }

            for(String fileName : AbstractZucchiniTest.featureSet.keySet()) {
                LOGGER.trace("Writing feature set out to {}", fileName);
                /* write the json first, needed for html generation */
                File json = new File(fileName);
                JsonArray features = AbstractZucchiniTest.featureSet.get(fileName);
                writer = new FileWriter(json);
                writer.write(features.toString());
                writer.close();
                writer = null;

                /* write the html report files */
                ZucchiniOutput options = getClass().getAnnotation(ZucchiniOutput.class);
                File html;
                if(options != null)
                    html = new File(options.html());
                else
                    html = new File("target/zucchini-reports");

                List<String> pathList = new LinkedList<>();
                pathList.add(json.getAbsolutePath());

                String version = this.getClass().getPackage().getImplementationVersion();

                if(version == null) {
                    try {
                        InputStream ips = this.getClass().getClassLoader().getResourceAsStream("version.properties");

                        if(ips != null) {
                            Properties props = new Properties();
                            props.load(ips);

                            version = props.getProperty("version");
                        }
                    }
                    catch(IOException ioe) {
                        LOGGER.warn("{}", ioe);
                    }
                }

                if(version == null)
                    version = "";
                else
                    version = " - Zucchini (" + version + ")";

                String rptName;

                if(System.getenv(NAME_ENV_VAR) != null)
                    rptName = System.getenv(NAME_ENV_VAR);
                else
                    rptName = System.getProperty("cucumber_report_name", "Cucumber Report");


                File reportOutputDirectory = new File("target/cucumber-html-reports");
                Configuration configuration = new Configuration(reportOutputDirectory, rptName);
                configuration.setBuildNumber(version);
                configuration.setParallelTesting(false);

                ReportBuilder reportBuilder = new ReportBuilder(pathList, configuration);
                Reportable report = reportBuilder.generateReports();

                /* ReportBuilder reportBuilder = new ReportBuilder(pathList, html, "", rptName + version, "Zucchini" + version, true, true,
                   true, false, false, false, false, false);

                // ReportBuilder(List<String> jsonReports, File reportDirectory, String pluginUrlPath, String buildNumber, String buildProject,
                // boolean skippedFails, boolean pendingFails, boolean undefinedFails, boolean missingFails, boolean flashCharts, boolean runWithJenkins,
                // boolean highCharts, boolean parallelTesting) throws IOException, VelocityException {

                reportBuilder.generateReports();

                boolean buildResult = reportBuilder.getBuildStatus();
                if(!buildResult)
                    throw new Exception("BUILD FAILED - Check Report For Details");*/
            }
        }
        catch(Exception t) {
            LOGGER.error("FATAL ERROR: " + t.getMessage());
            Runtime.getRuntime().halt(-1);
        }
        finally {
            if(writer != null) {
                try {
                    writer.close();
                }
                catch(IOException ex) {
                    LOGGER.error("ERROR writing report:", ex);
                }
            }
        }

        if(this.zucchiniFailureCauses.size() > 0) {
            StringBuilder sb = new StringBuilder();

            int idx = 0;

            sb.append("Zucchini failed with the following errors:\n");
            for(String cause : this.zucchiniFailureCauses) {
                sb.append(String.format("Cause[%3d] :: %s\n", idx++, cause));
            }

            LOGGER.error(sb.toString());

            Runtime.getRuntime().halt(-1);
        }
    }

    public void addFailureCause(String cause) {
        synchronized(this.zucchiniFailureCauses) {
            this.zucchiniFailureCauses.add(cause);
        }
    }
}
