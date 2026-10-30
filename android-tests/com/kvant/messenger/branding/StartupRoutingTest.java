// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.branding;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.provider.Settings;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class, sdk=28)
public class StartupRoutingTest {
    @Test public void launchSnapshotSurvivesTelegramIntentConsumptionAndSupportsSkip() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        Settings.Global.putFloat(activity.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f);
        Intent original=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        activity.setIntent(original);
        Intent snapshot=new Intent(original);
        original.setAction(null); // LaunchActivity.handleIntent consumes the original before showing the overlay.
        FrameLayout root=new FrameLayout(activity);
        activity.setContentView(root);
        KvantStartup.show(activity,root,null,snapshot);
        assertEquals(1,root.getChildCount());
        assertTrue(root.getChildAt(0) instanceof KvantStartup);
        MotionEvent tap=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,1,1,0);
        root.getChildAt(0).dispatchTouchEvent(tap);
        tap.recycle();
        assertEquals(0,root.getChildCount());
    }
}
