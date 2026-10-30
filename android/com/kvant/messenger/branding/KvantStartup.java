// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.branding;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import org.telegram.messenger.R;

/** Lightweight launch overlay: two silver orbits join, then reveal the supplied mark. */
public final class KvantStartup extends View {
    private static boolean shown;
    private static final int BACKGROUND = 0xff001139;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mark;
    private final Window window;
    private final int oldStatus, oldNavigation, oldVisibility;
    private ValueAnimator animator;
    private float progress;
    private boolean removed;
    private final float[][] segments = new float[240][6];

    public static void show(Activity activity, FrameLayout root, Bundle savedState, Intent intent) {
        if (shown || savedState != null || intent == null ||
                !Intent.ACTION_MAIN.equals(intent.getAction()) ||
                !intent.hasCategory(Intent.CATEGORY_LAUNCHER)) return;
        shown = true;
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        KvantStartup view = new KvantStartup(activity);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }

    private KvantStartup(Activity activity) {
        super(activity);
        mark = BitmapFactory.decodeResource(getResources(), R.drawable.kvant_mark);
        window = activity.getWindow();
        oldStatus = window.getStatusBarColor();
        oldNavigation = window.getNavigationBarColor();
        oldVisibility = window.getDecorView().getSystemUiVisibility();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        window.getDecorView().setSystemUiVisibility(oldVisibility &
                ~(SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        setContentDescription("kvantGram");
        setClickable(true);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { dismiss(); }
        });
        animator.start();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (animator == null) return;
        if (visibility == VISIBLE && animator.isPaused()) animator.resume();
        else if (visibility != VISIBLE && animator.isStarted()) animator.pause();
    }

    private static float smooth(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        return v * v * (3f - 2f * v);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float fade = 1f - smooth((progress - .9f) / .1f);
        setAlpha(fade);
        canvas.drawColor(BACKGROUND);
        float size = Math.min(getWidth() * .56f, getHeight() * .31f);
        float cx = getWidth() / 2f, cy = getHeight() * .46f;
        float join = smooth(progress / .67f);
        float reveal = smooth((progress - .63f) / .2f);
        float appearance = smooth(progress / .14f);
        if (reveal < 1f) {
            int index = 0;
            for (int ring = 0; ring < 2; ring++) {
                float sign = ring == 0 ? -1f : 1f;
                double rotation = sign * (.72 + (1 - join) * .45);
                for (int i = 0; i < 120; i++) {
                    double t = i * Math.PI * 2 / 120;
                    double next = (i + 1) * Math.PI * 2 / 120;
                    float[] s = segments[index++];
                    double x = Math.cos(t) * size * .49, y = Math.sin(t) * size * .21;
                    double nx = Math.cos(next) * size * .49, ny = Math.sin(next) * size * .21;
                    float shift = sign * (1 - join) * size * .62f;
                    s[0] = cx + shift + (float)(x * Math.cos(rotation) - y * Math.sin(rotation));
                    s[1] = cy + (float)(x * Math.sin(rotation) + y * Math.cos(rotation));
                    s[2] = cx + shift + (float)(nx * Math.cos(rotation) - ny * Math.sin(rotation));
                    s[3] = cy + (float)(nx * Math.sin(rotation) + ny * Math.cos(rotation));
                    s[4] = (float)Math.sin(t + ring * Math.PI / 2);
                    s[5] = ring;
                }
            }
            java.util.Arrays.sort(segments, (a, b) -> Float.compare(a[4], b[4]));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShader(null);
            for (float[] s : segments) {
                int silver = (int)(145 + 84 * ((s[4] + 1) / 2));
                paint.setColor(Color.rgb(silver, Math.min(255, silver + 4), Math.min(255, silver + 11)));
                paint.setAlpha((int)(255 * appearance * (1 - reveal)));
                paint.setStrokeWidth(size * .058f);
                canvas.drawLine(s[0], s[1], s[2], s[3], paint);
                paint.setColor(0xff9acdf1);
                paint.setAlpha((int)(110 * appearance * (1 - reveal)));
                paint.setStrokeWidth(size * .007f);
                canvas.drawLine(s[0], s[1] - size * .024f, s[2], s[3] - size * .024f, paint);
            }
        }
        if (mark != null && reveal > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setAlpha((int)(255 * reveal));
            canvas.drawBitmap(mark, null, new RectF(cx - size / 2, cy - size / 2,
                    cx + size / 2, cy + size / 2), paint);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffcbd4e3);
        paint.setAlpha((int)(210 * smooth(progress / .3f)));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(17 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText("kvantGram", cx, cy + size * .77f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) { performClick(); dismiss(); }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void dismiss() {
        if (removed) return;
        removed = true;
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); }
        if (getParent() instanceof ViewGroup) ((ViewGroup)getParent()).removeView(this);
        restoreWindow();
    }

    private void restoreWindow() {
        window.setStatusBarColor(oldStatus);
        window.setNavigationBarColor(oldNavigation);
        window.getDecorView().setSystemUiVisibility(oldVisibility);
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); }
        restoreWindow();
        super.onDetachedFromWindow();
    }
}
