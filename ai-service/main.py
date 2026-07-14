import json
import os
import re
import time
import hashlib
import math
import uuid
from datetime import datetime
from io import BytesIO
from pathlib import Path
from typing import Any, List

from docx import Document
from dotenv import load_dotenv
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from google import genai
from pydantic import BaseModel, Field
from pypdf import PdfReader

load_dotenv()

ENABLE_PADDLE_OCR = os.getenv("ENABLE_PADDLE_OCR", "false").lower() == "true"
ENABLE_LAYOUTLMV3 = os.getenv("ENABLE_LAYOUTLMV3", "false").lower() == "true"
PADDLE_OCR_LANGUAGE = os.getenv("PADDLE_OCR_LANGUAGE", "en")
LAYOUTLMV3_MODEL = os.getenv("LAYOUTLMV3_MODEL", "microsoft/layoutlmv3-base")
HUGGINGFACE_TOKEN = os.getenv("HUGGINGFACE_TOKEN") or None
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY") or os.getenv("GEMINI_API_KEY") or None
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
USD_TO_INR = float(os.getenv("USD_TO_INR", "83"))
EUR_TO_INR = float(os.getenv("EUR_TO_INR", "90"))
GBP_TO_INR = float(os.getenv("GBP_TO_INR", "105"))
TIMING_LOG_DIR = os.getenv("PROCESSING_TIMING_LOG_DIR", "/app/processing_logs")
RAG_INDEX_PATH = Path(os.getenv("RAG_INDEX_PATH", "/app/rag_index/index.json"))
RAG_EMBEDDING_DIMENSIONS = int(os.getenv("RAG_EMBEDDING_DIMENSIONS", "384"))
RAG_CHUNK_SIZE = int(os.getenv("RAG_CHUNK_SIZE", "900"))
RAG_CHUNK_OVERLAP = int(os.getenv("RAG_CHUNK_OVERLAP", "160"))
RAG_MIN_SCORE = float(os.getenv("RAG_MIN_SCORE", "0.08"))

_paddle_ocr = None
_layout_processor = None
_layout_model = None
_gemini_client = None

app = FastAPI(title="Agentic AI Dynamic Approval Service", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class AgentTrace(BaseModel):
    agent: str
    role: str
    observation: str
    decision: str
    confidence: float
    next_action: str


class AnalysisResult(BaseModel):
    filename: str
    document_type: str
    confidence: float
    priority: str
    amount_detected: str
    department: str
    sensitivity_score: int
    risk_score: int
    compliance_score: int
    financial_impact_score: int
    approval_chain: List[str]
    sla_deadline: str
    escalation_rules: List[str]
    summary: str
    extracted_text: str
    detected_keywords: List[str]
    ocr_engine: str
    layout_engine: str
    layout_regions: List[str]
    agent_trace: List[AgentTrace]
    agentic_decision: str
    timing_log_path: str | None = None


class AssistantRequest(BaseModel):
    question: str
    document_text: str = ""
    document_name: str = ""
    metadata: dict[str, Any] = {}


class AssistantResponse(BaseModel):
    answer: str
    model: str
    used_llm: bool


class RagDocumentMetadata(BaseModel):
    document_id: str
    source_document: str
    owner_email: str = ""
    owner_role: str = ""
    allowed_roles: list[str] = Field(default_factory=list)
    indexed_at: str
    chunk_count: int
    ocr_engine: str


class RagSource(BaseModel):
    document_id: str
    source_document: str
    snippet: str
    score: float
    chunk_index: int


class RagIndexResponse(BaseModel):
    document_id: str
    source_document: str
    chunk_count: int
    ocr_engine: str
    indexed_at: str
    content_hash: str
    duplicate: bool = False
    duplicate_of: str | None = None


class RagQueryRequest(BaseModel):
    question: str
    actor_email: str = ""
    actor_role: str = ""
    document_ids: list[str] = Field(default_factory=list)
    top_k: int = 5


class RagQueryResponse(BaseModel):
    answer: str
    sources: list[RagSource]
    model: str
    used_llm: bool


class RagDeleteResponse(BaseModel):
    document_id: str
    deleted_chunks: int


def clamp(value: int, minimum: int = 0, maximum: int = 100) -> int:
    return max(minimum, min(maximum, value))


def sanitize_log_filename(value: str) -> str:
    safe_name = re.sub(r"[^A-Za-z0-9._-]+", "_", value or "document")
    safe_name = safe_name.strip("._-") or "document"
    return safe_name[:80]


def write_processing_timing_log(filename: str, timings: dict[str, float], output_dir: str | None = None) -> str:
    output_path = Path(output_dir or TIMING_LOG_DIR)
    output_path.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    safe_name = sanitize_log_filename(Path(filename).stem or filename)
    log_path = output_path / f"{timestamp}_{safe_name}.json"

    duration_values = [float(duration) for module, duration in timings.items() if module != "total"]
    payload = {
        "filename": filename,
        "created_at": datetime.now().isoformat(timespec="milliseconds"),
        "total_duration_seconds": round(sum(duration_values), 4),
        "modules": {module: round(float(duration), 4) for module, duration in timings.items()},
    }

    with log_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)

    return str(log_path)


def tokenize_for_embedding(text: str) -> list[str]:
    return re.findall(r"[a-z0-9][a-z0-9._/-]*", (text or "").lower())


def embed_text(text: str, dimensions: int = RAG_EMBEDDING_DIMENSIONS) -> list[float]:
    vector = [0.0] * dimensions
    tokens = tokenize_for_embedding(text)
    if not tokens:
        return vector

    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % dimensions
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[index] += sign

    length = math.sqrt(sum(value * value for value in vector))
    if length == 0:
        return vector
    return [round(value / length, 6) for value in vector]


def cosine_similarity(left: list[float], right: list[float]) -> float:
    return sum(a * b for a, b in zip(left, right))


def split_text_into_chunks(text: str, chunk_size: int = RAG_CHUNK_SIZE, overlap: int = RAG_CHUNK_OVERLAP) -> list[str]:
    normalized = re.sub(r"\s+", " ", text or "").strip()
    if not normalized:
        return []

    chunks = []
    start = 0
    while start < len(normalized):
        end = min(start + chunk_size, len(normalized))
        if end < len(normalized):
            boundary = max(normalized.rfind(". ", start, end), normalized.rfind("\n", start, end))
            if boundary > start + int(chunk_size * 0.55):
                end = boundary + 1
        chunk = normalized[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end >= len(normalized):
            break
        start = max(end - overlap, start + 1)
    return chunks


def load_rag_index() -> dict[str, Any]:
    if not RAG_INDEX_PATH.exists():
        return {"documents": {}, "chunks": []}
    try:
        with RAG_INDEX_PATH.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except json.JSONDecodeError:
        return {"documents": {}, "chunks": []}


def save_rag_index(index: dict[str, Any]) -> None:
    RAG_INDEX_PATH.parent.mkdir(parents=True, exist_ok=True)
    with RAG_INDEX_PATH.open("w", encoding="utf-8") as handle:
        json.dump(index, handle, indent=2)


def normalize_document_id(value: str | None, filename: str) -> str:
    if value and value.strip():
        return value.strip()
    stem = sanitize_log_filename(Path(filename).stem or "document")
    return f"{stem}-{uuid.uuid4().hex[:10]}"


def content_hash(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def parse_allowed_roles(value: str | None) -> list[str]:
    if not value:
        return []
    try:
        parsed = json.loads(value)
        if isinstance(parsed, list):
            return [str(item).strip() for item in parsed if str(item).strip()]
    except json.JSONDecodeError:
        pass
    return [item.strip() for item in value.split(",") if item.strip()]


def can_access_rag_document(metadata: dict[str, Any], actor_email: str, actor_role: str) -> bool:
    role = (actor_role or "").strip()
    email = (actor_email or "").strip().lower()
    if role == "Admin":
        return True
    if email and email == str(metadata.get("owner_email", "")).lower():
        return True
    return role in metadata.get("allowed_roles", [])


def remove_rag_document(document_id: str) -> int:
    index = load_rag_index()
    original_count = len(index.get("chunks", []))
    index["chunks"] = [
        chunk for chunk in index.get("chunks", [])
        if str(chunk.get("document_id")) != str(document_id)
    ]
    index.get("documents", {}).pop(str(document_id), None)
    save_rag_index(index)
    return original_count - len(index["chunks"])


def find_duplicate_document(index: dict[str, Any], current_document_id: str, file_hash: str) -> dict[str, Any] | None:
    for document_id, metadata in index.get("documents", {}).items():
        if str(document_id) != str(current_document_id) and metadata.get("content_hash") == file_hash:
            return metadata
    return None


def build_rag_answer(question: str, sources: list[RagSource]) -> tuple[str, bool]:
    context = "\n\n".join(
        f"[{idx + 1}] {source.source_document} chunk {source.chunk_index}: {source.snippet}"
        for idx, source in enumerate(sources)
    )
    fallback = (
        "I could not find enough indexed context to answer that accurately."
        if not sources
        else "Based on the retrieved document snippets: " + " ".join(source.snippet for source in sources[:2])[:900]
    )
    return call_gemini(
        "You are a RAG document assistant. Answer only from the provided context. If the context is insufficient, say you do not know. Cite source filenames briefly.",
        f"Question: {question}\n\nRetrieved context:\n{context}",
        fallback,
    )


def add_trace(
    trace: list[AgentTrace],
    agent: str,
    role: str,
    observation: str,
    decision: str,
    confidence: float,
    next_action: str,
) -> None:
    trace.append(
        AgentTrace(
            agent=agent,
            role=role,
            observation=observation,
            decision=decision,
            confidence=round(confidence, 2),
            next_action=next_action,
        )
    )


def get_gemini_client():
    global _gemini_client
    if not GOOGLE_API_KEY:
        return None
    if _gemini_client is None:
        _gemini_client = genai.Client(api_key=GOOGLE_API_KEY)
    return _gemini_client


def call_gemini(system_prompt: str, user_prompt: str, fallback: str) -> tuple[str, bool]:
    client = get_gemini_client()
    if client is None:
        return fallback, False

    try:
        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=f"{system_prompt}\n\n{user_prompt}",
        )
        return (response.text or fallback).strip(), True
    except Exception as exc:
        return f"{fallback}\n\nLLM unavailable: {exc}", False


def get_paddle_ocr():
    global _paddle_ocr
    if not ENABLE_PADDLE_OCR:
        return None
    if _paddle_ocr is None:
        from paddleocr import PaddleOCR

        _paddle_ocr = PaddleOCR(use_angle_cls=True, lang=PADDLE_OCR_LANGUAGE, show_log=False)
    return _paddle_ocr


def get_layoutlmv3():
    global _layout_processor, _layout_model
    if not ENABLE_LAYOUTLMV3:
        return None, None
    if _layout_processor is None or _layout_model is None:
        from transformers import LayoutLMv3Model, LayoutLMv3Processor

        _layout_processor = LayoutLMv3Processor.from_pretrained(
            LAYOUTLMV3_MODEL,
            token=HUGGINGFACE_TOKEN,
            apply_ocr=False,
        )
        _layout_model = LayoutLMv3Model.from_pretrained(
            LAYOUTLMV3_MODEL,
            token=HUGGINGFACE_TOKEN,
        )
    return _layout_processor, _layout_model


def extract_pdf_text(content: bytes) -> str:
    reader = PdfReader(BytesIO(content))
    return "\n".join(page.extract_text() or "" for page in reader.pages).strip()


def extract_docx_text(content: bytes) -> str:
    document = Document(BytesIO(content))
    return "\n".join(paragraph.text for paragraph in document.paragraphs).strip()


def extract_image_text_with_paddle(content: bytes) -> str:
    ocr = get_paddle_ocr()
    if ocr is None:
        return (
            "Image uploaded. PaddleOCR is disabled. Set ENABLE_PADDLE_OCR=true after installing "
            "requirements-ai.txt to extract scanned image text."
        )

    from PIL import Image
    import numpy as np

    image = Image.open(BytesIO(content)).convert("RGB")
    result = ocr.ocr(np.array(image), cls=True)
    lines = []
    for page in result or []:
        for item in page or []:
            if len(item) >= 2 and item[1]:
                lines.append(item[1][0])
    return "\n".join(lines).strip() or "PaddleOCR ran, but no text was detected."


def extract_scanned_pdf_with_paddle(content: bytes) -> str:
    if not ENABLE_PADDLE_OCR:
        return (
            "PDF uploaded, but no embedded text was found. Enable PaddleOCR and install "
            "requirements-ai.txt to OCR scanned PDF pages."
        )

    try:
        import fitz

        document = fitz.open(stream=content, filetype="pdf")
        extracted_pages = []
        for page in document[:3]:
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            extracted_pages.append(extract_image_text_with_paddle(pixmap.tobytes("png")))
        return "\n".join(extracted_pages).strip()
    except Exception as exc:
        return f"Scanned PDF OCR failed: {exc}"


def ocr_agent(filename: str, content: bytes, trace: list[AgentTrace]) -> tuple[str, str]:
    lowered = filename.lower()
    engine = "Embedded text extractor"
    if lowered.endswith(".pdf"):
        text = extract_pdf_text(content)
        if not text:
            engine = "PaddleOCR scanned PDF fallback" if ENABLE_PADDLE_OCR else "PDF embedded text unavailable"
            text = extract_scanned_pdf_with_paddle(content)
    elif lowered.endswith(".docx"):
        text = extract_docx_text(content)
    elif lowered.endswith((".png", ".jpg", ".jpeg")):
        engine = "PaddleOCR" if ENABLE_PADDLE_OCR else "PaddleOCR disabled"
        text = extract_image_text_with_paddle(content)
    else:
        text = content.decode("utf-8", errors="ignore").strip()

    add_trace(
        trace,
        "OCR Agent",
        "Extract readable text from uploaded file.",
        f"Extracted {len(text)} characters using {engine}.",
        "Forward extracted text to layout and classification agents.",
        0.9 if text else 0.35,
        "layout_analysis",
    )
    return text, engine


def layout_agent(filename: str, content: bytes, text: str, trace: list[AgentTrace]) -> tuple[str, list[str]]:
    regions = []
    lowered_text = text.lower()
    if "signature" in lowered_text:
        regions.append("Signature block detected")
    if any(word in lowered_text for word in ["table", "subtotal", "total", "line item", "invoice"]):
        regions.append("Financial/table region detected")
    if any(word in lowered_text for word in ["contract", "agreement", "policy", "request"]):
        regions.append("Header/title region detected")

    if filename.lower().endswith(".pdf"):
        try:
            import fitz

            document = fitz.open(stream=content, filetype="pdf")
            for block in document[0].get_text("blocks")[:6]:
                block_text = str(block[4]).strip()
                if block_text:
                    regions.append(f"PDF block: {block_text[:80]}")
        except Exception:
            regions.append("PDF block extraction unavailable")

    engine = "LayoutLMv3 disabled"
    if ENABLE_LAYOUTLMV3:
        try:
            processor, model = get_layoutlmv3()
            if processor and model:
                engine = f"LayoutLMv3 loaded: {LAYOUTLMV3_MODEL}"
        except Exception as exc:
            engine = f"LayoutLMv3 unavailable: {exc}"

    add_trace(
        trace,
        "Layout Agent",
        "Detect layout regions and document structure.",
        f"Found {len(regions)} layout signals.",
        "Forward layout signals to classification and compliance agents.",
        0.82 if regions else 0.55,
        "classification",
    )
    return engine, regions or ["No strong layout regions detected"]


def classify_document(text: str, filename: str) -> tuple[str, float]:
    corpus = f"{filename} {text}".lower()
    rules = [
        ("Invoice", ["invoice", "bill to", "amount due", "tax invoice", "payment due", "total due"]),
        ("Contract", ["contract", "agreement", "clause", "party", "term", "renewal", "liability"]),
        ("HR Document", ["employee", "reimbursement", "relocation", "leave", "payroll", "hr"]),
        ("Purchase Order", ["purchase order", "po number", "vendor", "ship to"]),
        ("Legal Notice", ["notice", "legal", "breach", "jurisdiction"]),
        ("Financial Report", ["balance sheet", "profit", "loss", "financial report", "revenue"]),
        ("Medical Record", ["patient", "diagnosis", "medical", "prescription", "hospital"]),
    ]
    scores = [(label, sum(1 for term in terms if term in corpus)) for label, terms in rules]
    label, score = max(scores, key=lambda item: item[1])
    if score == 0:
        return "General Document", 0.72
    return label, min(0.98, 0.78 + score * 0.05)


def classification_agent(filename: str, text: str, trace: list[AgentTrace]) -> tuple[str, float]:
    document_type, confidence = classify_document(text, filename)
    add_trace(
        trace,
        "Classification Agent",
        "Identify document type.",
        f"Detected {document_type}.",
        "Forward type and confidence to scoring agent.",
        confidence,
        "risk_scoring",
    )
    return document_type, confidence


def format_inr(value: float) -> str:
    return f"₹{value:,.0f}"


def parse_money_value(value: str) -> float:
    cleaned = re.sub(r"[^0-9.]", "", str(value or ""))
    return float(cleaned) if cleaned else 0.0


def detect_amount(text: str) -> tuple[str, int]:
    values: list[float] = []

    # Supports Indian amount words: 10 lakh, 10 lakhs, 10 lac, 1.5 crore, etc.
    unit_matches = re.findall(
        r"([0-9]+(?:\.[0-9]+)?)\s*(lakh|lakhs|lac|lacs|lkh|lksh|crore|crores|cr)\b",
        text,
        flags=re.I,
    )
    for amount, unit in unit_matches:
        multiplier = 10000000 if unit.lower() in ["crore", "crores", "cr"] else 100000
        values.append(float(amount) * multiplier)

    # Supports currency-prefixed amounts and converts non-INR values into INR for routing.
    currency_matches = re.findall(
        r"(₹|rs\.?|inr|usd|\$|eur|€|gbp|£)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)",
        text,
        flags=re.I,
    )
    for currency, amount in currency_matches:
        numeric = float(amount.replace(",", ""))
        currency_key = currency.lower()
        if currency_key in ["usd", "$"]:
            numeric *= USD_TO_INR
        elif currency_key in ["eur", "€"]:
            numeric *= EUR_TO_INR
        elif currency_key in ["gbp", "£"]:
            numeric *= GBP_TO_INR
        values.append(numeric)

    # Supports currency-suffixed values: 20000 USD, 100000 INR.
    suffix_matches = re.findall(
        r"([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(usd|eur|gbp|inr|rs)\b",
        text,
        flags=re.I,
    )
    for amount, currency in suffix_matches:
        numeric = float(amount.replace(",", ""))
        currency_key = currency.lower()
        if currency_key == "usd":
            numeric *= USD_TO_INR
        elif currency_key == "eur":
            numeric *= EUR_TO_INR
        elif currency_key == "gbp":
            numeric *= GBP_TO_INR
        values.append(numeric)

    # Supports labelled plain amounts. Since no currency is present, assume INR.
    labelled_matches = re.findall(
        r"(?:total|amount|value|invoice|payment|cost|due|budget|financial impact)[^\n:₹$]{0,25}[:\-]?\s*([0-9][0-9,]{4,}(?:\.[0-9]{1,2})?)",
        text,
        flags=re.I,
    )
    values.extend(float(match.replace(",", "")) for match in labelled_matches)

    if not values:
        return "₹0", 0

    highest = max(values)
    return format_inr(highest), clamp(int(highest / 50000))


def detect_department(text: str, document_type: str) -> str:
    corpus = text.lower()
    if document_type == "HR Document" or any(word in corpus for word in ["employee", "payroll", "relocation", "hr"]):
        return "HR"
    if any(word in corpus for word in ["invoice", "payment", "tax", "budget", "finance"]):
        return "Finance"
    if any(word in corpus for word in ["contract", "agreement", "legal", "liability"]):
        return "Legal"
    return "Operations"


def scoring_agent(text: str, document_type: str, trace: list[AgentTrace]) -> dict[str, Any]:
    amount, financial_score = detect_amount(text)
    department = detect_department(text, document_type)
    corpus = text.lower()
    keywords = [
        word
        for word in [
            "confidential",
            "compliance",
            "gdpr",
            "hipaa",
            "payment",
            "liability",
            "termination",
            "signature",
            "penalty",
            "audit",
            "total due",
        ]
        if word in corpus
    ]
    sensitivity_score = clamp(25 + len([k for k in keywords if k in ["confidential", "gdpr", "hipaa", "audit"]]) * 18)
    compliance_score = clamp(20 + len([k for k in keywords if k in ["compliance", "gdpr", "hipaa", "audit"]]) * 20)
    risk_score = clamp(20 + financial_score // 2 + len([k for k in keywords if k in ["liability", "penalty", "termination"]]) * 12)
    total = int((financial_score + sensitivity_score + compliance_score + risk_score) / 4)
    priority = "Critical" if total >= 70 else "Medium" if total >= 40 else "Low"

    add_trace(
        trace,
        "Risk Scoring Agent",
        "Score financial impact, risk, sensitivity, and compliance.",
        f"Risk {risk_score}/100, compliance {compliance_score}/100, amount {amount}.",
        f"Assigned {priority} priority.",
        0.86,
        "approval_routing",
    )
    return {
        "amount": amount,
        "department": department,
        "keywords": keywords,
        "financial_score": financial_score,
        "sensitivity_score": sensitivity_score,
        "compliance_score": compliance_score,
        "risk_score": risk_score,
        "priority": priority,
    }


def routing_agent(document_type: str, score: dict[str, Any], trace: list[AgentTrace]) -> tuple[list[str], str, list[str]]:
    amount_value = parse_money_value(score["amount"])
    priority = score["priority"]
    if amount_value >= 1000000:
        chain = ["CFO"]
        route_reason = "Amount is 10 lakh or above, so CFO is the direct approver."
    elif amount_value >= 100000:
        chain = ["Manager"]
        route_reason = "Amount is below 10 lakh but above low-value range, so Manager is the approver."
    elif document_type == "HR Document" or amount_value < 100000:
        chain = ["HR"]
        route_reason = "Amount is in the low/thousands range or HR-related, so HR is the approver."
    else:
        chain = ["Manager"]
        route_reason = "Default medium-importance document, so Manager is the approver."

    sla = "4 hours" if chain == ["CFO"] else "12 hours" if chain == ["Manager"] else "24 hours"
    rules = [
        "No auto approval is allowed.",
        "Clarification returns the document to the employee with approver instructions.",
        "Low value below 1 lakh routes to HR.",
        "Medium value from 1 lakh to below 10 lakh routes to Manager.",
        "High value 10 lakh or above routes to CFO.",
    ]
    add_trace(
        trace,
        "Approval Routing Agent",
        "Generate human approval chain.",
        f"Selected route {' -> '.join(chain)}. {route_reason}",
        "Send document to first approver.",
        0.9,
        "compliance_review",
    )
    return chain, sla, rules


def compliance_agent(text: str, score: dict[str, Any], trace: list[AgentTrace]) -> None:
    warnings = []
    if score["compliance_score"] >= 60:
        warnings.append("Compliance-sensitive language detected")
    if "signature" not in text.lower():
        warnings.append("Signature evidence may be missing")
    add_trace(
        trace,
        "Compliance Agent",
        "Check policy and missing information signals.",
        "; ".join(warnings) if warnings else "No major compliance warning detected.",
        "Attach compliance note to manager summary.",
        0.78,
        "summary_generation",
    )


def summary_agent(
    filename: str,
    document_type: str,
    score: dict[str, Any],
    chain: list[str],
    text: str,
    trace: list[AgentTrace],
) -> str:
    preview = " ".join(text.split())[:3200] or "No readable text was extracted from this file."
    fallback = (
        f"{document_type} analyzed. Amount {score['amount']}. Priority {score['priority']}. "
        f"Risk {score['risk_score']}/100. Required approval route: {' -> '.join(chain)}. "
        f"Key text: {preview[:420]}"
    )
    summary, used_llm = call_gemini(
        "You are a document approval summary agent. Summarize only useful manager information: total due, document type, risk, missing information, and approval recommendation. Do not approve automatically.",
        (
            f"Filename: {filename}\n"
            f"Type: {document_type}\n"
            f"Priority: {score['priority']}\n"
            f"Amount: {score['amount']}\n"
            f"Risk: {score['risk_score']}\n"
            f"Approval chain: {' -> '.join(chain)}\n\n"
            f"Document text:\n{preview}"
        ),
        fallback,
    )
    add_trace(
        trace,
        "Summary Agent",
        "Create AI manager summary.",
        "Gemini summary generated." if used_llm else "Fallback summary generated.",
        "Return analysis result to frontend.",
        0.92 if used_llm else 0.7,
        "notify_employee_and_approver",
    )
    return summary


def notification_agent(chain: list[str], trace: list[AgentTrace]) -> None:
    add_trace(
        trace,
        "Notification Agent",
        "Prepare role notifications.",
        f"Employee notified. First approver {chain[0]} assigned.",
        "Wait for human approve, decline, or clarification.",
        0.88,
        "human_decision",
    )


def run_agentic_analysis(filename: str, content: bytes) -> AnalysisResult:
    trace: list[AgentTrace] = []
    timings: dict[str, float] = {}

    started = time.perf_counter()
    text, ocr_engine = ocr_agent(filename, content, trace)
    timings["ocr"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    layout_engine, layout_regions = layout_agent(filename, content, text, trace)
    timings["layout"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    document_type, confidence = classification_agent(filename, text, trace)
    timings["classification"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    score = scoring_agent(text, document_type, trace)
    timings["risk_scoring"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    approval_chain, sla, escalation_rules = routing_agent(document_type, score, trace)
    timings["routing"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    compliance_agent(text, score, trace)
    timings["compliance"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    summary = summary_agent(filename, document_type, score, approval_chain, text, trace)
    timings["ai_summary"] = round(time.perf_counter() - started, 4)

    started = time.perf_counter()
    notification_agent(approval_chain, trace)
    timings["notification"] = round(time.perf_counter() - started, 4)

    timings["total"] = round(sum(duration for duration in timings.values() if duration is not None), 4)
    timing_log_path = write_processing_timing_log(filename, timings)

    decision = (
        f"Agentic workflow completed: {document_type} routed to {' -> '.join(approval_chain)} "
        f"with {score['priority']} priority. Human approval is required."
    )

    return AnalysisResult(
        filename=filename,
        document_type=document_type,
        confidence=round(confidence, 2),
        priority=score["priority"],
        amount_detected=score["amount"],
        department=score["department"],
        sensitivity_score=score["sensitivity_score"],
        risk_score=score["risk_score"],
        compliance_score=score["compliance_score"],
        financial_impact_score=score["financial_score"],
        approval_chain=approval_chain,
        sla_deadline=sla,
        escalation_rules=escalation_rules,
        summary=summary,
        extracted_text=text[:1800],
        detected_keywords=score["keywords"],
        ocr_engine=ocr_engine,
        layout_engine=layout_engine,
        layout_regions=layout_regions,
        agent_trace=trace,
        agentic_decision=decision,
        timing_log_path=timing_log_path,
    )


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "services": {
            "agenticWorkflow": "enabled",
            "rag": "enabled",
            "paddleocr": "enabled" if ENABLE_PADDLE_OCR else "disabled",
            "layoutlmv3": "enabled" if ENABLE_LAYOUTLMV3 else "disabled",
            "geminiAssistant": "enabled" if GOOGLE_API_KEY else "disabled",
        },
    }


@app.get("/")
def root():
    return {
        "message": "Agentic AI Dynamic Approval Service is running",
        "agents": [
            "OCR Agent",
            "Layout Agent",
            "Classification Agent",
            "Risk Scoring Agent",
            "Approval Routing Agent",
            "Compliance Agent",
            "Summary Agent",
            "Notification Agent",
        ],
        "analyze": "POST /analyze with multipart form field named file",
        "assistant": "POST /assistant",
        "rag": {
            "index": "POST /rag/index with multipart form field named file",
            "query": "POST /rag/query",
            "delete": "DELETE /rag/documents/{document_id}",
            "reindex": "POST /rag/documents/{document_id}/reindex",
        },
    }


@app.post("/analyze", response_model=AnalysisResult)
async def analyze_document(file: UploadFile = File(...)):
    content = await file.read()
    return run_agentic_analysis(file.filename, content)


@app.post("/assistant", response_model=AssistantResponse)
async def assistant(request: AssistantRequest):
    detected_amount, _ = detect_amount(request.document_text)
    text_preview = " ".join(request.document_text.split())[:700]
    fallback = (
        f"Local document analysis found a total value of {detected_amount}. "
        f"Relevant extracted content: {text_preview or 'No readable document text was available.'}"
    )
    answer, used_llm = call_gemini(
        "You are an approval assistant agent. Answer from document text and metadata. Be concise and operational.",
        (
            f"Question: {request.question}\n"
            f"Document name: {request.document_name}\n"
            f"Metadata: {request.metadata}\n\n"
            f"Extracted document text:\n{request.document_text[:6000]}"
        ),
        fallback,
    )
    return AssistantResponse(answer=answer, model=GEMINI_MODEL, used_llm=used_llm)


@app.post("/rag/index", response_model=RagIndexResponse)
async def rag_index_document(
    file: UploadFile = File(...),
    document_id: str | None = Form(default=None),
    source_document: str | None = Form(default=None),
    owner_email: str = Form(default=""),
    owner_role: str = Form(default=""),
    allowed_roles: str | None = Form(default=None),
):
    content = await file.read()
    file_hash = content_hash(content)
    doc_id = normalize_document_id(document_id, file.filename)
    trace: list[AgentTrace] = []
    text, ocr_engine = ocr_agent(file.filename, content, trace)
    chunks = split_text_into_chunks(text)
    if not chunks:
        raise HTTPException(status_code=422, detail="No readable text was extracted for indexing")

    remove_rag_document(doc_id)
    index = load_rag_index()
    duplicate = find_duplicate_document(index, doc_id, file_hash)
    indexed_at = datetime.now().isoformat(timespec="milliseconds")
    metadata = {
        "document_id": doc_id,
        "source_document": source_document or file.filename,
        "owner_email": owner_email,
        "owner_role": owner_role,
        "allowed_roles": parse_allowed_roles(allowed_roles),
        "indexed_at": indexed_at,
        "chunk_count": len(chunks),
        "ocr_engine": ocr_engine,
        "content_hash": file_hash,
        "duplicate": duplicate is not None,
        "duplicate_of": duplicate.get("document_id") if duplicate else None,
    }
    index.setdefault("documents", {})[doc_id] = metadata
    for chunk_index, chunk in enumerate(chunks):
        index.setdefault("chunks", []).append(
            {
                "id": f"{doc_id}:{chunk_index}",
                "document_id": doc_id,
                "source_document": metadata["source_document"],
                "chunk_index": chunk_index,
                "text": chunk,
                "content_hash": file_hash,
                "embedding": embed_text(chunk),
            }
        )
    save_rag_index(index)
    return RagIndexResponse(
        document_id=doc_id,
        source_document=metadata["source_document"],
        chunk_count=len(chunks),
        ocr_engine=ocr_engine,
        indexed_at=indexed_at,
        content_hash=file_hash,
        duplicate=duplicate is not None,
        duplicate_of=metadata["duplicate_of"],
    )


@app.post("/rag/query", response_model=RagQueryResponse)
async def rag_query_documents(request: RagQueryRequest):
    question = request.question.strip()
    if not question:
        raise HTTPException(status_code=400, detail="Question is required")

    index = load_rag_index()
    documents = index.get("documents", {})
    requested_ids = {str(item) for item in request.document_ids if str(item).strip()}
    query_embedding = embed_text(question)
    scored: list[tuple[float, dict[str, Any]]] = []
    for chunk in index.get("chunks", []):
        document_id = str(chunk.get("document_id", ""))
        metadata = documents.get(document_id, {})
        if requested_ids and document_id not in requested_ids:
            continue
        if not can_access_rag_document(metadata, request.actor_email, request.actor_role):
            continue
        score = cosine_similarity(query_embedding, chunk.get("embedding", []))
        if score >= RAG_MIN_SCORE:
            scored.append((score, chunk))

    scored.sort(key=lambda item: item[0], reverse=True)
    top_k = max(1, min(request.top_k, 10))
    deduped: list[tuple[float, dict[str, Any]]] = []
    seen_content_chunks: set[tuple[str, int]] = set()
    for score, chunk in scored:
        dedupe_key = (str(chunk.get("content_hash", chunk.get("document_id", ""))), int(chunk.get("chunk_index", 0)))
        if dedupe_key in seen_content_chunks:
            continue
        seen_content_chunks.add(dedupe_key)
        deduped.append((score, chunk))
        if len(deduped) >= top_k:
            break
    sources = [
        RagSource(
            document_id=str(chunk.get("document_id", "")),
            source_document=str(chunk.get("source_document", "")),
            snippet=str(chunk.get("text", ""))[:700],
            score=round(score, 4),
            chunk_index=int(chunk.get("chunk_index", 0)),
        )
        for score, chunk in deduped
    ]
    answer, used_llm = build_rag_answer(question, sources)
    return RagQueryResponse(answer=answer, sources=sources, model=GEMINI_MODEL, used_llm=used_llm)


@app.delete("/rag/documents/{document_id}", response_model=RagDeleteResponse)
async def rag_delete_document(document_id: str):
    deleted = remove_rag_document(document_id)
    return RagDeleteResponse(document_id=document_id, deleted_chunks=deleted)


@app.post("/rag/documents/{document_id}/reindex", response_model=RagIndexResponse)
async def rag_reindex_document(
    document_id: str,
    file: UploadFile = File(...),
    source_document: str | None = Form(default=None),
    owner_email: str = Form(default=""),
    owner_role: str = Form(default=""),
    allowed_roles: str | None = Form(default=None),
):
    return await rag_index_document(
        file=file,
        document_id=document_id,
        source_document=source_document,
        owner_email=owner_email,
        owner_role=owner_role,
        allowed_roles=allowed_roles,
    )
