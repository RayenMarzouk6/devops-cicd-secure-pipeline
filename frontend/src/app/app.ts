import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CalculatorApiService, CalculationResponse, Operation } from './calculator-api.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = 'Calculatrice';
  protected readonly subtitle = 'A friendly Angular front end for the Spring Boot calculator API.';
  protected firstNumber = 10;
  protected secondNumber = 5;
  protected selectedOperation: Operation = 'add';
  protected isLoading = false;
  protected errorMessage = '';
  protected backendNotice = '';
  protected response: CalculationResponse | null = null;

  constructor(private readonly calculatorApiService: CalculatorApiService) {}

  protected calculate(operation: Operation): void {
    this.selectedOperation = operation;
    this.isLoading = true;
    this.errorMessage = '';
    this.backendNotice = '';

    this.response = this.buildLocalResponse(operation, this.firstNumber, this.secondNumber);

    this.calculatorApiService.calculate(operation, {
      firstNumber: this.firstNumber,
      secondNumber: this.secondNumber,
    }).subscribe({
      next: (response) => {
        this.response = response;
        this.isLoading = false;
        this.backendNotice = 'Verified with the Spring Boot backend.';
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error?.error?.message ?? 'Unable to reach the backend service.';
        this.backendNotice = 'Showing the local calculation preview because the backend request did not complete.';
      },
    });
  }

  private buildLocalResponse(operation: Operation, firstNumber: number, secondNumber: number): CalculationResponse {
    let result: number;

    switch (operation) {
      case 'add':
        result = firstNumber + secondNumber;
        break;
      case 'subtract':
        result = firstNumber - secondNumber;
        break;
      case 'multiply':
        result = firstNumber * secondNumber;
        break;
      case 'divide':
        result = secondNumber === 0 ? NaN : firstNumber / secondNumber;
        break;
    }

    return {
      operation,
      firstNumber,
      secondNumber,
      result,
      message: 'Local calculation preview.',
    };
  }

  protected get operationLabel(): string {
    return this.selectedOperation.charAt(0).toUpperCase() + this.selectedOperation.slice(1);
  }
}
