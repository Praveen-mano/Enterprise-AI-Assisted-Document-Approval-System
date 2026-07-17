# Enterprise AI-Assisted Document Approval System

![Python](https://img.shields.io/badge/Python-3.11+-3776AB?logo=python&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![FastAPI](https://img.shields.io/badge/FastAPI-AI_Service-009688?logo=fastapi&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

## 📌 Project Overview

Enterprise AI-Assisted Document Approval System is a full-stack platform for uploading, analyzing, routing, reviewing, and tracking enterprise documents. The system uses an AI service to extract document text, classify document type, detect amounts, score risk/compliance/sensitivity, generate summaries, and recommend a human approval route.

The final approval decision remains with authorized users such as HR, Manager, or CFO.

## ✨ Features

- JWT-based login and self-registration for Employee and General users.
- Seeded demo accounts for Employee, HR, Manager, CFO, and Admin roles.
- Document upload and AI-assisted analysis for PDF, DOCX, PNG, JPG, and JPEG files.
- Text extraction, document classification, amount detection, priority scoring, AI summary, and agent trace output.
- Amount-based approval routing for invoices: HR, Manager, or CFO.
- Manual approver selection for general documents.
- Role-based dashboards and document visibility.
- Human approval actions: approve, reject, and request clarification.
- Admin workflow builder with enable/disable, edit, delete, sequential steps, parallel steps, due hours, and escalation settings.
- Document library metadata with categories, folders, subfolders, and tags.
- Document library search, filtering, and sorting.
- Audit log and document history timeline.
- In-app notifications and optional SMTP email notifications.
- RAG document indexing, re-indexing, deletion, and role-aware document assistant queries.
- Docker Compose setup for frontend, backend, AI service, PostgreSQL, Redis, Kafka, and Zookeeper.

## 🧰 Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, Vite, Tailwind CSS, Framer Motion, Lucide React, Recharts |
| Backend | Java 21, Spring Boot 3, Spring Security, JWT, Spring Data JPA |
| AI Service | Python, FastAPI, pypdf, python-docx, PyMuPDF, Pillow, Google GenAI |
| Database | PostgreSQL 16, optional local H2 profile |
| Infrastructure | Docker, Docker Compose, Redis, Kafka, Zookeeper |

## 🔄 System Workflow

```text
Employee uploads a document
  -> React frontend sends the file to the FastAPI AI service
  -> AI service extracts text, classifies the document, scores risk, and returns a summary
  -> Frontend submits analyzed metadata to the Spring Boot backend
  -> Backend applies workflow rules or fallback routing
  -> HR, Manager, or CFO reviews the assigned document
  -> Reviewer approves, rejects, or requests clarification
  -> Backend records audit events and sends notifications
  -> Submitter tracks document status and history
```

## 📁 Project Structure

```text
.
├── ai-service/              # FastAPI AI analysis and RAG service
├── backend/                 # Spring Boot API, auth, workflows, approvals, audits
├── src/                     # React frontend source
├── docs/                    # Architecture notes, reports, and database schema
├── k8s/                     # Kubernetes manifests
├── scripts/                 # Utility scripts
├── docker-compose.yml       # Multi-service Docker setup
├── Dockerfile.frontend      # Frontend Docker image
├── package.json             # Frontend dependencies and scripts
└── README.md
```

## 🚀 Installation

### Docker Setup

Create the required environment files, then run:

```bash
docker compose up --build
```

Services:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080/actuator/health
AI API:   http://localhost:8000/health
Postgres: localhost:5433
```

Stop services:

```bash
docker compose down
```

### Manual Setup

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

AI service:

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn main:app --reload --port 8000
```

On Windows, activate with:

```powershell
.\.venv\Scripts\Activate.ps1
```

Frontend:

```bash
npm install
npm run dev
```

## 🔐 Environment Variables

### `backend/.env`

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/approvaldb
SPRING_DATASOURCE_USERNAME=approval
SPRING_DATASOURCE_PASSWORD=your_database_password
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
AI_SERVICE_URL=http://localhost:8000
JWT_SECRET=replace_with_a_strong_jwt_secret

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@example.com
MAIL_PASSWORD=your_app_password
MAIL_FROM=your_email@example.com
MAIL_STARTTLS=true
```

### `ai-service/.env`

```env
GOOGLE_API_KEY=your_google_genai_api_key
GEMINI_MODEL=gemini-2.5-flash

ENABLE_PADDLE_OCR=false
ENABLE_LAYOUTLMV3=false
PADDLE_OCR_LANGUAGE=en
LAYOUTLMV3_MODEL=microsoft/layoutlmv3-base
HUGGINGFACE_TOKEN=your_huggingface_token_optional

USD_TO_INR=83
EUR_TO_INR=90
GBP_TO_INR=105
PROCESSING_TIMING_LOG_DIR=/app/processing_logs
RAG_INDEX_PATH=/app/rag_index/index.json
```

### Frontend `.env`

```env
VITE_BACKEND_API_URL=http://localhost:8080
VITE_AI_API_URL=http://localhost:8000
```

## 👥 User Roles

| Role | Implemented Access |
| --- | --- |
| Employee | Upload documents, view own documents, track status, view notifications, query accessible documents |
| HR | Review documents assigned to HR, approve/reject/request clarification, view assigned history |
| Manager | Review documents assigned to Manager, approve/reject/request clarification, view assigned history |
| CFO | Review documents assigned to CFO, approve/reject/request clarification, view audit data |
| Admin | View all documents, manage workflows, manage categories, view audit data, access all document history |

Demo accounts are seeded automatically:

```text
employee@enterprise.ai
hr@enterprise.ai
manager@enterprise.ai
cfo@enterprise.ai
admin@enterprise.ai
```

Default password:

```text
enterprise-ai
```

## 🖼️ Screenshots

> Add screenshots after deployment.

```text
docs/screenshots/dashboard.png
docs/screenshots/upload.png
docs/screenshots/review.png
docs/screenshots/library.png
docs/screenshots/workflows.png
docs/screenshots/history.png
```

## 🛣️ Future Enhancements

- Persistent object storage for uploaded source files.
- Real-time backend processing status updates.
- Advanced analytics and approval performance reports.
- Workflow versioning and workflow simulation.
- Bulk document actions.
- Production observability, monitoring, and alerting.

## 🤝 Contributors

- Praveen
- Codex

## 📄 License

No license file is currently included. Add a license before public distribution.
