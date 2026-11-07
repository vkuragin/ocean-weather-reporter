# Ocean Weather Reporter

A Java 21 / Spring Boot REST service that geocodes a requested place, checks proximity to marine forecast grid cells, fetches weather and ocean conditions, and uses Spring AI with OpenAI to prepare a sourced report. Weather data is from Open-Meteo; nearby-place suggestions use OpenStreetMap Overpass.

## Run

Set `OPENAI_API_KEY` and run `mvn spring-boot:run`. Email delivery is optional; set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and optionally `SMTP_AUTH`, `SMTP_STARTTLS`. Without SMTP configuration reports are returned in the HTTP response and printed to standard output. Credentials are read only from environment variables.

## Request

`POST /api/v1/weather/report`

```json
{"location":"Lisbon, Portugal","email":"optional@example.com"}
```

The response contains the resolved location, report, and delivery status. A location outside `MAX_COASTAL_DISTANCE_KM` returns HTTP 422 and up to five nearby coastal suggestions. The suggestion search radius is configurable with `SUGGESTION_RADIUS_KM`.

## Configuration

`application.yml` supports configurable retry count, initial delay, and multiplier for web API and mail calls. Spring AI's model retry policy is configured under `spring.ai.retry`. OpenAI uses `OPENAI_MODEL` (default `gpt-4o-mini`).

The service logs request location, email presence (not the address), coastal-distance decisions, complete prompts, generated reports, and delivery results. Configure log retention/access appropriately because prompts and reports can contain user-requested content.

The nearest marine grid-cell coordinate is used as an approximation of distance to shore. This service is informational; marine forecast grids and public geodata are not suitable for navigation or safety decisions.
