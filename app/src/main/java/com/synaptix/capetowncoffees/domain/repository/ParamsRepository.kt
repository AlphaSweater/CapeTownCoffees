package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import kotlinx.coroutines.flow.StateFlow

interface ParamsRepository {
    val params: StateFlow<CoffeeSearchParameters>

    suspend fun setRadius(meters: Int)
    suspend fun setStrict(onlyCoffee: Boolean)
    suspend fun setQuery(q: String?)
    suspend fun setAll(next: CoffeeSearchParameters)

    /** Functional update, handy when doing multiple changes atomically */
    suspend fun update(transform: (CoffeeSearchParameters) -> CoffeeSearchParameters)
}