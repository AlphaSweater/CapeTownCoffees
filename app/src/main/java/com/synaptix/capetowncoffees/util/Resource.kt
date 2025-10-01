package com.synaptix.capetowncoffees.util

/**
 * A generic sealed class that represents a resource (like database or network) that can be in one of three states:
 * - Success: Contains the data of type [T]
 * - Error: Contains an error message
 * - Loading: Represents a loading state
 * 
 * @param T The type of data that will be wrapped by this Resource
 * 
 * Example usage:
 * ```
 * // In ViewModel
 * private val _userState = MutableStateFlow<Resource<User>>(Resource.Loading)
 * val userState: StateFlow<Resource<User>> = _userState.asStateFlow()
 * 
 * // In Fragment/Activity
 * viewModel.userState.collect { resource ->
 *     when (resource) {
 *         is Resource.Loading -> showLoading()
 *         is Resource.Success -> showUser(resource.data)
 *         is Resource.Error -> showError(resource.message)
 *     }
 * }
 * ```
 */
sealed class Resource<out T> {
    /**
     * Represents a successful operation containing the [data]
     */
    data class Success<out T>(val data: T) : Resource<T>()
    
    /**
     * Represents a failed operation with an error [message]
     */
    data class Error(val message: String) : Resource<Nothing>()
    
    /**
     * Represents a loading state
     */
    object Loading : Resource<Nothing>()
    
    /**
     * Returns the data if this is a [Success] resource, or null otherwise
     */
    fun getOrNull(): T? = if (this is Success) data else null
    
    /**
     * Returns the data if this is a [Success] resource, or throws an exception otherwise
     * @throws IllegalStateException if this is not a [Success] resource
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("Resource is in error state: $message")
        Loading -> throw IllegalStateException("Resource is still loading")
    }
    
    /**
     * Returns the error message if this is an [Error] resource, or null otherwise
     */
    fun errorMessageOrNull(): String? = if (this is Error) message else null
    
    /**
     * Returns true if this is a [Success] resource
     */
    fun isSuccess(): Boolean = this is Success<T>
    
    /**
     * Returns true if this is an [Error] resource
     */
    fun isError(): Boolean = this is Error
    
    /**
     * Returns true if this is a [Loading] resource
     */
    fun isLoading(): Boolean = this is Loading
    
    /**
     * Maps the data of a [Success] resource using the given [transform] function
     */
    inline fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        Loading -> Loading
    }
}

/**
 * Creates a [Resource.Success] with the given [data]
 */
fun <T> successOf(data: T): Resource<T> = Resource.Success(data)

/**
 * Creates a [Resource.Error] with the given [message]
 */
fun <T> errorOf(message: String): Resource<T> = Resource.Error(message)

/**
 * Creates a [Resource.Loading]
 */
fun <T> loadingResource(): Resource<T> = Resource.Loading
