package com.synaptix.capetowncoffees.utils

//sealed class MyResult<out T> {
//    data class Success<T>(val data: T) : MyResult<T>()
//    data class Error(val exception: Exception) : MyResult<Nothing>()
//}

// Extension function to safely unwrap a Result or return early with a custom error
//inline fun <T> MyResult<T>.getOrReturn(onError: (String) -> Nothing): T {
//    return when (this) {
//        is MyResult.Success -> data
//        is MyResult.Error -> onError(exception.message ?: "Unknown error")
//    }
//}