package com.vinekeepers.core.cursor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCloudAdapterImplTest {

    @AfterEach
    void clearEnv() {
        System.clearProperty("CURSOR_API_KEY");
        System.clearProperty("CURSOR_API_BASE_URL");
    }

    @Test
    void createBranchWithoutApiKeyReturnsStubMessage() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl();
        String result = impl.createBranch("my-project", "luna-feature");
        assertNotNull(result);
        assertTrue(result.contains("stub") || result.contains("branch"));
    }

    @Test
    void createBranchWithApiKeyReturnsSuccessStyleMessage() {
        System.setProperty("CURSOR_API_KEY", "test-key");
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl();
        String result = impl.createBranch("my-project", "luna-feature");
        assertNotNull(result);
        assertTrue(result.contains("Branch") || result.contains("luna-feature"));
    }

    @Test
    void runNovaCommitWithoutApiKeyReturnsStubMessage() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl();
        String result = impl.runNovaCommit("p", "add feature");
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("stub") || result.contains("nova"));
    }

    @Test
    void pushWithoutApiKeyReturnsStubMessage() {
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl();
        String result = impl.push("p");
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("stub") || result.toLowerCase().contains("push"));
    }

    @Test
    void createPrWithApiKeyReturnsPrStyleMessage() {
        System.setProperty("CURSOR_API_KEY", "test-key");
        CursorCloudAdapterImpl impl = new CursorCloudAdapterImpl();
        String result = impl.createPr("p", "Luna: add tests");
        assertNotNull(result);
        assertTrue(result.contains("PR") || result.contains("Luna"));
    }
}
