/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.test.uiautomator.testapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Ignore;
import org.junit.Test;

/** Integration tests for multi-window support. */
@LargeTest
public class MultiWindowTest extends BaseTest {

    private static final long LONG_TIMEOUT_MS = 30_000;
    private static final long SHORT_TIMEOUT_MS = 5_000;
    private static final long TRANSITION_DELAY_MS = 5_000;

    private static final BySelector STATUS_BAR = By.res("com.android.systemui", "status_bar");

    @Test
    @SdkSuppress(minSdkVersion = 21)
    public void testMultiWindow_statusBar() {
        // Can locate objects outside of current context.
        assertTrue(mDevice.hasObject(STATUS_BAR));
    }

    @Ignore // b/260647289
    @Test
    @SdkSuppress(minSdkVersion = 21)
    public void testMultiWindow_reconnected() {
        Configurator configurator = Configurator.getInstance();
        int initialFlags = configurator.getUiAutomationFlags();
        // Update the UiAutomation flags to force the underlying connection to be recreated.
        configurator.setUiAutomationFlags(5);
        try {
            assertTrue(mDevice.wait(Until.hasObject(STATUS_BAR), SHORT_TIMEOUT_MS));
        } finally {
            configurator.setUiAutomationFlags(initialFlags);
        }
    }

    @Ignore // b/264940470
    @Test
    @SdkSuppress(minSdkVersion = 24)
    public void testMultiWindow_pictureInPicture() {
        BySelector defaultMode = By.res(TEST_APP, "pip_mode").text("Default Mode");
        BySelector pipMode = By.res(TEST_APP, "pip_mode").text("PiP Mode");

        // Create window in PiP mode and verify its location (bounds correctly calculated).
        launchTestActivity(PictureInPictureTestActivity.class);
        assertTrue(mDevice.hasObject(defaultMode));
        mDevice.pressHome();
        SystemClock.sleep(TRANSITION_DELAY_MS); // Wait for the PiP window to settle.
        int width = mDevice.getDisplayWidth();
        int height = mDevice.getDisplayHeight();
        Rect bottomHalf = new Rect(0, height / 2, width, height);
        UiObject2 pipWindow = mDevice.wait(Until.findObject(pipMode), LONG_TIMEOUT_MS);
        assertNotNull("Timed out waiting for PiP window", pipWindow);
        assertTrue(bottomHalf.contains(pipWindow.getVisibleBounds()));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32)
    public void testMultiWindow_splitScreen() {
        if (Build.VERSION.SDK_INT == 33 && !"REL".equals(Build.VERSION.CODENAME)) {
            return; // b/262909049: Do not run this test on pre-release Android U.
        }

        // Launch two split-screen activities with different IDs.
        launchTestActivity(SplitScreenTestActivity.class);
        SystemClock.sleep(TRANSITION_DELAY_MS); // Wait for the windows to settle.

        // Both split screen windows are present and searchable.
        UiObject2 firstWindow = mDevice.findObject(By.res(TEST_APP, "window_id").text("first"));
        assertNotNull(firstWindow);
        UiObject2 secondWindow = mDevice.findObject(By.res(TEST_APP, "window_id").text("second"));
        assertNotNull(secondWindow);

        // Operations (clicks) and coordinates are valid in both split screen windows.
        int width = mDevice.getDisplayWidth();
        int height = mDevice.getDisplayHeight();
        mDevice.click(width / 2, height / 4);
        mDevice.click(width / 2, 3 * height / 4);
        assertEquals("I've been clicked!", firstWindow.getText());
        assertEquals("I've been clicked!", secondWindow.getText());
    }
}
