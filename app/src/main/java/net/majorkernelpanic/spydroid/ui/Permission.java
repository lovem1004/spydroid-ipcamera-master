package net.majorkernelpanic.spydroid.ui;

/**
 * Created by dengfengwang on 17-10-26.
 */

/*
 * *************************************************************************
 *  Permissions.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import net.majorkernelpanic.spydroid.R;
import net.majorkernelpanic.spydroid.SpydroidApplication;


public class Permission {

    public static final int PERMISSION_STORAGE_TAG = 255;
    public static final int PERMISSION_SETTINGS_TAG = 254;


    public static final int PERMISSION_SYSTEM_RINGTONE = 42;
    public static final int PERMISSION_SYSTEM_BRIGHTNESS = 43;
    public static final int PERMISSION_SYSTEM_DRAW_OVRLAYS = 44;

    /*
     * Marshmallow permission system management
     */

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean canDrawOverlays(Context context) {
        return !isMarshMallowOrLater() || Settings.canDrawOverlays(context);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean canWriteSettings(Context context) {
        return !isMarshMallowOrLater() || Settings.System.canWrite(context);
    }

    public static void checkDrawOverlaysPermission(Activity activity) {
        if (isMarshMallowOrLater() && !canDrawOverlays(activity)) {
            showSettingsPermissionDialog(activity, PERMISSION_SYSTEM_DRAW_OVRLAYS);
        }
    }

    public static void checkWriteSettingsPermission(Activity activity, int mode) {
        if (isMarshMallowOrLater() && !canWriteSettings(activity)) {
            showSettingsPermissionDialog(activity, mode);
        }
    }

    private static Dialog sAlertDialog;

    public static void showSettingsPermissionDialog(final Activity activity, int mode) {
        if (activity.isFinishing() || (sAlertDialog != null && sAlertDialog.isShowing()))
            return;
        sAlertDialog = createSettingsDialogCompat(activity, mode);
    }

    private static Dialog createSettingsDialogCompat(final Activity activity, int mode) {
        int titleId = 0, textId = 0;
        String action = Settings.ACTION_MANAGE_WRITE_SETTINGS;
        switch (mode) {
            case PERMISSION_SYSTEM_DRAW_OVRLAYS:
                titleId = R.string.allow_draw_overlays_title;
                textId = R.string.allow_sdraw_overlays_description;
                action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION;
                break;
        }
        final String finalAction = action;
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(titleId))
                .setMessage(activity.getString(textId))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                        Intent i = new Intent(finalAction);
                        i.setData(Uri.parse("package:" + activity.getPackageName()));
                        try {
                            activity.startActivity(i);
                        } catch (Exception ex) {}
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("user_declined_settings_access", true);
                        commitPreferences(editor);
                    }
                });
        return dialogBuilder.show();
    }

    public static boolean isFroyoOrLater() {
        return Build.VERSION.SDK_INT >= 8;
    }

    public static boolean isGingerbreadOrLater() {
        return Build.VERSION.SDK_INT >= 9;
    }

    public static boolean isHoneycombOrLater() {
        return Build.VERSION.SDK_INT >= 11;
    }

    public static boolean isHoneycombMr1OrLater() {
        return Build.VERSION.SDK_INT >= 12;
    }

    public static boolean isHoneycombMr2OrLater() {
        return Build.VERSION.SDK_INT >= 13;
    }

    public static boolean isICSOrLater() {
        return Build.VERSION.SDK_INT >= 14;
    }

    public static boolean isJellyBeanOrLater() {
        return Build.VERSION.SDK_INT >= 16;
    }

    public static boolean isJellyBeanMR1OrLater() {
        return Build.VERSION.SDK_INT >= 17;
    }

    public static boolean isJellyBeanMR2OrLater() {
        return Build.VERSION.SDK_INT >= 18;
    }

    public static boolean isKitKatOrLater() {
        return Build.VERSION.SDK_INT >= 19;
    }

    public static boolean isLolliPopOrLater() {
        return Build.VERSION.SDK_INT >= 21;
    }

    public static boolean isMarshMallowOrLater() {
        return Build.VERSION.SDK_INT >= 23;
    }

    public static void commitPreferences(SharedPreferences.Editor editor){
        if (isGingerbreadOrLater())
            editor.apply();
        else
            editor.commit();
    }
}

