package com.example.backend.model;

public record CalculationResponse(String operation, double firstNumber, double secondNumber, double result, String message) {
}