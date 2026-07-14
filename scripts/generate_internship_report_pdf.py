from pathlib import Path
import textwrap


ROOT = Path(__file__).resolve().parents[1]
PDF_PATH = ROOT / "docs" / "internship-project-report.pdf"


REPORT = [
    ("title", "AI-Powered Dynamic Hierarchical Document Approval System"),
    ("subtitle", "Internship Project Report"),
    ("meta", "Generated for project demonstration and review"),
    ("h1", "1. Project Statement"),
    ("p", "The project is an enterprise-grade AI-powered document approval system that automates document understanding, classification, prioritization, and approval routing. It reduces manual approval delays by extracting useful document information, understanding risk and financial importance, and sending the document to the correct approval role."),
    ("p", "The final workflow uses four business roles: Employee, HR, Manager, and CFO. The employee uploads documents. The AI service extracts and analyzes content. Based on amount and importance, the system routes the document to HR, Manager, or CFO. Approvers can approve, reject, or request clarification."),
    ("h1", "2. Main Objective"),
    ("p", "The objective is to build a cloud-native approval platform that replaces static approval workflows with dynamic AI-based routing. The system identifies useful document information, summarizes the document, calculates risk and importance, and assigns the correct approver."),
    ("h1", "3. Technology Stack"),
    ("bullet", "Frontend: React.js, TailwindCSS, Framer Motion, Recharts"),
    ("bullet", "Backend: Spring Boot, REST APIs, PostgreSQL, role-based authentication"),
    ("bullet", "AI Service: FastAPI, PaddleOCR support, LayoutLMv3 support, Google Gemini API"),
    ("bullet", "Infrastructure: Docker Compose, PostgreSQL, Redis, Kafka"),
    ("h1", "4. Document Upload To Approval Pipeline"),
    ("bullet", "Employee logs in and uploads a document."),
    ("bullet", "OCR Agent extracts readable text from PDF, DOCX, or image files."),
    ("bullet", "Layout Agent detects layout regions such as headers, tables, and signatures."),
    ("bullet", "Classification Agent identifies document type."),
    ("bullet", "Risk Scoring Agent calculates financial impact, risk, compliance, and sensitivity."),
    ("bullet", "Approval Routing Agent routes the document to HR, Manager, or CFO."),
    ("bullet", "Compliance Agent checks missing information and policy-sensitive signals."),
    ("bullet", "Summary Agent creates a useful AI summary."),
    ("bullet", "Notification Agent prepares role and employee notifications."),
    ("bullet", "Approver approves, rejects, or requests clarification."),
    ("h1", "5. Dynamic Approval Routing"),
    ("p", "The routing rule is based on INR amount after currency conversion. Below 1 lakh INR routes to HR. From 1 lakh to below 10 lakh INR routes to Manager. 10 lakh INR and above routes to CFO. No document is auto-approved; every document requires a human decision."),
    ("h1", "6. Agentic AI Layer"),
    ("p", "The AI service contains multiple specialist agents: OCR Agent, Layout Agent, Classification Agent, Risk Scoring Agent, Approval Routing Agent, Compliance Agent, Summary Agent, and Notification Agent. Each agent performs one responsibility and passes useful output to the next agent."),
    ("p", "The API returns an agent trace so approvers can see why the system classified, scored, and routed the document."),
    ("h1", "7. Role-Based Screens"),
    ("bullet", "Employee: upload documents, track status, view notifications, and read clarification notes."),
    ("bullet", "HR: review low-value or HR-related documents and make decisions."),
    ("bullet", "Manager: review medium-value documents and monitor assigned approval queue."),
    ("bullet", "CFO: review high-value financial documents and critical approval requests."),
    ("h1", "8. Database Usage"),
    ("p", "PostgreSQL stores users, roles, documents, approval status, notifications, and audit logs. Spring Boot connects to PostgreSQL through Docker Compose and seeds demo users for role-based login."),
    ("h1", "9. Advantages"),
    ("bullet", "Reduces manual routing effort."),
    ("bullet", "Extracts useful information from documents using AI."),
    ("bullet", "Routes documents dynamically based on importance."),
    ("bullet", "Improves transparency with agent trace."),
    ("bullet", "Supports human-in-the-loop decisions."),
    ("bullet", "Provides focused dashboards for each role."),
    ("h1", "10. Drawbacks"),
    ("bullet", "AI output depends on document quality."),
    ("bullet", "Scanned documents may need stronger OCR configuration."),
    ("bullet", "Gemini API quota limits can affect AI summary availability."),
    ("bullet", "Production deployment requires stronger JWT security."),
    ("bullet", "Exchange rates are currently configured manually."),
    ("bullet", "LayoutLMv3 and PaddleOCR require heavier dependencies."),
    ("h1", "11. Future Enhancements"),
    ("bullet", "Add real JWT token validation."),
    ("bullet", "Store uploaded files in object storage."),
    ("bullet", "Add email and SMS notification integration."),
    ("bullet", "Add approval history timeline from database."),
    ("bullet", "Add live Kafka event processing."),
    ("bullet", "Add admin policy builder and audit export reports."),
    ("bullet", "Add automated currency exchange-rate API."),
    ("h1", "12. Conclusion"),
    ("p", "The project demonstrates an AI-first document approval system with dynamic routing, agentic AI analysis, role-based dashboards, and human approval controls. It is suitable as an enterprise prototype for intelligent approval automation and can be expanded into a production-ready approval platform."),
]


def esc(text: str) -> str:
    return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def wrap(text: str, width: int) -> list[str]:
    return textwrap.wrap(text, width=width, break_long_words=False, replace_whitespace=False) or [""]


def build_pages() -> list[list[tuple[str, str]]]:
    pages: list[list[tuple[str, str]]] = []
    page: list[tuple[str, str]] = []
    used = 0
    max_lines = 42

    for style, text in REPORT:
        width = 78
        if style == "title":
            lines = wrap(text, 42)
            height = 3 + len(lines)
        elif style in {"h1", "subtitle"}:
            lines = wrap(text, 60)
            height = 2 + len(lines)
        else:
            lines = wrap(text, width)
            height = len(lines) + 1

        if used + height > max_lines and page:
            pages.append(page)
            page = []
            used = 0

        for line in lines:
            page.append((style, line))
        page.append(("space", ""))
        used += height

    if page:
        pages.append(page)
    return pages


def stream_for_page(lines: list[tuple[str, str]], page_no: int) -> str:
    y = 792
    out = ["q", "0.96 0.98 1 rg", "0 0 612 792 re f", "Q"]
    out += ["q", "0.08 0.13 0.20 rg", "0 742 612 50 re f", "Q"]
    out += ["BT", "/F2 10 Tf", "0.8 0.88 0.95 rg", f"54 760 Td ({esc('AI Document Approval System')}) Tj", "ET"]
    out += ["BT", "/F1 9 Tf", "0.35 0.42 0.52 rg", f"520 24 Td ({page_no}) Tj", "ET"]
    y = 710

    for style, text in lines:
        if style == "space":
            y -= 8
            continue
        if style == "title":
            font, size, color = "F2", 22, "0.05 0.12 0.20"
            leading = 27
        elif style == "subtitle":
            font, size, color = "F2", 16, "0.04 0.45 0.58"
            leading = 21
        elif style == "meta":
            font, size, color = "F1", 10, "0.35 0.42 0.52"
            leading = 15
        elif style == "h1":
            font, size, color = "F2", 13, "0.04 0.34 0.47"
            leading = 18
        elif style == "bullet":
            font, size, color = "F1", 10, "0.13 0.18 0.26"
            text = "- " + text
            leading = 14
        else:
            font, size, color = "F1", 10, "0.13 0.18 0.26"
            leading = 14

        out += ["BT", f"/{font} {size} Tf", f"{color} rg", f"54 {y} Td ({esc(text)}) Tj", "ET"]
        y -= leading
    return "\n".join(out)


def make_pdf() -> bytes:
    pages = build_pages()
    objects: list[str] = []

    def add(obj: str) -> int:
        objects.append(obj)
        return len(objects)

    font1 = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    font2 = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")
    page_ids = []

    for index, lines in enumerate(pages, start=1):
        stream = stream_for_page(lines, index)
        content_id = add(f"<< /Length {len(stream.encode('latin-1'))} >>\nstream\n{stream}\nendstream")
        page_id = add(
            f"<< /Type /Page /Parent 0 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << /F1 {font1} 0 R /F2 {font2} 0 R >> >> "
            f"/Contents {content_id} 0 R >>"
        )
        page_ids.append(page_id)

    pages_id = add(f"<< /Type /Pages /Kids [{' '.join(f'{pid} 0 R' for pid in page_ids)}] /Count {len(page_ids)} >>")
    catalog_id = add(f"<< /Type /Catalog /Pages {pages_id} 0 R >>")

    fixed_objects = []
    for obj in objects:
        fixed_objects.append(obj.replace("/Parent 0 0 R", f"/Parent {pages_id} 0 R"))

    output = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for idx, obj in enumerate(fixed_objects, start=1):
        offsets.append(len(output))
        output.extend(f"{idx} 0 obj\n{obj}\nendobj\n".encode("latin-1"))
    xref = len(output)
    output.extend(f"xref\n0 {len(fixed_objects) + 1}\n0000000000 65535 f \n".encode("latin-1"))
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode("latin-1"))
    output.extend(
        f"trailer\n<< /Size {len(fixed_objects) + 1} /Root {catalog_id} 0 R >>\nstartxref\n{xref}\n%%EOF".encode(
            "latin-1"
        )
    )
    return bytes(output)


def main() -> None:
    PDF_PATH.write_bytes(make_pdf())
    print(PDF_PATH)


if __name__ == "__main__":
    main()
