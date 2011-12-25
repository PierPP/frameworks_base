/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    final static boolean DEBUG_DEADZONE = false;

    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;

    final static boolean ANIMATE_HIDE_TRANSITION = false; // turned off because it introduces
                                                          // unsightly delay when videos goes to
                                                          // full screen

    protected IStatusBarService mBarService;
    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;

    boolean mHidden, mLowProfile, mShowMenu;
    int mDisabledFlags = 0;

    public final static int SHOW_LEFT_MENU = 1;
    public final static int SHOW_RIGHT_MENU = 0;
    public final static int SHOW_BOTH_MENU = 2;

    private int mNavLayout = LAYOUT_REGULAR;
    public final static int LAYOUT_REGULAR = 0;
    public final static int LAYOUT_SEARCH = 1;

    public final static int VISIBILITY_SYSTEM = 0;
    public final static int VISIBILITY_SYSTEM_AND_INVIZ = 3;
    public final static int VISIBILITY_NEVER = 1;
    public final static int VISIBILITY_ALWAYS = 2;

    public View getSearchButton() {
        if (mNavLayout == LAYOUT_SEARCH)
            return mCurrentView.findViewById(R.id.search);
        else
            return null;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewById(R.id.recent_apps);
    }

    public View getLeftMenuButton() {
        return mCurrentView.findViewById(R.id.menu_left);
    }

    public View getRightMenuButton() {
        return mCurrentView.findViewById(R.id.menu);
    }

    public View getBackButton() {
        return mCurrentView.findViewById(R.id.back);
    }

    public View getHomeButton() {
        return mCurrentView.findViewById(R.id.home);
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mHidden = false;

        mDisplay = ((WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        final Resources res = mContext.getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
    }

    View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                setLowProfile(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0);
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };
    private OnLongClickListener mSearchLongClickListener = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            v.getContext().sendBroadcast(new Intent(Intent.ACTION_SEARCH_LONG_PRESS));
            Slog.i(TAG, "Sending long press search key event");
            return true;
        }
    };

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags)
            return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0);

        getBackButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        getHomeButton().setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);
        getRecentsButton().setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);
        if (getSearchButton() != null)
            getSearchButton().setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show)
            return;

        mShowMenu = show;
        boolean localShow = show;

        int currentSetting = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.MENU_LOCATION, SHOW_RIGHT_MENU);

        int currentVisibility = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.MENU_VISIBILITY, VISIBILITY_SYSTEM);

        switch (currentVisibility) {
            default:
            case VISIBILITY_SYSTEM:
                ((ImageView) getLeftMenuButton())
                        .setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                : R.drawable.ic_sysbar_menu);
                ((ImageView) getRightMenuButton())
                        .setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                : R.drawable.ic_sysbar_menu);
                break;
            case VISIBILITY_ALWAYS:
                ((ImageView) getLeftMenuButton())
                        .setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                : R.drawable.ic_sysbar_menu);
                ((ImageView) getRightMenuButton())
                        .setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                : R.drawable.ic_sysbar_menu);
                localShow = true;
                break;
            case VISIBILITY_NEVER:
                ((ImageView) getLeftMenuButton())
                        .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                ((ImageView) getRightMenuButton())
                        .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                localShow = true;
                break;
            case VISIBILITY_SYSTEM_AND_INVIZ:
                if (localShow) {
                    ((ImageView) getLeftMenuButton())
                            .setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                    : R.drawable.ic_sysbar_menu);
                    ((ImageView) getRightMenuButton())
                            .setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
                                    : R.drawable.ic_sysbar_menu);
                } else {
                    localShow = true;
                    ((ImageView) getLeftMenuButton())
                            .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                    ((ImageView) getRightMenuButton())
                            .setImageResource(R.drawable.ic_sysbar_menu_inviz);
                }
                break;
        }

        // do this after just in case show was changed
        switch (currentSetting) {
            case SHOW_BOTH_MENU:
                getLeftMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                getRightMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                break;
            case SHOW_LEFT_MENU:
                getLeftMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                getRightMenuButton().setVisibility(View.INVISIBLE);
                break;
            default:
            case SHOW_RIGHT_MENU:
                getLeftMenuButton().setVisibility(View.INVISIBLE);
                getRightMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
                break;
        }
    }

    public void setLowProfile(final boolean lightsOut) {
        setLowProfile(lightsOut, true, false);
    }

    public void setLowProfile(final boolean lightsOut, final boolean animate, final boolean force) {
        if (!force && lightsOut == mLowProfile)
            return;

        mLowProfile = lightsOut;

        if (DEBUG)
            Slog.d(TAG, "setting lights " + (lightsOut ? "out" : "on"));

        final View navButtons = mCurrentView.findViewById(R.id.nav_buttons);
        final View lowLights = mCurrentView.findViewById(R.id.lights_out);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        if (!animate) {
            navButtons.setAlpha(lightsOut ? 0f : 1f);

            lowLights.setAlpha(lightsOut ? 1f : 0f);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            navButtons.animate()
                    .alpha(lightsOut ? 0f : 1f)
                    .setDuration(lightsOut ? 600 : 200)
                    .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                    .alpha(lightsOut ? 1f : 0f)
                    .setStartDelay(lightsOut ? 500 : 0)
                    .setDuration(lightsOut ? 1000 : 300)
                    .setInterpolator(new AccelerateInterpolator(2.0f))
                    .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator _a) {
                            lowLights.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    public void setHidden(final boolean hide) {
        if (hide == mHidden)
            return;

        mHidden = hide;
        Slog.d(TAG,
                (hide ? "HIDING" : "SHOWING") + " navigation bar");

        // bring up the lights no matter what
        setLowProfile(false);
    }

    public void onFinishInflate() {

        switch (mNavLayout = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_LAYOUT, LAYOUT_REGULAR)) {
            default:
            case LAYOUT_REGULAR:
                mRotatedViews[Surface.ROTATION_0] =
                        mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

                mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

                mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                        ? findViewById(R.id.rot90)
                        : findViewById(R.id.rot270);
                break;
            case LAYOUT_SEARCH:
                mRotatedViews[Surface.ROTATION_0] =
                        mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0_search);

                mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90_search);

                mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                        ? findViewById(R.id.rot90_search)
                        : findViewById(R.id.rot270_search);

                View searchView = findViewById(R.id.search);
                searchView.setOnLongClickListener(mSearchLongClickListener);
                break;
        }

        for (View v : mRotatedViews) {
            // this helps avoid drawing artifacts with glowing navigation keys
            ViewGroup group = (ViewGroup) v.findViewById(R.id.nav_buttons);
            group.setMotionEventSplittingEnabled(false);
        }
        mCurrentView = mRotatedViews[Surface.ROTATION_0];

    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i = 0; i < 4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);
        mVertical = (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270);

        // force the low profile & disabled states into compliance
        setLowProfile(mLowProfile, false, true /* force */);
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG_DEADZONE) {
            mCurrentView.findViewById(R.id.deadzone).setBackgroundColor(0x808080FF);
        }

        if (DEBUG) {
            Slog.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }
    }

    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > mDisplay.getRawWidth()
                || r.bottom > mDisplay.getRawHeight();
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                getResourceName(mCurrentView.getId()),
                mCurrentView.getWidth(), mCurrentView.getHeight(),
                visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s hidden=%s low=%s menu=%s",
                mDisabledFlags,
                mVertical ? "true" : "false",
                mHidden ? "true" : "false",
                mLowProfile ? "true" : "false",
                mShowMenu ? "true" : "false"));

        final View back = getBackButton();
        final View home = getHomeButton();
        final View recent = getRecentsButton();
        final View menu = getRightMenuButton();

        pw.println("      back: "
                + PhoneStatusBar.viewInfo(back)
                + " " + visibilityToString(back.getVisibility())
                );
        pw.println("      home: "
                + PhoneStatusBar.viewInfo(home)
                + " " + visibilityToString(home.getVisibility())
                );
        pw.println("      rcnt: "
                + PhoneStatusBar.viewInfo(recent)
                + " " + visibilityToString(recent.getVisibility())
                );
        pw.println("      menu: "
                + PhoneStatusBar.viewInfo(menu)
                + " " + visibilityToString(menu.getVisibility())
                );
        pw.println("    }");
    }
}
