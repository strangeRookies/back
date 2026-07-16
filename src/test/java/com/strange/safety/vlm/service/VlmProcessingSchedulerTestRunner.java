package com.strange.safety.vlm.service;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** Runs VlmProcessingSchedulerTest in-process when Gradle forked workers fail to load the class. */
public class VlmProcessingSchedulerTestRunner {

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(VlmProcessingSchedulerTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.getFailures().forEach(f -> {
            System.out.println("FAILURE: " + f.getTestIdentifier().getDisplayName());
            f.getException().printStackTrace(System.out);
        });
        if (summary.getTestsFailedCount() > 0 || summary.getTestsSucceededCount() == 0) {
            System.exit(1);
        }
    }
}