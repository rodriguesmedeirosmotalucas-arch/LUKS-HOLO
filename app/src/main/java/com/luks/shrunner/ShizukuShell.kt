
package com.luks.shrunner

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuShell {

    fun hasPermission(): Boolean {
        return Shizuku.checkSelfPermission() ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        if (!hasPermission()) {
            Shizuku.requestPermission(1001)
        }
    }
}
