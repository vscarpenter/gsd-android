package dev.vinny.gsd.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import dev.vinny.gsd.R

/**
 * Starts an email to support (spec §8.17, About), addressed and with a subject. With no email app
 * installed, it says so instead of crashing.
 */
fun openSupportEmail(context: Context, address: String) {
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address?subject=" + Uri.encode("GSD Task Manager Support")))
    try {
        context.startActivity(mail)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
    }
}

/** Opens a web page in a Custom Tab. With no browser installed, it says so instead of crashing. */
fun openInBrowser(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
    }
}
