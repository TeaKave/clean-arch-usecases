package com.arch.domain.usecases

import com.arch.domain.Result

/**
 * Abstract class for a Use Case (Interactor in terms of Clean Architecture).
 * This interface represents an execution unit for different use cases (this means any use case
 * in the application should implement this contract).
 *
 * Use cases are the entry points to the domain layer.
 *
 */
abstract class UseCaseResult<out T : Any, in Params> : UseCase<Result<T>, Params>()