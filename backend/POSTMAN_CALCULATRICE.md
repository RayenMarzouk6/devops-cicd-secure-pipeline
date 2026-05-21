# Postman Documentation - Calculatrice API

Use this file to test the Spring Boot backend in Postman.

## Base URL

Set a Postman variable named `baseUrl` to:

```text
http://localhost:8080
```

Then call the endpoints with:

```text
{{baseUrl}}/api/calculatrice
```

## Common Headers

For every request, send:

```http
Content-Type: application/json
Accept: application/json
```

## 1. Add

- Method: `POST`
- URL: `{{baseUrl}}/api/calculatrice/add`
- Body type: raw JSON

```json
{
  "firstNumber": 10,
  "secondNumber": 5
}
```

Expected response:

```json
{
  "operation": "add",
  "firstNumber": 10,
  "secondNumber": 5,
  "result": 15,
  "message": "Operation completed successfully."
}
```

## 2. Subtract

- Method: `POST`
- URL: `{{baseUrl}}/api/calculatrice/subtract`
- Body type: raw JSON

```json
{
  "firstNumber": 10,
  "secondNumber": 5
}
```

Expected response:

```json
{
  "operation": "subtract",
  "firstNumber": 10,
  "secondNumber": 5,
  "result": 5,
  "message": "Operation completed successfully."
}
```

## 3. Multiply

- Method: `POST`
- URL: `{{baseUrl}}/api/calculatrice/multiply`
- Body type: raw JSON

```json
{
  "firstNumber": 10,
  "secondNumber": 5
}
```

Expected response:

```json
{
  "operation": "multiply",
  "firstNumber": 10,
  "secondNumber": 5,
  "result": 50,
  "message": "Operation completed successfully."
}
```

## 4. Divide

- Method: `POST`
- URL: `{{baseUrl}}/api/calculatrice/divide`
- Body type: raw JSON

```json
{
  "firstNumber": 10,
  "secondNumber": 5
}
```

Expected response:

```json
{
  "operation": "divide",
  "firstNumber": 10,
  "secondNumber": 5,
  "result": 2,
  "message": "Operation completed successfully."
}
```

### Divide error case

If `secondNumber` is `0`, the API returns:

```json
{
  "error": "Bad Request",
  "message": "Division by zero is not allowed."
}
```

## Quick Postman Test Order

1. Start the backend application.
2. Create a Postman environment with `baseUrl=http://localhost:8080`.
3. Send the `add`, `subtract`, `multiply`, and `divide` requests.
4. Check that the divide error case returns HTTP `400`.

## Notes

- The backend runs on port `8080` by default.
- These endpoints are intended for CI/CD and quality pipeline testing with Maven, SonarQube, and Nexus.
