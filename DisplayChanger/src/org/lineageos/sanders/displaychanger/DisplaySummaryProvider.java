/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.sanders.displaychanger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.view.Display;
import android.view.IWindowManager;

/** Supplies the current logical resolution to the Settings injected tile. */
public final class DisplaySummaryProvider extends ContentProvider {

    static final Uri SUMMARY_URI = Uri.parse(
            "content://org.lineageos.sanders.displaychanger.summary/get_summary");
    private static final String METHOD_GET_SUMMARY = "get_summary";
    private static final String METHOD_GET_DYNAMIC_SUMMARY = "get_dynamic_summary";
    private static final String SETTINGS_SUMMARY = "com.android.settings.summary";
    private static final String DISPLAY_SIZE_FORCED = "display_size_forced";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_GET_SUMMARY.equals(method) && !METHOD_GET_DYNAMIC_SUMMARY.equals(method)) {
            return super.call(method, arg, extras);
        }

        final Context context = getContext();
        if (context == null) {
            return null;
        }

        final Point size = readCurrentSize(context);
        if (size == null) {
            return null;
        }

        final Bundle result = new Bundle();
        result.putString(SETTINGS_SUMMARY, context.getString(
                R.string.settings_summary_resolution, size.x, size.y));
        return result;
    }

    private Point readCurrentSize(Context context) {
        final IWindowManager windowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
        if (windowManager != null) {
            try {
                final Point size = new Point();
                windowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, size);
                if (isValid(size)) {
                    return size;
                }
            } catch (RemoteException | RuntimeException ignored) {
                // Fall through to the legacy setting and display metrics.
            }
        }

        final Point forcedSize = parseDisplaySize(Settings.Global.getString(
                context.getContentResolver(), DISPLAY_SIZE_FORCED));
        if (forcedSize != null) {
            return forcedSize;
        }

        final android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final Point metricsSize = new Point(metrics.widthPixels, metrics.heightPixels);
        return isValid(metricsSize) ? metricsSize : null;
    }

    private Point parseDisplaySize(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        final int separator = value.indexOf(',');
        if (separator <= 0 || separator == value.length() - 1
                || value.lastIndexOf(',') != separator) {
            return null;
        }
        try {
            final Point size = new Point(
                    Integer.parseInt(value.substring(0, separator)),
                    Integer.parseInt(value.substring(separator + 1)));
            return isValid(size) ? size : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isValid(Point size) {
        return size != null && size.x > 0 && size.y > 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
