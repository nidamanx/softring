// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.view.View
import android.widget.TextView

/** About page: what SoftRing does and does not do, battery, privacy, permissions, author. */
class InfoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        EdgeToEdge.apply(this, findViewById(R.id.root))

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.info_version).text = getString(R.string.info_version, versionName())
        showBullets(R.id.info_never, R.string.info_never_media, R.string.info_never_vibrate, R.string.info_never_raise)
        showBullets(R.id.info_permissions, R.string.info_perm_dnd, R.string.info_perm_exact, R.string.info_perm_fgs)
        findViewById<TextView>(R.id.info_author).text = getString(R.string.info_author, AUTHOR)
        findViewById<TextView>(R.id.info_source).apply {
            text = getString(R.string.info_source, SOURCE_URL.removePrefix("https://"))
            setOnClickListener { openSource() }
        }
    }

    /** A bulleted list: wrapped lines line up with the text, not with the bullet. */
    private fun showBullets(viewId: Int, vararg ids: Int) {
        val view = findViewById<TextView>(viewId)
        val density = resources.displayMetrics.density
        val gap = (BULLET_GAP_DP * density).toInt()
        val list = SpannableStringBuilder()
        ids.forEachIndexed { index, id ->
            val start = list.length
            list.append(getString(id))
            if (index < ids.lastIndex) list.append("\n")
            val bullet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                BulletSpan(gap, view.currentTextColor, (BULLET_RADIUS_DP * density).toInt())
            } else {
                BulletSpan(gap)
            }
            list.setSpan(bullet, start, list.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.text = list
    }

    private fun versionName(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return info.versionName.orEmpty()
    }

    // The browser opens the page: SoftRing itself has no internet access
    private fun openSource() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
        } catch (e: ActivityNotFoundException) {
            // No browser installed: the address stays readable on screen
        }
    }

    private companion object {
        const val BULLET_GAP_DP = 10
        const val BULLET_RADIUS_DP = 2.5f
        const val AUTHOR = "Nicola Davide Mannarelli"
        const val SOURCE_URL = "https://github.com/nidamanx/softring"
    }
}
