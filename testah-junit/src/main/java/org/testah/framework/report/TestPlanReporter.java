package org.testah.framework.report;

import java.util.HashMap;

import org.testah.TS;
import org.testah.client.dto.TestPlanDto;
import org.testah.driver.http.requests.PostRequestDto;
import org.testah.driver.http.response.ResponseDto;
import org.testah.framework.cli.Cli;
import org.testah.framework.cli.Params;
import org.testah.framework.report.jira.JiraRemoteLinkBuilder;
import org.testah.framework.report.jira.JiraReporter;
import org.testah.framework.testPlan.AbstractTestPlan;
import org.testah.runner.testPlan.TestPlanActor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class TestPlanReporter.
 */
public class TestPlanReporter {

    private JiraRemoteLinkBuilder jiraRemoteLinkBuilder = null;

    /**
     * Report results.
     *
     * @param testPlan
     *            the test plan
     */
    public void reportResults(final TestPlanDto testPlan) {
        String filename = "results";
        final HashMap<String, String> ignored = AbstractTestPlan.getIgnoredTests();
        if (null == testPlan) {
            TS.log().info(Cli.BAR_LONG);
            TS.log().info(
                    Cli.BAR_WALL + "No Tests Ran, could be due to use of filters, for details turn on trace logging");

            if (null != ignored) {
                ignored.forEach((k, v) -> TS.log().info(Cli.BAR_WALL + "" + v + " - " + k));
            }
            TS.log().info(Cli.BAR_LONG);
            return;
        }
        try {
            testPlan.getRunInfo().setIgnore(AbstractTestPlan.getIgnoredTests().size());
            testPlan.getRunInfo().setTotal(testPlan.getRunInfo().getFail() + testPlan.getRunInfo().getPass()
                    + testPlan.getRunInfo().getIgnore());
            testPlan.getRunInfo().getRunTimeProperties().put("builtOn", TS.params().getComputerName());
        } catch (final Exception e) {
            TS.log().trace(e);
        }

        if (TestPlanActor.isResultsInUse()) {
            filename += "_" + testPlan.getSource().replace(".", "_") + "_" + TS.util().nowUnique();
        }
        final org.testah.client.dto.RunInfoDto ri = testPlan.getRunInfo();
        System.out.println("\n\n\n");
        TS.log().info(Cli.BAR_LONG);
        TS.log().info(Cli.BAR_WALL + "TestPlan[" + testPlan.getSource() + " (thread:" + Thread.currentThread().getId()
                + ") Status: " + testPlan.getStatusEnum());
        TS.log().info(Cli.BAR_WALL + "Passed: " + ri.getPass());
        TS.log().info(Cli.BAR_WALL + "Failed: " + ri.getFail());
        TS.log().info(Cli.BAR_WALL + "Ignore/NA/FilteredOut: " + ri.getIgnore());
        TS.log().info(Cli.BAR_WALL + "Total: " + ri.getTotal());
        TS.log().info(Cli.BAR_WALL + "Duration: " + TS.util().getDurationPretty(testPlan.getRunTime().getDuration()));

        if (TS.params().isUseXunitFormatter()) {
            TS.log().info(Cli.BAR_WALL + "Report XUnit: "
                    + new JUnitFormatter(testPlan).createReport(filename + ".xml").getReportFile().getAbsolutePath());
        }
        if (TS.params().isUseHtmlFormatter()) {
            final AbstractFormatter html = new HtmlFormatter(testPlan).createReport(filename + ".html");
            TS.log().info(Cli.BAR_WALL + "Report Html: " + html.getReportFile().getAbsolutePath());
            openReport(html.getReportFile().getAbsolutePath());
        }
        if (TS.params().isUseMetaFormatter()) {
            final AbstractFormatter meta = new MetaFormatter(testPlan).createReport(filename + ".txt");
            TS.log().info(Cli.BAR_WALL + "Report Meta: " + meta.getReportFile().getAbsolutePath());
        }
        if (TS.params().isUseJira()) {
            if (null != this.getJiraRemoteLinkBuilder()) {
                final JiraReporter jiraReporter = new JiraReporter();
                jiraReporter.createOrUpdateTestPlanRemoteLink(testPlan, this.getJiraRemoteLinkBuilder());
            } else {
                TS.log().warn("Use Jira is On, but JiraRemoteLinkBuilder is not set, can set ex: "
                        + "TS.getTestPlanReporter().setJiraRemoteLinkBuilder(jiraRemoteLinkBuilder);");
            }
        }
        if (null == TS.params().getSendJsonTestDataToService()
                || TS.params().getSendJsonTestDataToService().length() > 0) {
            try {
                final ObjectMapper map = new ObjectMapper();
                map.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
                TS.log().info(Cli.BAR_WALL + "Posting Data: ");
                final ResponseDto response = TS.http().doRequest(
                        new PostRequestDto(TS.params().getSendJsonTestDataToService(), AbstractTestPlan.getTestPlan())
                                .withJsonUTF8(),
                        false).print(true);
                TS.log().trace("Request Payload:\n" + response.getRequestUsed().getPayloadString());
                TS.log().trace("Response Body:\n" + response.getResponseBody());
                try {
                    final HashMap<String, String> values = TS.util().getMap().readValue(response.getResponseBody(),
                            new TypeReference<HashMap<String, String>>() {
                            });
                    if (null != values.get("message")) {
                        final HashMap<Integer, String> ids = TS.util().getMap().readValue(values.get("message"),
                                new TypeReference<HashMap<Integer, String>>() {
                                });
                        TS.log().info(Cli.BAR_LONG);
                        TS.log().info(Cli.BAR_WALL + "Ids From TMS");
                        ids.forEach((k, v) -> TS.log().info("ID[ " + k + " ] - " + v));
                    }
                } catch (final Exception issueWithResponse) {
                    TS.log().trace(issueWithResponse);
                }

            } catch (final Exception e) {
                TS.log().warn("Issue posting data to declared service: " + TS.params().getSendJsonTestDataToService(),
                        e);
            }
        }

        TS.log().info(Cli.BAR_LONG);
    }

    /**
     * Open report.
     *
     * @param pathToReport
     *            the path to report
     */
    public void openReport(final String pathToReport) {
        if (TS.params().isAutoOpenHtmlReport()) {
            try {
                ProcessBuilder pb = null;
                if (Params.isMac()) {
                    pb = new ProcessBuilder("/usr/bin/open", pathToReport);
                } else if (Params.isWindows()) {
                    pb = new ProcessBuilder("cmd", "/c", "start", pathToReport);
                }
                if (null != pb) {
                    final Process p = pb.start();
                    p.waitFor();
                }
            } catch (final Exception e) {
                throw new RuntimeException("Issue Opening Report", e);
            }
        }
    }

    public JiraRemoteLinkBuilder getJiraRemoteLinkBuilder() {
        return jiraRemoteLinkBuilder;
    }

    public void setJiraRemoteLinkBuilder(final JiraRemoteLinkBuilder jiraRemoteLinkBuilder) {
        this.jiraRemoteLinkBuilder = jiraRemoteLinkBuilder;
    }

}
