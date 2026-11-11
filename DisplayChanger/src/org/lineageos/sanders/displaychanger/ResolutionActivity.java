/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.sanders.displaychanger;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Device-specific display resolution controls for Moto G (5S) Plus. */
public final class ResolutionActivity extends Activity {

    private static final String DISPLAY_SIZE_FORCED = "display_size_forced";
    private static final String DISPLAY_DENSITY_FORCED = "display_density_forced";
    private static final int DP_PER_INCH = 160;
    private static final long CONFIGURATION_REBUILD_DELAY_MS = 250;

    private static final ResolutionOption RESOLUTION_720P =
            new ResolutionOption(720, 1280, R.string.resolution_720p_title,
                    R.string.resolution_720p_size);
    private static final ResolutionOption RESOLUTION_1080P =
            new ResolutionOption(1080, 1920, R.string.resolution_1080p_title,
                    R.string.resolution_1080p_size);
    private static final ResolutionOption[] RESOLUTION_OPTIONS = {
            RESOLUTION_720P, RESOLUTION_1080P
    };

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<RadioButton> mResolutionButtons = new ArrayList<>();
    private final List<TextView> mSecondaryTextViews = new ArrayList<>();
    private ExecutorService mExecutor;
    private LinearLayout mResolutionCard;
    private LinearLayout mResolutionOptions;
    private View mResolutionDivider;
    private Button mApplyButton;
    private TextView mDescription;
    private ResolutionOption mCurrentOption;
    private ResolutionOption mPendingOption;
    private boolean mUpdatingSelection;
    private boolean mApplying;
    private boolean mDestroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExecutor = Executors.newSingleThreadExecutor();

        setTitle(R.string.screen_resolution_title);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rebuildContentView();
        updateSelection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mResolutionOptions != null && !mApplying) {
            applyMonetColors();
            updateSelection();
        }
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        mMainHandler.removeCallbacksAndMessages(null);
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mDestroyed) {
            // WindowManager applies the logical size and density as separate changes. Rebuilding
            // immediately can capture the intermediate size/old density pair and make all sp
            // text look oversized until the Activity is reopened. Coalesce both callbacks and
            // rebuild after the final display metrics have reached Resources.
            mMainHandler.removeCallbacks(mConfigurationRebuild);
            if (mApplying) {
                setResolutionOptionsEnabled(false);
            }
            mMainHandler.postDelayed(mConfigurationRebuild, CONFIGURATION_REBUILD_DELAY_MS);
        }
    }

    private final Runnable mConfigurationRebuild = new Runnable() {
        @Override
        public void run() {
            if (!isActivityAlive()) {
                return;
            }
            if (mApplying) {
                setResolutionOptionsEnabled(false);
                // Size and density are delivered separately by WindowManager. Wait for the
                // apply operation to finish before recreating the Activity, otherwise its
                // ActionBar could be rebuilt against the first, intermediate configuration.
                mMainHandler.postDelayed(this, 100);
            } else {
                // Recreate the complete Activity, including the framework ActionBar. Rebuilding
                // only the content leaves the title TextView with the old density after a
                // display change.
                recreate();
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Applies the framework's current Monet palette without requiring the app
     * to link directly against Android 12-only resource symbols. This keeps
     * the device tree buildable on older Lineage branches while following the
     * actual system palette on newer ones.
     */
    private void applyMonetColors() {
        final boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int background = getFrameworkColor(
                night ? "system_neutral1_900" : "system_neutral1_10",
                getThemeColor(android.R.attr.colorBackground,
                        night ? Color.BLACK : Color.WHITE));
        final int onSurface = getFrameworkColor(
                night ? "system_neutral1_100" : "system_neutral1_900",
                getThemeColor(android.R.attr.textColorPrimary,
                        night ? Color.WHITE : Color.BLACK));
        final int onSurfaceVariant = getFrameworkColor(
                night ? "system_neutral2_200" : "system_neutral2_700",
                getThemeColor(android.R.attr.textColorSecondary, onSurface));
        final int accent = getFrameworkColor(
                night ? "system_accent1_200" : "system_accent1_600",
                getThemeColor(android.R.attr.colorAccent,
                        getColor(android.R.color.holo_blue_light)));
        final int buttonAccent = getFrameworkColor("system_accent1_600",
                blendColors(background, accent, night ? 0.65f : 0.85f));

        final Window window = getWindow();
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        if (getActionBar() != null) {
            getActionBar().setBackgroundDrawable(new ColorDrawable(background));
        }

        int systemUiVisibility = window.getDecorView().getSystemUiVisibility();
        if (night) {
            systemUiVisibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            systemUiVisibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            systemUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            systemUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(systemUiVisibility);

        final View content = findViewById(android.R.id.content);
        content.setBackgroundColor(background);
        applyMonetColors(content, accent, buttonAccent, onSurface, onSurfaceVariant,
                background);
        applyResolutionCardBackground(background, onSurface);
        if (mResolutionDivider != null) {
            mResolutionDivider.setBackgroundColor(blendColors(background, onSurface, 0.20f));
        }
    }

    private void applyResolutionCardBackground(int background, int onSurface) {
        if (mResolutionCard == null) {
            return;
        }
        final GradientDrawable cardShape = new GradientDrawable();
        cardShape.setShape(GradientDrawable.RECTANGLE);
        cardShape.setCornerRadius(dp(20));
        cardShape.setColor(blendColors(background, onSurface, 0.08f));
        cardShape.setStroke(dp(1), blendColors(background, onSurface, 0.18f));
        mResolutionCard.setBackground(cardShape);
    }

    private void rebuildContentView() {
        // The display changer intentionally handles density and size changes itself so the
        // Settings entry remains stable during the transition. Recreate every dp/sp-based
        // view here; otherwise the existing hierarchy keeps pixel dimensions from the old
        // density and briefly appears miniature until the Activity is reopened.
        mResolutionButtons.clear();
        mSecondaryTextViews.clear();
        setContentView(createContentView());
        applyMonetColors();
    }

    private void applyMonetColors(View view, int accent, int buttonAccent, int onSurface,
            int onSurfaceVariant, int background) {
        // RadioButton extends Button on the framework. Handle it first so its
        // label keeps the readable surface color instead of receiving a solid
        // button background tint.
        if (view instanceof RadioButton) {
            final RadioButton radioButton = (RadioButton) view;
            radioButton.setButtonTintList(null);
            radioButton.setButtonDrawable(createRadioButtonDrawable(accent, onSurfaceVariant,
                    blendColors(background, onSurfaceVariant, 0.38f)));
            radioButton.setPadding(0, 0, 0, 0);
            radioButton.setMinWidth(0);
            radioButton.setMinimumWidth(0);
            radioButton.setTextColor(onSurfaceVariant);
        } else if (view instanceof Button) {
            final Button button = (Button) view;
            final int disabledBackground = blendColors(background, onSurface, 0.12f);
            final int disabledText = blendColors(background, onSurface, 0.38f);
            final GradientDrawable buttonShape = new GradientDrawable();
            buttonShape.setShape(GradientDrawable.RECTANGLE);
            buttonShape.setCornerRadius(dp(20));
            button.setBackground(buttonShape);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(dp(16), 0, dp(16), 0);
            button.setBackgroundTintList(new ColorStateList(
                    new int[][] {
                            new int[] {-android.R.attr.state_enabled},
                            new int[] {android.R.attr.state_pressed},
                            new int[] {}
                    },
                    new int[] {
                            disabledBackground,
                            blendColors(buttonAccent, Color.WHITE, 0.10f),
                            buttonAccent
                    }));
            button.setTextColor(new ColorStateList(
                    new int[][] {
                            new int[] {-android.R.attr.state_enabled},
                            new int[] {}
                    },
                    new int[] {disabledText, getContrastingTextColor(buttonAccent)}));
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(mSecondaryTextViews.contains(view)
                    ? onSurfaceVariant : onSurface);
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyMonetColors(group.getChildAt(i), accent, buttonAccent, onSurface,
                        onSurfaceVariant, background);
            }
        }
    }

    private Drawable createRadioButtonDrawable(int checkedColor, int uncheckedColor,
            int disabledColor) {
        final int radioInset = dp(10);
        final GradientDrawable checkedRing = createCircleDrawable(dp(20), Color.TRANSPARENT,
                checkedColor, dp(2));
        final GradientDrawable checkedDot = createCircleDrawable(dp(10), checkedColor,
                checkedColor, 0);
        final LayerDrawable checked = new LayerDrawable(new Drawable[] {
                checkedRing, checkedDot
        });
        checked.setLayerInset(0, radioInset, radioInset, radioInset, radioInset);
        checked.setLayerInset(1, dp(15), dp(15), dp(15), dp(15));

        final LayerDrawable unchecked = new LayerDrawable(new Drawable[] {
                createCircleDrawable(dp(20), Color.TRANSPARENT, uncheckedColor, dp(2))
        });
        unchecked.setLayerInset(0, radioInset, radioInset, radioInset, radioInset);

        final LayerDrawable disabled = new LayerDrawable(new Drawable[] {
                createCircleDrawable(dp(20), Color.TRANSPARENT, disabledColor, dp(2))
        });
        disabled.setLayerInset(0, radioInset, radioInset, radioInset, radioInset);

        final StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[] {-android.R.attr.state_enabled}, disabled);
        selector.addState(new int[] {android.R.attr.state_checked}, checked);
        selector.addState(new int[] {}, unchecked);
        return selector;
    }

    private GradientDrawable createCircleDrawable(int size, int fillColor, int strokeColor,
            int strokeWidth) {
        final GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(fillColor);
        if (strokeWidth > 0) {
            circle.setStroke(strokeWidth, strokeColor);
        }
        circle.setSize(size, size);
        return circle;
    }

    private int getFrameworkColor(String name, int fallback) {
        final int resourceId = getResources().getIdentifier(name, "color", "android");
        if (resourceId == 0) {
            return fallback;
        }
        try {
            return getResources().getColor(resourceId, getTheme());
        } catch (android.content.res.Resources.NotFoundException e) {
            return fallback;
        }
    }

    private int getThemeColor(int attribute, int fallback) {
        final TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId == 0) {
            return fallback;
        }
        try {
            return getResources().getColor(value.resourceId, getTheme());
        } catch (android.content.res.Resources.NotFoundException e) {
            return fallback;
        }
    }

    private int getContrastingTextColor(int background) {
        final double luminance = (0.299 * Color.red(background)
                + 0.587 * Color.green(background)
                + 0.114 * Color.blue(background)) / 255.0;
        return luminance > 0.55 ? Color.BLACK : Color.WHITE;
    }

    private int blendColors(int base, int overlay, float amount) {
        final float clampedAmount = Math.max(0.0f, Math.min(1.0f, amount));
        final int red = Math.round(Color.red(base)
                + (Color.red(overlay) - Color.red(base)) * clampedAmount);
        final int green = Math.round(Color.green(base)
                + (Color.green(overlay) - Color.green(base)) * clampedAmount);
        final int blue = Math.round(Color.blue(base)
                + (Color.blue(overlay) - Color.blue(base)) * clampedAmount);
        return Color.rgb(red, green, blue);
    }

    private View createContentView() {
        final ScrollView scrollView = new ScrollView(this);
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        final int padding = dp(24);
        final int topPadding = dp(20);
        content.setPadding(padding, topPadding, padding, dp(24));

        mDescription = new TextView(this);
        mDescription.setText(R.string.resolution_note);
        mDescription.setTextAppearance(android.R.style.TextAppearance_Material_Body2);
        mDescription.setLineSpacing(0, 1.05f);
        mSecondaryTextViews.add(mDescription);
        content.addView(mDescription, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mResolutionCard = new LinearLayout(this);
        mResolutionCard.setOrientation(LinearLayout.VERTICAL);
        mResolutionCard.setPadding(dp(16), dp(16), dp(16), dp(12));
        mResolutionCard.setElevation(dp(1));

        mResolutionOptions = new LinearLayout(this);
        mResolutionOptions.setOrientation(LinearLayout.HORIZONTAL);
        mResolutionOptions.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupParams.topMargin = dp(16);
        mResolutionCard.addView(mResolutionOptions, groupParams);

        for (ResolutionOption option : RESOLUTION_OPTIONS) {
            final LinearLayout optionView = new LinearLayout(this);
            optionView.setOrientation(LinearLayout.VERTICAL);
            optionView.setGravity(Gravity.CENTER_HORIZONTAL);
            optionView.setPadding(dp(8), dp(8), dp(8), dp(4));
            optionView.setTag(option);

            final TextView title = new TextView(this);
            title.setText(option.titleResId);
            title.setGravity(Gravity.CENTER);
            title.setTextAppearance(android.R.style.TextAppearance_Material_Subhead);
            optionView.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final TextView dimensions = new TextView(this);
            dimensions.setText(option.dimensionsResId);
            dimensions.setGravity(Gravity.CENTER);
            dimensions.setTextAppearance(android.R.style.TextAppearance_Material_Body2);
            mSecondaryTextViews.add(dimensions);
            optionView.addView(dimensions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final RadioButton selector = new RadioButton(this);
            selector.setId(View.generateViewId());
            selector.setTag(option);
            selector.setGravity(Gravity.CENTER);
            selector.setContentDescription(getString(option.titleResId) + " "
                    + getString(option.dimensionsResId));
            final LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(
                    dp(40), dp(40));
            optionView.addView(selector, selectorParams);
            mResolutionButtons.add(selector);

            final View.OnClickListener selectListener = view -> selectOption(option);
            optionView.setOnClickListener(selectListener);
            title.setOnClickListener(selectListener);
            dimensions.setOnClickListener(selectListener);
            selector.setOnClickListener(selectListener);

            final LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mResolutionOptions.addView(optionView, optionParams);

            if (option != RESOLUTION_OPTIONS[RESOLUTION_OPTIONS.length - 1]) {
                mResolutionDivider = new View(this);
                final LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        dp(1), dp(48));
                dividerParams.gravity = Gravity.CENTER_VERTICAL;
                dividerParams.setMargins(dp(8), 0, dp(8), 0);
                mResolutionOptions.addView(mResolutionDivider, dividerParams);
            }
        }

        final LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(24);
        content.addView(mResolutionCard, cardParams);

        mApplyButton = new Button(this);
        mApplyButton.setText(R.string.resolution_apply);
        mApplyButton.setAllCaps(false);
        mApplyButton.setMinWidth(0);
        mApplyButton.setMinimumWidth(0);
        mApplyButton.setGravity(Gravity.CENTER);
        mApplyButton.setEnabled(false);
        mApplyButton.setOnClickListener(view -> applyResolution(mPendingOption));
        final FrameLayout applyContainer = new FrameLayout(this);
        final LinearLayout.LayoutParams applyContainerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        applyContainerParams.topMargin = dp(16);
        applyContainer.addView(mApplyButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36), Gravity.CENTER));
        content.addView(applyContainer, applyContainerParams);

        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.post(() -> {
            applyActionBarInset(scrollView, content, topPadding);
            // On this framework the ActionBar can finish its first layout one frame after the
            // content view. Recheck after it is laid out so the warning never sits underneath it.
            scrollView.postDelayed(() -> applyActionBarInset(scrollView, content, topPadding),
                    100);
        });
        return scrollView;
    }

    private void applyActionBarInset(ScrollView scrollView, LinearLayout content,
            int baseTopPadding) {
        if (!isActivityAlive()) {
            return;
        }
        // The ActionBar container is a framework-internal resource and is not exposed through
        // android.R.id on the public API used to compile this privileged app.
        final int actionBarContainerId = getResources().getIdentifier(
                "action_bar_container", "id", "android");
        if (actionBarContainerId == 0) {
            return;
        }
        final View actionBarContainer = getWindow().getDecorView().findViewById(
                actionBarContainerId);
        if (actionBarContainer == null || actionBarContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        final int overlap = Math.max(0, actionBarContainer.getBottom() - scrollView.getTop());
        if (overlap > 0) {
            content.setPadding(content.getPaddingLeft(), baseTopPadding + overlap,
                    content.getPaddingRight(), content.getPaddingBottom());
        }
    }

    private void updateSelection() {
        if (!isActivityAlive() || mExecutor == null || mExecutor.isShutdown()) {
            return;
        }
        try {
            mExecutor.execute(() -> {
                final ResolutionOption selected = readCurrentResolution();
                mMainHandler.post(() -> {
                    if (!isActivityAlive()) {
                        return;
                    }
                    mUpdatingSelection = true;
                    mCurrentOption = selected;
                    mPendingOption = selected;
                    for (RadioButton button : mResolutionButtons) {
                        button.setChecked(button.getTag() == selected);
                    }
                    mUpdatingSelection = false;
                    updateApplyButton();
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The display change may have destroyed this Activity between the guard and
            // execute(). A stale callback must never bring the settings page down.
        }
    }

    private void updateApplyButton() {
        if (mApplyButton == null) {
            return;
        }
        mApplyButton.setEnabled(!mApplying && mPendingOption != null
                && mPendingOption != mCurrentOption);
    }

    private void selectOption(ResolutionOption option) {
        if (mUpdatingSelection || mApplying) {
            return;
        }
        mPendingOption = option;
        for (RadioButton button : mResolutionButtons) {
            button.setChecked(button.getTag() == option);
        }
        updateApplyButton();
    }

    private ResolutionOption readCurrentResolution() {
        try {
            final IWindowManager windowManager = IWindowManager.Stub.asInterface(
                    ServiceManager.getService(Context.WINDOW_SERVICE));
            if (windowManager != null) {
                final Point size = new Point();
                windowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, size);
                final ResolutionOption option = optionForDimensions(size.x, size.y);
                if (option != null) {
                    return option;
                }
            }
        } catch (RemoteException | RuntimeException ignored) {
            // Fall back to the legacy global setting on older framework branches.
        }

        return optionForForcedSize(Settings.Global.getString(
                getContentResolver(), DISPLAY_SIZE_FORCED));
    }

    private ResolutionOption optionForForcedSize(String forcedSize) {
        if (forcedSize == null || forcedSize.isEmpty()) {
            // The live base/initial display size is the source of truth. Do not infer a
            // resolution from a factory value when the framework setting is unavailable.
            return null;
        }
        for (ResolutionOption option : RESOLUTION_OPTIONS) {
            if (forcedSize.equals(option.width + "," + option.height)
                    || forcedSize.equals(option.height + "," + option.width)) {
                return option;
            }
        }
        return null;
    }

    private ResolutionOption optionForDimensions(int width, int height) {
        for (ResolutionOption option : RESOLUTION_OPTIONS) {
            if ((width == option.width && height == option.height)
                    || (width == option.height && height == option.width)) {
                return option;
            }
        }
        return null;
    }

    private void applyResolution(ResolutionOption option) {
        if (mApplying || option == null) {
            return;
        }
        mApplying = true;
        setResolutionOptionsEnabled(false);
        updateApplyButton();

        if (!isActivityAlive() || mExecutor == null || mExecutor.isShutdown()) {
            mApplying = false;
            setResolutionOptionsEnabled(true);
            updateApplyButton();
            return;
        }
        try {
            mExecutor.execute(() -> {
                try {
                    DisplayResolutionController.apply(this, option);
                    mMainHandler.post(() -> {
                        if (!isActivityAlive()) {
                            return;
                        }
                        mApplying = false;
                        setResolutionOptionsEnabled(true);
                        updateSelection();
                        Toast.makeText(this, R.string.resolution_applied,
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (RemoteException | RuntimeException e) {
                    mMainHandler.post(() -> {
                        if (!isActivityAlive()) {
                            return;
                        }
                        mApplying = false;
                        setResolutionOptionsEnabled(true);
                        updateSelection();
                        Toast.makeText(this, R.string.resolution_apply_error,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            mApplying = false;
            setResolutionOptionsEnabled(true);
            updateApplyButton();
        }
    }

    private boolean isActivityAlive() {
        return !mDestroyed && !isFinishing() && !isDestroyed();
    }

    private void setResolutionOptionsEnabled(boolean enabled) {
        if (mResolutionOptions != null) {
            mResolutionOptions.setEnabled(enabled);
        }
        for (RadioButton button : mResolutionButtons) {
            button.setEnabled(enabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ResolutionOption {
        final int width;
        final int height;
        final int titleResId;
        final int dimensionsResId;

        ResolutionOption(int width, int height, int titleResId, int dimensionsResId) {
            this.width = width;
            this.height = height;
            this.titleResId = titleResId;
            this.dimensionsResId = dimensionsResId;
        }
    }

    private static final class DisplayResolutionController {
        private DisplayResolutionController() {
        }

        static void apply(Context context, ResolutionOption option) throws RemoteException {
            final IWindowManager windowManager = IWindowManager.Stub.asInterface(
                    ServiceManager.getService(Context.WINDOW_SERVICE));
            if (windowManager == null) {
                throw new IllegalStateException("Window manager is unavailable");
            }

            // Always sample the live values immediately before changing anything. This is
            // intentional: the user may have changed "Smallest width" in Developer options
            // since the last time this activity was opened.
            final DisplayState current = readCurrentState(context, windowManager);
            final Point targetSize = new Point(option.width, option.height);
            final Point initialSize = new Point();
            windowManager.getInitialDisplaySize(Display.DEFAULT_DISPLAY, initialSize);
            validateDisplaySize(targetSize);
            validateDisplaySize(initialSize);

            final int targetSmallestDimension = Math.min(targetSize.x, targetSize.y);
            final int targetDensity = calculatePreservedDensity(
                    current, targetSmallestDimension);

            // WindowManager applies size and density as separate display configurations. Apply
            // first the change that makes the intermediate UI smaller, avoiding an oversized
            // SystemUI while the second configuration is still being propagated.
            if (targetSmallestDimension < current.getSmallestDimension()) {
                // Going to 720p: lower density first, then reduce the logical size.
                setForcedDensity(windowManager, targetDensity);
                applyDisplaySize(windowManager, targetSize, initialSize);
            } else {
                // Restoring 1080p: increase the logical size first, then restore density.
                applyDisplaySize(windowManager, targetSize, initialSize);
                setForcedDensity(windowManager, targetDensity);
            }
            context.getContentResolver().notifyChange(DisplaySummaryProvider.SUMMARY_URI, null);
        }

        private static void setForcedDensity(IWindowManager windowManager, int density)
                throws RemoteException {
            // Density is user-scoped because Developer options' smallest-width preference is
            // user-scoped as well. Keeping this override on restore preserves that preference.
            windowManager.setForcedDisplayDensityForUser(
                    Display.DEFAULT_DISPLAY, density, UserHandle.myUserId());
        }

        private static void applyDisplaySize(IWindowManager windowManager, Point targetSize,
                Point initialSize) throws RemoteException {
            // Selecting the native mode is the same as Samsung's original-resolution choice:
            // clear the logical size override instead of forcing the factory dimensions.
            if (targetSize.equals(initialSize)) {
                windowManager.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
            } else {
                windowManager.setForcedDisplaySize(Display.DEFAULT_DISPLAY, targetSize.x,
                        targetSize.y);
            }
        }

        private static DisplayState readCurrentState(Context context,
                IWindowManager windowManager) throws RemoteException {
            final Point size = new Point();
            windowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, size);
            if (!isValidDisplaySize(size)) {
                final Point forcedSize = parseDisplaySize(Settings.Global.getString(
                        context.getContentResolver(), DISPLAY_SIZE_FORCED));
                if (forcedSize != null) {
                    size.set(forcedSize.x, forcedSize.y);
                }
            }
            if (!isValidDisplaySize(size)) {
                final android.util.DisplayMetrics metrics =
                        context.getResources().getDisplayMetrics();
                size.set(metrics.widthPixels, metrics.heightPixels);
            }

            int densityDpi = windowManager.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
            if (densityDpi <= 0) {
                densityDpi = parsePositiveInteger(Settings.Secure.getStringForUser(
                        context.getContentResolver(), DISPLAY_DENSITY_FORCED,
                        UserHandle.myUserId()));
            }
            if (densityDpi <= 0) {
                densityDpi = context.getResources().getConfiguration().densityDpi;
            }
            if (densityDpi <= 0) {
                densityDpi = Math.round(
                        context.getResources().getDisplayMetrics().density * DP_PER_INCH);
            }

            validateDisplaySize(size);
            if (densityDpi <= 0) {
                throw new IllegalStateException("Current display density is unavailable");
            }
            return new DisplayState(size.x, size.y, densityDpi);
        }

        private static int calculatePreservedDensity(DisplayState current,
                int targetSmallestDimension) {
            final int currentSmallestDimension = current.getSmallestDimension();
            final float currentSmallestWidthDp = current.getSmallestWidthDp();
            if (currentSmallestDimension <= 0 || targetSmallestDimension <= 0
                    || !Float.isFinite(currentSmallestWidthDp)
                    || currentSmallestWidthDp <= 0) {
                throw new IllegalArgumentException("Invalid display dimensions");
            }

            // Current smallest width is currentSmallestDimension * 160 / currentDensity.
            // Solving for the density at the target size gives the requested formula:
            // targetDensity = currentDensity * targetSmallest / currentSmallest.
            final double targetDensity = current.densityDpi
                    * ((double) targetSmallestDimension / currentSmallestDimension);
            if (!Double.isFinite(targetDensity) || targetDensity > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Calculated display density is invalid");
            }
            return Math.max(1, (int) Math.round(targetDensity));
        }

        private static void validateDisplaySize(Point size) {
            if (!isValidDisplaySize(size)) {
                throw new IllegalStateException("Logical display size is unavailable");
            }
        }

        private static boolean isValidDisplaySize(Point size) {
            return size != null && size.x > 0 && size.y > 0;
        }

        private static Point parseDisplaySize(String forcedSize) {
            if (forcedSize == null || forcedSize.isEmpty()) {
                return null;
            }
            final int separator = forcedSize.indexOf(',');
            if (separator <= 0 || separator == forcedSize.length() - 1
                    || forcedSize.lastIndexOf(',') != separator) {
                return null;
            }
            try {
                final int width = Integer.parseInt(forcedSize.substring(0, separator));
                final int height = Integer.parseInt(forcedSize.substring(separator + 1));
                final Point size = new Point(width, height);
                return isValidDisplaySize(size) ? size : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static int parsePositiveInteger(String value) {
            if (value == null || value.isEmpty()) {
                return -1;
            }
            try {
                final int parsed = Integer.parseInt(value);
                return parsed > 0 ? parsed : -1;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        private static final class DisplayState {
            final int width;
            final int height;
            final int densityDpi;

            DisplayState(int width, int height, int densityDpi) {
                this.width = width;
                this.height = height;
                this.densityDpi = densityDpi;
            }

            int getSmallestDimension() {
                return Math.min(width, height);
            }

            float getSmallestWidthDp() {
                return getSmallestDimension() * (float) DP_PER_INCH / densityDpi;
            }
        }
    }
}
