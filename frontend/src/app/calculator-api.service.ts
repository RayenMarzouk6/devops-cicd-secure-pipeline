import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

export type Operation = 'add' | 'subtract' | 'multiply' | 'divide';

export interface CalculationRequest {
  firstNumber: number;
  secondNumber: number;
}

export interface CalculationResponse {
  operation: Operation;
  firstNumber: number;
  secondNumber: number;
  result: number;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class CalculatorApiService {
  private readonly baseUrl = '/api/calculatrice';

  constructor(private readonly http: HttpClient) {}

  calculate(operation: Operation, payload: CalculationRequest) {
    return this.http.post<CalculationResponse>(`${this.baseUrl}/${operation}`, payload);
  }
}