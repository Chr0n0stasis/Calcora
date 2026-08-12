package android.net

import platform.Foundation.NSURL

class Uri private constructor(internal val url: NSURL?) {
    companion object {
        fun parse(value: String) = Uri(NSURL.URLWithString(value))
    }
}
