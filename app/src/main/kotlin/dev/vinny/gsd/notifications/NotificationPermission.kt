package dev.vinny.gsd.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether the app holds the notification permission. It is a runtime permission only from Android 13;
 * on 12 and 12L (minSdk 31) it is granted at install, and asking for it shows no dialog, so the check
 * there must read granted rather than denied.
 */
fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
