# AI-Powered Dynamic Hierarchical Document Approval System

## Project Statement

The project is an enterprise-grade AI-powered document approval system that automates document understanding, classification, prioritization, and approval routing. It is designed to reduce manual approval delays by extracting document information, understanding risk and financial importance, and sending the document to the correct approval role.

The final workflow uses four business roles:

- Employee
- HR
- Manager
- CFO

The employee uploads documents. The AI service extracts and analyzes document content. Based on amount and importance, the system routes the document to HR, Manager, or CFO. Approvers can approve, reject, or request clarification. Employee notifications reflect the final decision or clarification request.

## Main Objective

The objective is to build a cloud-native approval platform that replaces static approval workflows with dynamic AI-based routing. The system identifies useful document information, summarizes the document, calculates risk and importance, and assigns the correct approver.

## Technology Stack

Frontend:

- React.js
- TailwindCSS
- Framer Motion
- Recharts

Backend:

- Spring Boot
- REST APIs
- PostgreSQL through Docker
- Role-based authentication

AI Service:

- FastAPI
- PaddleOCR support
- LayoutLMv3 support
- Google Gemini API
- Agentic AI workflow

Infrastructure:

- Docker Compose
- PostgreSQL
- Redis
- Kafka

## Project Pipeline

1. Employee logs into the system.
2. Employee uploads a document.
3. AI service receives the document.
4. OCR Agent extracts readable text.
5. Layout Agent detects important document regions.
6. Classification Agent identifies document type.
7. Risk Scoring Agent calculates financial impact, risk, compliance, and sensitivity.
8. Approval Routing Agent decides whether the document goes to HR, Manager, or CFO.
9. Compliance Agent checks for missing signatures, compliance-sensitive words, and policy issues.
10. Summary Agent creates a useful AI summary.
11. Notification Agent prepares role and employee notifications.
12. Assigned approver reviews the document.
13. Approver approves, rejects, or requests clarification.
14. Employee receives the decision or clarification note.

## Dynamic Approval Routing

The routing rule is based on INR amount after currency conversion:

- Below 1 lakh INR: HR
- 1 lakh to below 10 lakh INR: Manager
- 10 lakh INR and above: CFO

No document is auto-approved. Every document requires a human decision from the assigned role.

## Agentic AI Layer

The AI service contains multiple specialized agents:

- OCR Agent
- Layout Agent
- Classification Agent
- Risk Scoring Agent
- Approval Routing Agent
- Compliance Agent
- Summary Agent
- Notification Agent

Each agent performs one responsibility and passes its output to the next agent. The API returns an agent trace so approvers can see why the system routed the document to a specific role.

## User Role Screens

Employee:

- Upload document
- Track document status
- View notifications
- Read clarification notes

HR:

- Review low-value or HR-related documents
- Approve, reject, or request clarification

Manager:

- Review medium-value documents
- Monitor assigned approval queue
- Approve, reject, or request clarification

CFO:

- Review high-value documents
- Approve or reject critical financial documents
- Request clarification for financial details

## Database Usage

PostgreSQL stores:

- Users
- User roles
- Documents
- Approval status
- Notifications
- Audit logs

The backend connects to PostgreSQL through Docker Compose. Spring Boot seeds demo users and stores role-based login data.

## Advantages

- Reduces manual document routing effort
- Uses AI to extract useful document information
- Routes documents dynamically based on importance
- Improves approval transparency with agent trace
- Supports human-in-the-loop decisions
- Provides clean dashboards for each role
- Uses Docker-based infrastructure
- Supports future integration with enterprise tools

## Drawbacks

- AI output depends on document quality
- Scanned documents may need stronger OCR configuration
- Gemini API quota limits can affect AI summary availability
- Real production deployment requires stronger JWT security
- Exchange rates are currently configured manually
- LayoutLMv3 and PaddleOCR require heavier dependencies
- Backend and AI service are separate and need orchestration in production

## Future Enhancements

- Add real JWT token validation
- Store uploaded files in object storage
- Add email and SMS notification integration
- Add approval history timeline from database
- Add live Kafka event processing
- Add admin policy builder
- Add audit export reports
- Add enterprise SSO login
- Add automated currency exchange-rate API
- Fine-tune LayoutLMv3 for company-specific document types

## Conclusion

The project demonstrates an AI-first document approval system with dynamic routing, agentic AI analysis, role-based dashboards, and human approval controls. It is suitable as an enterprise prototype for intelligent approval automation and can be expanded into a production-ready approval platform with stronger security, storage, monitoring, and integration layers.
