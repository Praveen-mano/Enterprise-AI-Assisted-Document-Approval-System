# Architecture Overview

## Frontend

- React Dashboard
- AI Assistant
- Document Viewer
- Upload Portal

## API Gateway

- Authentication API
- Upload API
- Workflow API
- AI Analysis API
- Notification API
- Dashboard API

## AI Service Layer

- OCR Service: PaddleOCR integration point
- Classification Service: LayoutLMv3 integration point
- Summary Service: LLM summarization integration point
- Dynamic Approval Engine: CrewAI/LangChain routing agent integration point

## Data Layer

PostgreSQL:
- Users
- Documents
- Approval Logs
- Workflow History
- Audit Trails

Redis:
- Session Cache
- Real-time Workflow State

Kafka:
- Approval Events
- AI Processing Events
- Notifications Queue
