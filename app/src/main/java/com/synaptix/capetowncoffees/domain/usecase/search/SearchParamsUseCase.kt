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

package com.synaptix.capetowncoffees.domain.usecase.search

import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchParamsUseCase @Inject constructor() {

    private val ref = AtomicReference(CoffeeSearchParameters())

    /** Snapshot of current params. */
    fun current(): CoffeeSearchParameters = ref.get()

    fun setRadius(meters: Int) {
        require(meters in 100..50_000) { "Radius must be between 100 and 50,000 meters." }
        ref.updateAndGet { it.copy(radiusMeters = meters) }
    }

    fun setStrict(onlyCoffee: Boolean) {
        ref.updateAndGet { it.copy(strictCoffeeOnly = onlyCoffee) }
    }

    fun setQuery(q: String?) {
        ref.updateAndGet { it.copy(query = q.orEmpty()) }
    }

    fun setAll(next: CoffeeSearchParameters) {
        require(next.radiusMeters in 100..50_000)
        require(next.maxResults in 1..100)
        ref.set(next)
    }

    /** Params for Near list (inherit radius/strict; ignore query; section caps). */
    fun forNear(maxResults: Int, sortByDistance: Boolean = true): CoffeeSearchParameters =
        current().copy(query = "", maxResults = maxResults, sortByDistance = sortByDistance)

    /** Params for Featured list (inherit radius/strict; ignore query; section caps). */
    fun forFeatured(maxResults: Int, sortByDistance: Boolean = false): CoffeeSearchParameters =
        current().copy(query = "", maxResults = maxResults, sortByDistance = sortByDistance)
}