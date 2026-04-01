# Learn2Debug

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Learn2Debug is a **Java + Spring Boot** backend where users paste code, get it judged, see detected errors, and receive documentation links to learn how to fix problems.

## What this codebase provides

- REST API for code analysis (`POST /api/analyze`)
- Health endpoint (`GET /api/health`)
- Structured findings with:
  - severity (`ERROR`, `WARNING`, `TIP`)
  - line number
  - explanation
  - fix suggestion
  - related documentation links
- Basic Spring Boot integration tests with MockMvc

## Tech stack

- Java 17
- Spring Boot 3 (Web + Validation)
- Maven
- JUnit 5 / Spring Boot Test

## Run locally

```bash
git clone https://github.com/yourusername/Learn2Debug.git
cd Learn2Debug
mvn spring-boot:run
```

The API will start at `http://localhost:8080`.

## API usage

### Health

```bash
curl http://localhost:8080/api/health
```

### Analyze code

```bash
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "level": "beginner",
    "code": "int x = 10 / 0;\nString temp = \"demo\";"
  }'
```

### Example response (truncated)

```json
{
  "summary": "Analysis complete: 2 finding(s) for level=beginner",
  "score": 75,
  "findings": [
    {
      "severity": "ERROR",
      "line": 1,
      "title": "Possible division by zero",
      "relatedDocumentation": [
        "https://docs.oracle.com/javase/8/docs/api/java/lang/ArithmeticException.html"
      ]
    }
  ]
}
```

## How to test this

### 1) Run automated tests

```bash
mvn test
```

This runs `AnalysisControllerTest` and checks:
- `GET /api/health` returns `200` and `{ "status": "ok" }`
- `POST /api/analyze` returns findings for problematic code
- Validation returns `400` when `code` is blank

### 2) Manual API smoke test (while app is running)

Run the app in one terminal:

```bash
mvn spring-boot:run
```

Then run these in another terminal:

```bash
# health check
curl http://localhost:8080/api/health

# analyze request
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"level":"beginner","code":"int value = 4 / 0;"}'

# validation failure (blank code)
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"level":"beginner","code":""}'
```

Expected results:
- Health returns `{"status":"ok"}`
- Analyze returns JSON with `findings`
- Blank `code` returns `400` with `errors.code = "code is required"`
