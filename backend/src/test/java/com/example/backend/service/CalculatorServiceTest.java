package com.example.backend.service;

import com.example.backend.model.CalculationRequest;
import com.example.backend.model.CalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorServiceTest {

	private final CalculatorService calculatorService = new CalculatorService();

	@Test
	void addShouldReturnSum() {
		CalculationResponse response = calculatorService.add(new CalculationRequest(10, 5));

		assertEquals("add", response.operation());
		assertEquals(15.0, response.result());
	}

	@Test
	void divideShouldRejectZero() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> calculatorService.divide(new CalculationRequest(10, 0)));

		assertEquals("Division by zero is not allowed.", exception.getMessage());
	}
}