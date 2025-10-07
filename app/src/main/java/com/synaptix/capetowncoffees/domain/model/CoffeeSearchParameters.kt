package com.synaptix.capetowncoffees.domain.model

/**
 * User-editable search parameters for finding coffee places.
 * Includes builder/helper methods and validation.
 */
data class CoffeeSearchParameters(
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val query: String? = DEFAULT_QUERY,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val sortByDistance: Boolean = true,
    val strictCoffeeOnly: Boolean = true
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 30_000
        const val DEFAULT_QUERY = ""
        const val DEFAULT_MAX_RESULTS = 20

        fun builder() = Builder()
    }

    class Builder {
        private var radiusMeters: Int = DEFAULT_RADIUS_METERS
        private var query: String? = DEFAULT_QUERY
        private var maxResults: Int = DEFAULT_MAX_RESULTS
        private var sortByDistance: Boolean = true
        private var strictCoffeeOnly: Boolean = true

        fun radiusMeters(value: Int) = apply { radiusMeters = value }
        fun query(value: String?) = apply { query = value }
        fun maxResults(value: Int) = apply { maxResults = value }
        fun sortByDistance(value: Boolean) = apply { sortByDistance = value }
        fun strictCoffeeOnly(value: Boolean) = apply { strictCoffeeOnly = value }

        fun build(): CoffeeSearchParameters {
            require(radiusMeters in 100..50_000) { "Radius must be between 100 and 50,000 meters." }
            require(maxResults in 1..100) { "Max results must be between 1 and 100." }
            return CoffeeSearchParameters(
                radiusMeters,
                query,
                maxResults,
                sortByDistance,
                strictCoffeeOnly
            )
        }
    }
}

