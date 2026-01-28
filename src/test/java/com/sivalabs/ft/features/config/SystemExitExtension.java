package com.sivalabs.ft.features.config;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for testing System.exit() behavior in Java 21+.
 *
 * Since SecurityManager is completely removed in Java 21+, this extension
 * provides a simplified approach for testing shutdown behavior without
 * actually intercepting System.exit() calls.
 *
 * Instead of preventing System.exit(), tests should be designed to verify
 * the conditions that would lead to an exit call rather than the exit call itself.
 */
public class SystemExitExtension implements BeforeEachCallback {

    private static final AtomicBoolean exitCalled = new AtomicBoolean(false);
    private static final AtomicInteger exitCode = new AtomicInteger(0);

    /**
     * Exception thrown to simulate System.exit() behavior in tests.
     * Used by test code to simulate exit conditions.
     */
    public static class SystemExitException extends RuntimeException {
        private final int exitCode;

        public SystemExitException(int exitCode) {
            super("System.exit(" + exitCode + ") would be called");
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        reset();
    }

    /**
     * Check if a simulated System.exit() was called during test execution.
     *
     * Note: In Java 21+, we can't actually intercept System.exit() calls.
     * This method is kept for API compatibility but will always return false
     * unless explicitly set by test code using simulateExit().
     */
    public static boolean wasExitCalled() {
        return exitCalled.get();
    }

    /**
     * Get the exit code from a simulated System.exit().
     */
    public static int getLastExitCode() {
        return exitCode.get();
    }

    /**
     * Reset the state for a new test.
     */
    public static void reset() {
        exitCalled.set(false);
        exitCode.set(0);
    }

    /**
     * Simulate a System.exit() call for testing purposes.
     * This should be called by test code when System.exit() would normally be called.
     *
     * @param exitCode the exit code that would be passed to System.exit()
     */
    public static void simulateExit(int exitCode) {
        SystemExitExtension.exitCalled.set(true);
        SystemExitExtension.exitCode.set(exitCode);
        throw new SystemExitException(exitCode);
    }

    /**
     * Legacy method for compatibility.
     * @deprecated No longer needed in Java 21+
     */
    @Deprecated
    public static void restore() {
        // No-op in Java 21+
    }
}
