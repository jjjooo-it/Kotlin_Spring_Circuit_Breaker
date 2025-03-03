package com.spring.circuit.breaker.controller

import com.spring.circuit.breaker.service.CircuitBreakerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/circuit-breaker")
class CircuitBreakerController(
    private val circuitBreakerService: CircuitBreakerService
) {

    @GetMapping("/test")
    fun test(): String {
        circuitBreakerService.makeMultipleRequests()
        return "테스트 완료"
    }

    @GetMapping("/status")
    fun status(): String {
        circuitBreakerService.printStatus()
        return "상태 출력 완료"
    }
}
