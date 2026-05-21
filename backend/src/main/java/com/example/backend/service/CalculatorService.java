package com.example.backend.service;

import com.example.backend.model.CalculationRequest;
import com.example.backend.model.CalculationResponse;
import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

	public CalculationResponse add(CalculationRequest request) {
		return response("add", request, request.firstNumber() + request.secondNumber(), "Operation completed successfully.");
	}

	public CalculationResponse subtract(CalculationRequest request) {
		return response("subtract", request, request.firstNumber() - request.secondNumber(), "Operation completed successfully.");
	}

	public CalculationResponse multiply(CalculationRequest request) {
		return response("multiply", request, request.firstNumber() * request.secondNumber(), "Operation completed successfully.");
	}

	public CalculationResponse divide(CalculationRequest request) {
		if (request.secondNumber() == 0.0d) {
			throw new IllegalArgumentException("Division by zero is not allowed.");
		}

		return response("divide", request, request.firstNumber() / request.secondNumber(), "Operation completed successfully.");
	}

	private CalculationResponse response(String operation, CalculationRequest request, double result, String message) {
		return new CalculationResponse(operation, request.firstNumber(), request.secondNumber(), result, message);
	}
}