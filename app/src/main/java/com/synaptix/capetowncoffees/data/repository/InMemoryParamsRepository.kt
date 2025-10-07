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
//* ChatGPT was used to clarify repository patterns, data source integration, and best
//practices for separating data access logic from UI components.
//* It also provided suggestions to improve maintainability and consistency.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.repository

import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.repository.ParamsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryParamsRepository @Inject constructor() : ParamsRepository {

    private val _params = MutableStateFlow(CoffeeSearchParameters())
    override val params: StateFlow<CoffeeSearchParameters> = _params

    override suspend fun setRadius(meters: Int) {
        _params.update { it.copy(radiusMeters = meters.coerceIn(100, 50_000)) }
    }

    override suspend fun setStrict(onlyCoffee: Boolean) {
        _params.update { it.copy(strictCoffeeOnly = onlyCoffee) }
    }

    override suspend fun setQuery(q: String?) {
        _params.update { it.copy(query = q.orEmpty()) }
    }

    override suspend fun setAll(next: CoffeeSearchParameters) {
        _params.value = next
    }

    override suspend fun update(transform: (CoffeeSearchParameters) -> CoffeeSearchParameters) {
        _params.update(transform)
    }
}