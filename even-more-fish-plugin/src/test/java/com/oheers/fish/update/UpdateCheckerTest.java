package com.oheers.fish.update;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test void plainReleaseIsPreserved() throws Exception {
        assertEquals("2.3.6", UpdateChecker.parseVersionResponse("[{\"version_number\":\"2.3.6\"}]", "installed"));
    }
    @Test void minecraftSuffixIsRemovedWithoutDroppingSnapshotQualifier() throws Exception {
        assertEquals("2.3.6-SNAPSHOT", UpdateChecker.parseVersionResponse("[{\"version_number\":\"2.3.6-SNAPSHOT-1.21\"}]", "installed"));
        assertEquals("2.3.6", UpdateChecker.parseVersionResponse("[{\"version_number\":\"2.3.6-1.21.4\"}]", "installed"));
        assertEquals("2.3.6-SNAPSHOT", UpdateChecker.parseVersionResponse("[{\"version_number\":\"2.3.6-SNAPSHOT\"}]", "installed"));
    }
    @Test void emptyResponseUsesInstalledVersion() throws Exception {
        assertEquals("installed", UpdateChecker.parseVersionResponse("[]", "installed"));
    }
    @Test void malformedResponsesFailIntoTheExistingFallbackPath() {
        for (String body : new String[]{"{}", "[{}]", "[null]", "[{\"version_number\":null}]", "[{\"version_number\":123}]", "[{\"version_number\":\"\"}]", "not JSON"}) {
            assertThrows(Exception.class, () -> UpdateChecker.parseVersionResponse(body, "installed"));
        }
    }
}
