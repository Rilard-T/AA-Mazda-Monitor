package com.aatorque.stats

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

abstract class CarFragment : Fragment() {

    var title: String? = null
    fun setTitle(@StringRes resId: Int) {
        title = requireContext().getString(resId)
    }
}