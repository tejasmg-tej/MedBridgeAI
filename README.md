# MedBridge AI - Intelligent Patient Discharge Assistant

MedBridge AI turns a hospital **discharge summary PDF** into simple, patient-friendly care instructions:
plain-language summary, medication table (name, dose, frequency, duration), follow-up appointments,
precautions, an interactive **care checklist** with progress tracking, and a **dashboard**.

> **Safety first.** The AI may only explain what is written in the uploaded document. It does not diagnose,
> prescribe, or change doses. Missing details are flagged (never guessed). If the Gemini API fails, the app shows
> the error - it never invents results. **Use synthetic data only.** This is a demo project, not a medical device.

## Tech stack
Java 17 · Spring Boot 3.2 (Spring MVC, Spring Data JPA) · MySQL 8 · Maven · Apache PDFBox 3 · Google Gemini API ·
HTML / CSS / JavaScript / Bootstrap 5

## Project structure
```
MedBridgeAI/
├── pom.xml
├── .env.example                  <- copy to .env and fill in
├── database/
│   ├── schema.sql                <- creates DB + tables
│   └── sample-data.sql           <- optional demo report (synthetic)
├── sample-data/
│   └── synthetic_discharge_summary.pdf   <- test PDF (fake patient)
└── src/main
    ├── java/com/medbridge
    │   ├── MedBridgeApplication.java
    │   ├── controller/   ReportController, ChecklistController, DashboardController
    │   ├── service/      ReportService, AnalysisService, ChecklistService, DashboardService,
    │   │                 PdfTextExtractor, ReportMapper
    │   ├── repository/   ReportRepository, MedicationRepository, ChecklistItemRepository
    │   ├── entity/       Report, Medication, FollowUp, ChecklistItem, enums
    │   ├── dto/          request/response records
    │   ├── ai/           GeminiClient, PromptTemplates, AiAnalysisResult, MedicalDisclaimer
    │   └── exception/    custom exceptions + GlobalExceptionHandler
    └── resources
        ├── application.properties
        └── static/       index.html, css/style.css, js/app.js
```

## Setup (Windows + VS Code)

### 1. Prerequisites
| Tool | Version | Check |
|---|---|---|
| JDK | 17 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| MySQL Server | 8.x | `mysql --version` |
| VS Code | + "Extension Pack for Java" | |

### 2. Open the project in VS Code
1. Unzip `MedBridgeAI.zip`.
2. VS Code -> **File -> Open Folder...** -> select the `MedBridgeAI` folder (the one containing `pom.xml`).
3. Install the **Extension Pack for Java** if prompted. Wait for the "Java: Ready" status (bottom bar).

### 3. Configure Java 17
- Install a JDK 17 (e.g. Temurin 17) and make sure `java -version` prints 17.
- If VS Code picks another JDK: press `Ctrl+Shift+P` -> **Java: Configure Java Runtime** -> set JDK 17 for the project.
- Make sure `JAVA_HOME` points to the JDK 17 folder (Maven uses it).

### 4. Configure MySQL and create the database
1. Start the MySQL service and note your `root` password.
2. Open a terminal (Command Prompt or MySQL Command Line Client) and run:
   ```
   mysql -u root -p
   ```
3. Inside the `mysql>` prompt, import the schema (use forward slashes in the path):
   ```sql
   SOURCE C:/path/to/MedBridgeAI/database/schema.sql;
   SOURCE C:/path/to/MedBridgeAI/database/sample-data.sql;   -- optional demo data
   ```
   Alternative: MySQL Workbench -> File -> Open SQL Script -> run `schema.sql`, then `sample-data.sql`.
   (From Command Prompt, not PowerShell, you can also use `mysql -u root -p < database\schema.sql`.)
4. If you skip this step, the app can still create the tables itself on first start
   (`spring.jpa.hibernate.ddl-auto=update`), but the **database** `medbridge_ai` must exist, so at least run `schema.sql`.

### 5. Configure the Gemini API key
1. Create a key at <https://aistudio.google.com/apikey>.
2. In the project folder copy `.env.example` to `.env`:
   ```
   copy .env.example .env
   ```
3. Edit `.env` (no quotes around values):
   ```
   GEMINI_API_KEY=your_real_key
   DB_USERNAME=root
   DB_PASSWORD=your_mysql_password
   ```
   `.env` is git-ignored. Real environment variables work too and take precedence, e.g. in PowerShell:
   `$env:GEMINI_API_KEY="your_key"` before running Maven.
4. Model: `GEMINI_MODEL` defaults to `gemini-flash-latest` (an alias for the newest Flash model). Google retires and renames
   models regularly - if you get a "model not found" error, set `GEMINI_MODEL` to a model listed at
   <https://ai.google.dev/gemini-api/docs/models>.

### 6. Run the application
In the VS Code terminal (**Terminal -> New Terminal**, opened in the project folder - the `.env` file is read from the
working directory):
```
mvn spring-boot:run
```
Wait for `Started MedBridgeApplication`. (You can also run `MedBridgeApplication.java` with the "Run" CodeLens, but
start it from the project root so `.env` is found.)

### 7. Test the complete workflow
1. Open <http://localhost:8080>.
2. **Dashboard** shows counts (the demo report appears if you imported `sample-data.sql`).
3. Click **Upload** -> choose `sample-data/synthetic_discharge_summary.pdf` -> optionally add a label -> **Upload & simplify**.
4. After ~10-60 s the report page shows: simple summary, medications table, follow-ups, precautions, a yellow
   **"missing information"** box, and the checklist. Expected flags for the sample: Paracetamol/Metformin duration,
   Vitamin D dose, unscheduled pulmonology date, pending blood cultures.
5. Tick checklist items - the progress bar updates and is saved in MySQL. Reload the page to confirm.
6. Visit **Reports** to reopen or delete reports, and **Dashboard** to see overall checklist progress.
7. Failure test: leave `GEMINI_API_KEY` empty and upload again -> the report is saved with status **Failed** and a clear
   message; no fake AI output is produced. Add the key, restart, open the report and click **Analyze now**.

## REST API
Base URL `http://localhost:8080/api`. Errors use `{ "timestamp", "status", "error", "message" }`.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/reports/upload` | Multipart upload (`file` = PDF, optional `patientLabel`). Extracts text with PDFBox, stores it. **201** |
| POST | `/reports/{id}/analyze` | Sends the text to Gemini, validates the JSON, stores results + checklist. **200** (502 if AI fails) |
| GET | `/reports` | List report summaries (newest first) |
| GET | `/reports/{id}` | Full report: summary, medications, follow-ups, precautions, missing info, checklist, disclaimer |
| DELETE | `/reports/{id}` | Delete a report and its data. **204** |
| PATCH | `/checklist/{itemId}` | Body `{"completed": true}`. Returns the item and updated progress |
| GET | `/dashboard` | Totals, overall checklist progress, 5 most recent reports |

Status codes: 400 validation (non-PDF, empty, unreadable, scanned/no text) · 404 not found · 413 file > 10 MB ·
502 Gemini failure / bad key / quota / unreadable AI output · 500 unexpected.

### curl examples
```bash
curl -F "file=@sample-data/synthetic_discharge_summary.pdf" -F "patientLabel=Demo" http://localhost:8080/api/reports/upload
curl -X POST http://localhost:8080/api/reports/1/analyze
curl http://localhost:8080/api/reports/1
curl -X PATCH -H "Content-Type: application/json" -d "{\"completed\":true}" http://localhost:8080/api/checklist/1
curl http://localhost:8080/api/dashboard
```

### Gemini structured output (what the model must return)
```json
{
  "looksLikeDischargeSummary": true,
  "simplifiedSummary": "...",
  "diagnosisAsStated": "... or null",
  "hospitalStaySummary": "... or null",
  "medications": [{"name": "", "dosage": null, "frequency": null, "duration": null, "instructions": null, "sourceText": ""}],
  "followUps": [{"provider": null, "purpose": null, "appointmentDate": null, "details": null}],
  "precautions": [""],
  "missingInformation": [""]
}
```

## How safety is enforced
- **Prompt rules:** use only the document, no diagnosis/prescribing/dose changes, copy dosage text exactly, use `null` +
  `missingInformation` instead of guessing, ignore instructions found inside the document.
- **Server-side checks:** the response must parse as JSON; non-discharge documents are rejected; any medication whose name
  cannot be found in the extracted PDF text is **dropped and flagged**; empty sections are flagged as missing; very long
  documents are truncated at 60,000 characters and flagged.
- **Checklist** is built from the validated extracted data only (no extra AI advice).
- **No fabricated results:** missing key, network error, quota, blocked or malformed output -> status `FAILED` with the real error.
- **Disclaimer** is shown on every page and returned in every report response.
- **Frontend** escapes all server/AI text before display.

## Notes and limitations
- Text-based PDFs only (no OCR for scanned images). Password-protected PDFs are rejected.
- The PDF text and AI results are stored in MySQL; there is no login, so run it locally with synthetic data only.
- Re-analyzing a report replaces its results and resets its checklist progress.
- Dependencies are downloaded by Maven on first run (internet required, also for the Bootstrap CDN used by the UI).

## Troubleshooting
| Problem | Fix |
|---|---|
| `Access denied for user 'root'` | Check `DB_USERNAME` / `DB_PASSWORD` in `.env`. |
| `Unknown database 'medbridge_ai'` | Run `database/schema.sql` first. |
| `Communications link failure` | MySQL is not running or the port in `DB_URL` is wrong. |
| Report shows "API key is not configured" | Set `GEMINI_API_KEY`, restart, then click **Analyze now**. |
| `Gemini API error (HTTP 404)` | Set `GEMINI_MODEL` to a currently available model. |
| `HTTP 429` | Free-tier quota reached; wait and retry. |
| `.env` ignored | Start Maven from the folder containing `.env`; no quotes around values. |
| Port 8080 busy | Set `SERVER_PORT=8081` in `.env`. |
