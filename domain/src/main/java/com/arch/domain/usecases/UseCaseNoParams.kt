package com.arch.domain.usecases

/**
 * Abstract class for a Use Case (Interactor in terms of Clean Architecture).
 * This interface represents an execution unit for different use cases (this means any use case
 * in the application should implement this contract).
 *
 * Use cases are the entry points to the domain layer.
 *
 */
abstract class UseCaseNoParams<out T> : UseCase<T, Unit>() {

    /**
     * Executes appropriate implementation of [UseCase],
     * @return type [T] of parameter. In the most common way the [T] is wrapped to a special use-case implementation.
     */
    suspend operator fun invoke(): T = super.invoke(Unit)
}