package com.taigashinohara.mochimori

import android.app.Application

/**
 * App entry point.
 *
 * v0.3.9: Material You Dynamic Colors are NO LONGER applied. The
 * v0.3.8 real-device screenshot showed why: the foundation resolved
 * its gradient stops and lights from theme roles, so the user's
 * wallpaper colours (pink / brown) flooded the whole background —
 * the navy art direction disappeared, the gradient turned muddy,
 * and the bright light behind the bottom navigation washed the
 * unselected tab labels out to near-invisibility. The art direction
 * is a fixed palette (deep navy + soft blue-white); the whole app
 * now uses it as designed, on every device. See
 * docs/GLASS_REVIEW_v0.3.8.md.
 */
class MochiApp : Application()
