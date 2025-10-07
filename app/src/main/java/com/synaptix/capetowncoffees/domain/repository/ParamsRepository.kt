//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

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