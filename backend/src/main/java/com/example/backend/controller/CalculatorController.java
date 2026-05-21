package com.example.backend.controller;

import com.example.backend.model.CalculationRequest;
import com.example.backend.model.CalculationResponse;
import com.example.backend.service.CalculatorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculatrice")
public class CalculatorController {

	private final CalculatorService calculatorService;

	public CalculatorController(CalculatorService calculatorService) {
		this.calculatorService = calculatorService;
	}

	@PostMapping("/add")
	public ResponseEntity<CalculationResponse> add(@RequestBody CalculationRequest request) {
		return ResponseEntity.status(HttpStatus.OK).body(calculatorService.add(request));
	}

	@PostMapping("/subtract")
	public ResponseEntity<CalculationResponse> subtract(@RequestBody CalculationRequest request) {
		return ResponseEntity.ok(calculatorService.subtract(request));
	}

	@PostMapping("/multiply")
	public ResponseEntity<CalculationResponse> multiply(@RequestBody CalculationRequest request) {
		return ResponseEntity.ok(calculatorService.multiply(request));
	}

	@PostMapping("/divide")
	public ResponseEntity<CalculationResponse> divide(@RequestBody CalculationRequest request) {
		return ResponseEntity.ok(calculatorService.divide(request));
	}
}