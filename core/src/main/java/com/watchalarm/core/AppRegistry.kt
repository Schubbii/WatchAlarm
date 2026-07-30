package com.watchalarm.core

import android.app.Activity

/**
 * Von der jeweiligen App (Handy/Uhr) in der Application-Klasse befüllt,
 * damit der gemeinsame [AlarmService] die formfaktor-spezifische
 * Klingel-Activity starten kann.
 */
object AppRegistry {
    var ringActivityClass: Class<out Activity>? = null
}
