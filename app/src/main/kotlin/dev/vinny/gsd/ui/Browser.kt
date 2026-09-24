package dev.vinny.gsd.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import dev.vinny.gsd.R

/** Opens a web page in a Custom Tab. With no browser installed, it says so instead of crashing. */
fun openInBrowser(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
    }
}
