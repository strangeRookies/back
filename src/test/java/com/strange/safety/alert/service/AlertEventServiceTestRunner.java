package com.strange.safety.alert.service;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** Runs AlertEventServiceTest in-process when Gradle forked workers fail to load the class. */
public class AlertEventServiceTestRunner {

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(AlertEventServiceTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.getFailures().forEach(failure -> {
            System.out.println("FAILURE: " + failure.getTestIdentifier().getDisplayName());
            failure.getException().printStackTrace(System.out);
        });
        if (summary.getTestsFailedCount() > 0 || summary.getTestsSucceededCount() == 0) {
            System.exit(1);
        }
    }
}
