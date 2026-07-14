# AI-Powered Dynamic Hierarchical Document Approval System

Enterprise prototype for AI-powered document approval automation. The system extracts document text, summarizes it with AI, stores workflows in PostgreSQL, and routes documents to the correct human approver.

## Workflow

```text
Employee or General upload
  -> OCR / embedded text extraction
  -> Layout analysis
  -> Document classification
  -> Amount and risk scoring
  -> Approval routing
  -> Human approve / reject / clarification
  -> Sender notification
```

AI does not auto-approve documents. HR, Manager, or CFO must make the final decision.

## Roles

| Role | Access |
| --- | --- |
| Employee | Upload documents and track decisions |
| General | Upload/analyze documents and choose HR, Manager, or CFO as the receiver |
| HR | Review documents assigned to HR |
| Manager | Review documents assigned to Manager |
| CFO | Review documents assigned to CFO |
| Admin | Monitor all documents, routing rules, categories, and action history |

Seeded demo users:

```text
employee@enterprise.ai
general@enterprise.ai
hr@enterprise.ai
manager@enterprise.ai
cfo@enterprise.ai
admin@enterprise.ai
```

Password for all seeded users:

```text
enterprise-ai
```

Passwords are stored as BCrypt hashes in PostgreSQL, not plain text.

New users can create only these account types from the login screen:

```text
Employee
General
```

HR, Manager, and CFO accounts must be seeded or managed by the organization.

## Routing

Documents are categorized at submission:

```text
Invoice Documents
General Documents
```

Invoice Documents use the LLM/RAG-style routing policy. The AI extraction layer provides invoice signals such as amount, risk, compliance, sensitivity, and summary. The backend routing policy then assigns the document to HR, Manager, or CFO.

```text
Invoice amount >= INR 10 lakh, risk >= 75, or compliance >= 80 -> CFO
Invoice amount >= INR 1 lakh, risk >= 45, or compliance >= 50  -> Manager
Lower-signal invoices                                         -> HR
```

General Documents are manual direct assignments. Employee or General users must choose exactly one allowed role:

```text
HR
Manager
CFO
```

The backend validates this choice, so the browser cannot send general documents to unsupported roles. Routing logic is kept modular in the backend so thresholds can be changed later.

## Admin Dashboard

Use:

```text
admin@enterprise.ai
enterprise-ai
```

Admin can view:

```text
All documents
Invoice vs General category counts
Current routing mode
Current approver
Routing policy summary
Timestamped action history
Decision comments
```

Every submission, approval, rejection, and clarification request is stored in the audit log with actor, role, document, comment, and timestamp.

## Decision Emails

After HR, Manager, or CFO approves, rejects, or requests clarification, the backend creates an in-app notification and sends an email to the uploader's email address.

During upload, Employee and General users can also enter any separate validation email address. After the document is validated, the same status and summary email is sent to that exact user-provided address. This email address does not need to be a registered account in the app.

The email includes:

```text
Document status
Document name
Reviewer role
Approval route
AI summary
Clarification instructions, when needed
```

By default Docker starts Mailpit, a local email inbox. The backend should connect to it using the hostname `mailpit` and port `1025` when running in Docker Compose. Validation emails are sent automatically after HR, Manager, or CFO approves, rejects, or requests clarification.

Important: `MAIL_USERNAME` / `MAIL_FROM` are only the sender SMTP account. They are not the recipient. The recipient is dynamic and comes from the upload form field named "Recipient email for validation report".

Open the local inbox:

```text
http://localhost:8025
```

The message will show the email id entered by the uploader in the `To` field, plus the document status and AI summary.

To send to a real Gmail inbox instead of Mailpit, edit:

```text
backend/.env
```

For Gmail, use a Google App Password instead of your normal Gmail password:

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-google-app-password
MAIL_FROM=your-email@gmail.com
MAIL_STARTTLS=true
```

Then restart the backend:

```powershell
docker compose up --build -d backend
```

You can test SMTP before approving a document:

```powershell
$login = Invoke-RestMethod "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"employee@enterprise.ai","password":"enterprise-ai"}'
Invoke-RestMethod "http://localhost:8080/api/notifications/test-email" -Method Post -Headers @{Authorization="Bearer $($login.token)"} -ContentType "application/json" -Body '{"email":"your-email@gmail.com"}'
```

If SMTP is not configured, the app still stores the notification in PostgreSQL and shows it in the Notification screen.

## Tech Stack

- Frontend: React, Vite, TailwindCSS, Framer Motion
- Backend: Spring Boot, JWT authentication, REST APIs
- AI service: FastAPI, PaddleOCR hooks, LayoutLMv3 hooks, Gemini assistant
- Database: PostgreSQL
- Cache/events: Redis, Kafka
- Deployment: Docker Compose

## Run With Docker

Start Docker Desktop, then run:
ri
```powershell
docker compose up --build -d
```

Open:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080/actuator/health
AI:       http://localhost:8000/health
```

Stop:

```powershell
docker compose down
```

## Run Manually

Terminal 1, backend:

```powershell
cd "G:\AI based Document approval System\backend"
.\mvnw.cmd spring-boot:run
```

Terminal 2, AI service:

```powershell
cd "G:\AI based Document approval System\ai-service"
.\.venv\Scripts\Activate.ps1
python -m uvicorn main:app --reload --port 8000
```

Terminal 3, frontend:

```powershell
cd "G:\AI based Document approval System"
npm install
npm run dev
```

## Gemini API

Create or edit:

```text
ai-service/.env
```

Add:

```env
GOOGLE_API_KEY=your_google_ai_studio_key
GEMINI_MODEL=gemini-2.5-flash
```

The app still works with local extraction and routing if Gemini quota is unavailable.

## Important Files

```text
src/main.jsx
src/styles.css
ai-service/main.py
backend/src/main/java/com/enterprise/approval
backend/src/main/resources/application.yml
docker-compose.yml
```
