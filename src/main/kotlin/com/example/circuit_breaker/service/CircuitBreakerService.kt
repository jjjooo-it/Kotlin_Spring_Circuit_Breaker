package com.spring.circuit.breaker.service

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.atomic.AtomicInteger

@Service
class CircuitBreakerService(
    private val webClient: WebClient = WebClient.create("http://localhost:10001")
) {
    companion object {
        var successCount: AtomicInteger = AtomicInteger(0)
        var error400Count: AtomicInteger = AtomicInteger(0)
        var error500Count: AtomicInteger = AtomicInteger(0)
        var blockedRequestCount: AtomicInteger = AtomicInteger(0)
        var totalRequestCount: AtomicInteger = AtomicInteger(0)


        const val FAILURE_RATE_THRESHOLD_1 = 0.15
        const val FAILURE_RATE_THRESHOLD_2 = 0.20
        const val FAILURE_RATE_THRESHOLD_3 = 0.25
    }

    @CircuitBreaker(name = "circuitBreaker", fallbackMethod = "fallback")
    fun request(): Mono<String> {
        totalRequestCount.incrementAndGet()

        return webClient.get()
            .uri("/api/random-error")
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnTerminate {
                successCount.incrementAndGet()
                println("Success")
            }
            .onErrorResume(WebClientResponseException::class.java) { e ->
                when (e.statusCode.value()) {
                    400 -> {
                        error400Count.incrementAndGet()
                        println("400 error (ignored)")
                        Mono.empty()
                    }
                    500 -> {
                        error500Count.incrementAndGet()
                        println("500 error (triggering circuit breaker)")
                        triggerCircuitBreaker()
                        Mono.empty()
                    }
                    else -> {
                        println("Other error: ${e.statusCode}")
                        Mono.empty()
                    }
                }
            }
    }

    private fun triggerCircuitBreaker() {
        val failureRate = calculateFailureRate()
        println("Current Failure Rate: $failureRate")

        when {
            failureRate > FAILURE_RATE_THRESHOLD_3 -> {
                println("Failure rate is too high (> 25%). Circuit Breaker triggered.")
                blockedRequestCount.incrementAndGet()
            }
            failureRate > FAILURE_RATE_THRESHOLD_2 -> {
                println("Failure rate is too high (> 20%). Circuit Breaker triggered.")
                blockedRequestCount.incrementAndGet()
            }
            failureRate > FAILURE_RATE_THRESHOLD_1 -> {
                println("Failure rate is too high (> 15%). Circuit Breaker triggered.")
                blockedRequestCount.incrementAndGet()
            }
        }
    }

    private fun calculateFailureRate(): Double {
        val totalErrors = error500Count.get() + error400Count.get()
        return if (totalRequestCount.get() > 0) {
            totalErrors.toDouble() / totalRequestCount.get().toDouble()
        } else {
            0.0
        }
    }

    private fun fallback(exception: CallNotPermittedException): Mono<Void> {
        blockedRequestCount.incrementAndGet()
        println("Request blocked due to circuit breaker")
        return Mono.empty()
    }

    fun makeMultipleRequests() {
        repeat(1000) {
            request().subscribe()
        }
    }

    fun printStatus() {
        println("===== CURRENT STATUS =====")
        println("Total Requests: $totalRequestCount")
        println("Successful Requests: $successCount")
        println("400 Error Requests (ignored): $error400Count")
        println("500 Error Requests (triggered circuit breaker): $error500Count")
        println("Blocked Requests (due to circuit breaker): $blockedRequestCount")
    }
}
