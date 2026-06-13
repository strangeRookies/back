package com.strange.safety.camera.service;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;

public class TestRunner {
    public static void main(String[] args) {
        System.out.println("=== Starting Programmatic JUnit Test Runner ===");
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(CameraServiceTest.class))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        
        System.out.println("=== Programmatic JUnit Test Runner Finished ===");
        if (summary.getTestsFailedCount() > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }
}
