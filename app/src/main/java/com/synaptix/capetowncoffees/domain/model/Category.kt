package com.synaptix.capetowncoffees.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class Category(
    @StringRes val nameResId: Int,
    @DrawableRes val iconResId: Int
)
