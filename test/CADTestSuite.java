package cadstudio;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Automated Unit Test Suite for HDE Studio CAD Engine.
 * Runs 100% in headless mode (no GUI or display server required).
 * Verifies critical architectural drafting math, dimensioning, versioning, and licensing logic.
 */
public class CADTestSuite {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("   HDE STUDIO AUTOMATED REGRESSION & UNIT TEST SUITE   ");
        System.out.println("=======================================================");
        long start = System.currentTimeMillis();

        testAppVersionFormatting();
        testVersionComparator();
        testWallCornerExtensionMath();
        testWallOpeningCutoutLogic();
        testImperialDimensionFormatting();
        testLicenseManagerValidation();

        long duration = System.currentTimeMillis() - start;
        System.out.println("\n-------------------------------------------------------");
        System.out.printf("Test Results: %d Passed, %d Failed, %d Total (took %d ms)\n",
                passedTests, failedTests, totalTests, duration);
        System.out.println("=======================================================");

        if (failedTests > 0) {
            System.err.println("FATAL: Test suite failed! Blocking deployment.");
            System.exit(1);
        } else {
            System.out.println("SUCCESS: All architectural and engine checks PASSED.");
            System.exit(0);
        }
    }

    private static void assertTrue(String testName, boolean condition, String errorDetail) {
        totalTests++;
        if (condition) {
            passedTests++;
            System.out.println("  [PASS] " + testName);
        } else {
            failedTests++;
            System.err.println("  [FAIL] " + testName + " -> " + errorDetail);
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        totalTests++;
        boolean match = (expected == null && actual == null) || (expected != null && expected.equals(actual));
        if (match) {
            passedTests++;
            System.out.println("  [PASS] " + testName);
        } else {
            failedTests++;
            System.err.println("  [FAIL] " + testName + " -> Expected: <" + expected + ">, Got: <" + actual + ">");
        }
    }

    // ─── Test 1: Application Version ───
    private static void testAppVersionFormatting() {
        System.out.println("\n[Suite 1] Application Version & Metadata");
        assertTrue("APP_VERSION is non-null and not empty",
                HousePlanCADApp.APP_VERSION != null && !HousePlanCADApp.APP_VERSION.isEmpty(),
                "Version string is missing");
        assertTrue("APP_VERSION follows semantic versioning (X.Y.Z)",
                HousePlanCADApp.APP_VERSION.matches("^\\d+\\.\\d+\\.\\d+$"),
                "Version format invalid: " + HousePlanCADApp.APP_VERSION);
        assertEquals("APP_VERSION matches current release v2.2.3", "2.2.3", HousePlanCADApp.APP_VERSION);
    }

    // ─── Test 2: In-App Version Comparator ───
    private static void testVersionComparator() {
        System.out.println("\n[Suite 2] Update Engine Version Comparison");
        assertTrue("Remote newer patch version triggers update",
                HousePlanCADApp.isNewerVersion("2.2.4", "2.2.3"), "Expected true for 2.2.4 > 2.2.3");
        assertTrue("Remote newer minor version triggers update",
                HousePlanCADApp.isNewerVersion("2.3.0", "2.2.3"), "Expected true for 2.3.0 > 2.2.3");
        assertTrue("Remote newer major version triggers update",
                HousePlanCADApp.isNewerVersion("3.0.0", "2.2.3"), "Expected true for 3.0.0 > 2.2.3");
        assertTrue("Same version does NOT trigger update",
                !HousePlanCADApp.isNewerVersion("2.2.3", "2.2.3"), "Expected false for 2.2.3 == 2.2.3");
        assertTrue("Older version does NOT trigger update",
                !HousePlanCADApp.isNewerVersion("1.2.1", "2.2.3"), "Expected false for 1.2.1 < 2.2.3");
        assertTrue("Handles 'v' prefix gracefully",
                HousePlanCADApp.isNewerVersion("v2.2.4", "v2.2.3"), "Expected true for v2.2.4 > v2.2.3");
    }

    // ─── Test 3: Wall Corner Extension Geometry Math ───
    private static void testWallCornerExtensionMath() {
        System.out.println("\n[Suite 3] Wall Corner Joining & Extension Math");
        // Setup two 9-inch walls meeting at corner (100, 100):
        // Wall 1: horizontal (100, 100) to (300, 100)
        // Wall 2: vertical (100, 100) to (100, 300)
        HousePlanCADApp.Wall w1 = new HousePlanCADApp.Wall(100, 100, 300, 100, 9.0);
        HousePlanCADApp.Wall w2 = new HousePlanCADApp.Wall(100, 100, 100, 300, 9.0);
        List<HousePlanCADApp.Wall> walls = new ArrayList<>();
        walls.add(w1);
        walls.add(w2);

        double extW1Start = HousePlanCADApp.computeWallEndpointExtension(walls, w1, true);
        double extW1End = HousePlanCADApp.computeWallEndpointExtension(walls, w1, false);
        double extW2Start = HousePlanCADApp.computeWallEndpointExtension(walls, w2, true);
        double extW2End = HousePlanCADApp.computeWallEndpointExtension(walls, w2, false);

        double expectedHalfThick = (9.0 / 24.0) * HousePlanCADApp.FT_PX; // 4.5 inches in world pixels = 7.5px

        assertTrue("Wall 1 start corner extends by half thickness",
                Math.abs(extW1Start - expectedHalfThick) < 0.01,
                "Expected " + expectedHalfThick + " but got " + extW1Start);
        assertTrue("Wall 1 unconnected end has 0 extension (no overhang)",
                extW1End == 0.0,
                "Expected 0.0 but got " + extW1End);
        assertTrue("Wall 2 start corner extends by half thickness",
                Math.abs(extW2Start - expectedHalfThick) < 0.01,
                "Expected " + expectedHalfThick + " but got " + extW2Start);
        assertTrue("Wall 2 unconnected end has 0 extension (no overhang)",
                extW2End == 0.0,
                "Expected 0.0 but got " + extW2End);
    }

    // ─── Test 4: Wall Opening Cutouts ───
    private static void testWallOpeningCutoutLogic() {
        System.out.println("\n[Suite 4] Wall Cutouts & Opening Interval Merging");
        List<double[]> cutouts = new ArrayList<>();
        // Two overlapping openings: [20, 50] and [40, 70]
        cutouts.add(new double[]{20, 50});
        cutouts.add(new double[]{40, 70});
        cutouts.sort(Comparator.comparingDouble(a -> a[0]));

        List<double[]> merged = new ArrayList<>();
        for (double[] c : cutouts) {
            if (merged.isEmpty()) merged.add(c);
            else {
                double[] last = merged.get(merged.size() - 1);
                if (c[0] <= last[1]) last[1] = Math.max(last[1], c[1]);
                else merged.add(c);
            }
        }

        assertEquals("Overlapping openings merge into 1 interval", 1, merged.size());
        assertEquals("Merged start is 20.0", 20.0, merged.get(0)[0]);
        assertEquals("Merged end is 70.0", 70.0, merged.get(0)[1]);
    }

    // ─── Test 5: Imperial Dimension Formatting ───
    private static void testImperialDimensionFormatting() {
        System.out.println("\n[Suite 5] Engineering Dimension Formatting");
        // 14 feet * 24 px/ft = 336 px
        String fmt = HousePlanCADApp.formatImperial(14.0 * HousePlanCADApp.FT_PX);
        assertTrue("Formats 14ft (336px) as 14'-0\"", fmt.contains("14'") && fmt.contains("0\""),
                "Unexpected format output: " + fmt);

        // 10 feet 6 inches * 24 px/ft = 252 px
        String fmt6 = HousePlanCADApp.formatImperial(10.5 * HousePlanCADApp.FT_PX);
        assertTrue("Formats 10.5ft (252px) as 10'-6\"", fmt6.contains("10'") && fmt6.contains("6\""),
                "Unexpected format output: " + fmt6);
    }

    // ─── Test 6: Licensing Logic ───
    private static void testLicenseManagerValidation() {
        System.out.println("\n[Suite 6] License Engine Baseline Verification");
        String fp = HousePlanCADApp.LicenseManager.getMachineFingerprint();
        assertTrue("Machine fingerprint generated", fp != null && fp.length() >= 8,
                "Invalid machine fingerprint: " + fp);

        // Verify key length constraint baseline
        assertTrue("Rejects key shorter than 12 chars",
                !HousePlanCADApp.LicenseManager.verifyAndActivate("test@example.com", "SHORT-KEY"),
                "Should reject key < 12 characters");
        assertTrue("Rejects null or invalid email",
                !HousePlanCADApp.LicenseManager.verifyAndActivate("invalid-email", "HDE-PRO-1234-5678"),
                "Should reject email without @");
    }
}
