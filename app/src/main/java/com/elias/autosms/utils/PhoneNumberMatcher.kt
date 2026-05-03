package com.elias.autosms.utils

import android.telephony.PhoneNumberUtils

object PhoneNumberMatcher {

    // True when two numbers represent the same subscriber, allowing for
    // formatting differences (spaces, dashes, country code presence).
    // Falls back to suffix matching if PhoneNumberUtils' compare returns false,
    // which is the common case for internationally-formatted SMS notifications.
    fun matches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (PhoneNumberUtils.compare(a, b)) return true
        val da = digitsOnly(a)
        val db = digitsOnly(b)
        if (da.isEmpty() || db.isEmpty()) return false
        // Last 7 digits is the safest cross-country comparison; it tolerates
        // different country prefixes and trunk codes while staying specific.
        val len = minOf(7, da.length, db.length)
        return da.takeLast(len) == db.takeLast(len)
    }

    fun digitsOnly(input: String): String =
            input.filter { it.isDigit() }
}
