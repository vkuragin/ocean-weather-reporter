# Ocean Weather Reporter

An AI Agent (Java 21/Spring Boot) that geocodes a requested place, checks proximity to marine forecast grid cells,
fetches weather and ocean conditions, and uses Spring AI with OpenAI to prepare a sourced report. 
Then sends the complete report to the email specified by user. 

Weather data is from Open-Meteo; nearby-place suggestions use OpenStreetMap Overpass.

## Run

Set `OPENAI_API_KEY` and run `mvn spring-boot:run`. Email delivery is optional; set `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, `SMTP_PASSWORD`, and optionally `SMTP_AUTH`, `SMTP_STARTTLS`. Without SMTP configuration reports are
returned in the HTTP response and printed to standard output. Credentials are read only from environment variables.

## Request

`POST /api/v1/weather/report` creates an asynchronous task and returns `200 OK` with a task ID and polling URL.

```json
{
  "location": "Lisbon, Portugal",
  "email": "optional@example.com"
}
```

Example response:

```json
{
  "taskId": "...",
  "statusUrl": "/api/v1/weather/tasks/...",
  "status": "PENDING"
}
```

Poll `GET /api/v1/weather/tasks/{taskId}`. The response moves through `PENDING`, `RUNNING`, `SUCCEEDED`, or `FAILED`;
completed tasks include the report under `result`, and failed tasks include an `error` message. Unknown task IDs return
404. The task registry is in memory and is cleared when the process restarts. A location outside
`MAX_COASTAL_DISTANCE_KM` becomes a failed task with up to five nearby coastal suggestions. The suggestion search radius
is configurable with `SUGGESTION_RADIUS_KM`.

## Configuration

`application.yml` supports configurable retry count, initial delay, and multiplier for web API and mail calls. Spring
AI's model retry policy is configured under `spring.ai.retry`. OpenAI uses `OPENAI_MODEL` (default `gpt-4o-mini`).

Open-Meteo endpoints and request behavior are configured under `open-meteo`: geocoding, weather, and marine base URLs;
weather and marine forecast durations; marine cell selection; and connect/read timeouts. The client sends explicit
Celsius and km/h weather units and retries only connection failures and upstream 5xx responses.

The service logs request location, email presence (not the address), coastal-distance decisions, complete prompts,
generated reports, and delivery results. Configure log retention/access appropriately because prompts and reports can
contain user-requested content.

When an email is supplied in the request and mail delivery is enabled, the report prompt exposes a Spring AI`send_email`
tool. The recipient is kept in tool context rather than supplied as a model-generated argument; delivery retries use the
`agent.retry` settings. If the model does not invoke the tool or delivery fails, the report is printed to standard
output.

The nearest marine grid-cell coordinate is used as an approximation of distance to shore. This service is informational;
marine forecast grids and public geodata are not suitable for navigation or safety decisions.

---
the following section should not be touched LLMs ever
## TODO 

- [x] Async post/status
- [X] Send email as AI tool
- [X] Web search as AI tool
- [X] AI Agent controls flow
- [X] Normalize location name
- [X] Store location/date for future similar requests (in-memory?)
