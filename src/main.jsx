import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { AnimatePresence, motion } from "framer-motion";
import {
  Bell,
  Bot,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleUserRound,
  Clock3,
  CloudUpload,
  FileSearch,
  FileText,
  FolderTree,
  LayoutDashboard,
  LoaderCircle,
  LogOut,
  MessageSquareText,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  Tags,
  UploadCloud,
  X,
  XCircle
} from "lucide-react";
import "./styles.css";

const AI_API_URL = import.meta.env.VITE_AI_API_URL || "http://localhost:8000";
const BACKEND_API_URL = import.meta.env.VITE_BACKEND_API_URL || "http://localhost:8080";

const roleEmails = {
  Employee: "employee@enterprise.ai",
  HR: "hr@enterprise.ai",
  Manager: "manager@enterprise.ai",
  CFO: "cfo@enterprise.ai",
  Admin: "admin@enterprise.ai"
};

const navByRole = {
  Employee: ["Dashboard", "Upload", "Library", "History", "Notifications"],
  General: ["Dashboard", "Send", "Library", "History", "Notifications"],
  HR: ["Dashboard", "Review", "Library", "History", "Notifications"],
  Manager: ["Dashboard", "Review", "Library", "History", "Notifications"],
  CFO: ["Dashboard", "Review", "Library", "History", "Notifications"],
  Admin: ["Dashboard", "Admin", "Library", "History", "Notifications"]
};

const processingSteps = [
  "Secure upload",
  "OCR extraction",
  "Layout analysis",
  "Document classification",
  "Financial scoring",
  "Approval routing",
  "AI summary"
];

function cx(...classes) {
  return classes.filter(Boolean).join(" ");
}

function tagNames(document) {
  return (document.tags || []).map((tag) => tag.name).filter(Boolean);
}

function isApproverRole(role) {
  return ["HR", "Manager", "CFO"].includes(role);
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : "Unknown";
}

function folderLabel(folder) {
  return folder?.path || folder?.name || "Unfiled";
}

async function backendRequest(path, token, options = {}) {
  const response = await fetch(`${BACKEND_API_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      Authorization: `Bearer ${token}`,
      ...options.headers
    }
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.detail || payload.message || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

function Panel({ children, className = "" }) {
  return <section className={cx("panel", className)}>{children}</section>;
}

function StatusBadge({ status = "PENDING" }) {
  const normalized = status.toUpperCase();
  const labels = {
    PENDING: "Pending",
    APPROVED: "Approved",
    REJECTED: "Rejected",
    CLARIFICATION: "Needs clarification"
  };
  return <span className={cx("status-badge", `status-${normalized.toLowerCase()}`)}>{labels[normalized] || status}</span>;
}

function Login({ onLogin }) {
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState(roleEmails.Employee);
  const [password, setPassword] = useState("enterprise-ai");
  const [displayName, setDisplayName] = useState("");
  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");
  const [registerRole, setRegisterRole] = useState("Employee");
  const [department, setDepartment] = useState("Operations");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const response = await fetch(`${BACKEND_API_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
      });
      if (!response.ok) throw new Error("Invalid email or password.");
      const result = await response.json();
      if (!navByRole[result.user.role]) throw new Error("This account does not have access to this application.");
      onLogin(result);
    } catch (loginError) {
      setError(`${loginError.message} Check that Spring Boot is running on ${BACKEND_API_URL}.`);
    } finally {
      setLoading(false);
    }
  }

  async function register(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const response = await fetch(`${BACKEND_API_URL}/api/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          displayName,
          email: registerEmail,
          password: registerPassword,
          role: registerRole,
          department
        })
      });
      if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        throw new Error(payload.message || payload.detail || "Account creation failed.");
      }
      const result = await response.json();
      onLogin(result);
    } catch (registerError) {
      setError(`${registerError.message} Backend URL: ${BACKEND_API_URL}`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-brand">
        <span className="brand-mark"><ShieldCheck /></span>
        <div>
          <p className="brand-kicker">ApprovalOS</p>
          <h1>AI document approvals, routed with context.</h1>
          <p>Secure human decisions backed by OCR, document intelligence, and policy-aware routing.</p>
        </div>
        <div className="login-flow">
          {["Extract", "Understand", "Route", "Decide"].map((item, index) => (
            <div key={item}><span>{index + 1}</span>{item}</div>
          ))}
        </div>
      </div>

      <form className="login-card" onSubmit={mode === "login" ? submit : register}>
        <div>
          <p className="eyebrow">{mode === "login" ? "Secure workspace" : "Create access"}</p>
          <h2>{mode === "login" ? "Sign in" : "Create account"}</h2>
          <p className="muted">
            {mode === "login"
              ? "Use a seeded account or an account created by your team."
              : "Create an Employee account. Approval roles are managed by the organization."}
          </p>
        </div>

        {mode === "login" ? (
          <>
            <label className="field">
              <span>Email</span>
              <input value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" />
            </label>
            <label className="field">
              <span>Password</span>
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
            </label>
            <div className="demo-users">
              {Object.entries(roleEmails).map(([role, value]) => (
                <button type="button" key={role} onClick={() => setEmail(value)}>{role}</button>
              ))}
            </div>
          </>
        ) : (
          <>
            <label className="field">
              <span>Full name</span>
              <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" />
            </label>
            <label className="field">
              <span>Email</span>
              <input value={registerEmail} onChange={(event) => setRegisterEmail(event.target.value)} autoComplete="username" />
            </label>
            <label className="field">
              <span>Password <em>minimum 8 characters</em></span>
              <input type="password" value={registerPassword} onChange={(event) => setRegisterPassword(event.target.value)} autoComplete="new-password" />
            </label>
            <div className="two-fields">
              <label className="field">
                <span>Account type</span>
                <select value={registerRole} onChange={(event) => setRegisterRole(event.target.value)}>
                  <option value="Employee">Employee</option>
                </select>
              </label>
              <label className="field">
                <span>Department</span>
                <input value={department} onChange={(event) => setDepartment(event.target.value)} />
              </label>
            </div>
          </>
        )}

        {error && <p className="error-box">{error}</p>}
        <button className="primary-btn full" disabled={loading}>
          {loading ? <LoaderCircle className="spin" /> : <ShieldCheck />}
          {loading ? (mode === "login" ? "Authenticating" : "Creating account") : (mode === "login" ? "Continue securely" : "Create and continue")}
        </button>
        <button
          type="button"
          className="auth-switch"
          onClick={() => {
            setMode(mode === "login" ? "register" : "login");
            setError("");
          }}
        >
          {mode === "login" ? "Create Employee account" : "Back to sign in"}
        </button>
      </form>
    </main>
  );
}

function Header({ user, active, setActive, onLogout, notificationCount }) {
  return (
    <header className="app-header">
      <button className="app-brand" onClick={() => setActive("Dashboard")}>
        <span className="brand-mark small"><ShieldCheck /></span>
        <span><strong>ApprovalOS</strong><small>AI approval workspace</small></span>
      </button>
      <nav className="main-nav">
        {navByRole[user.role].map((item) => {
          const Icon = item === "Dashboard" ? LayoutDashboard : item === "Upload" ? UploadCloud : item === "Send" ? Send : item === "Review" ? FileSearch : item === "Admin" ? ShieldCheck : item === "Library" ? FolderTree : item === "History" ? Clock3 : Bell;
          return (
            <button key={item} className={cx(active === item && "active")} onClick={() => setActive(item)}>
              <Icon />
              <span>{item}</span>
              {item === "Notifications" && notificationCount > 0 && <b>{notificationCount}</b>}
            </button>
          );
        })}
      </nav>
      <div className="user-menu">
        <span className="user-avatar"><CircleUserRound /></span>
        <span className="user-copy"><strong>{user.name}</strong><small>{user.role}</small></span>
        <button className="icon-btn" onClick={onLogout} title="Sign out"><LogOut /></button>
      </div>
    </header>
  );
}

function Metric({ label, value, icon: Icon, tone }) {
  return (
    <div className={cx("metric", tone)}>
      <span className="metric-icon"><Icon /></span>
      <div><small>{label}</small><strong>{value}</strong></div>
    </div>
  );
}

function DocumentRows({ documents, onSelect }) {
  if (!documents.length) {
    return <div className="empty-state"><FileText /><strong>No documents yet</strong><p>Documents relevant to this workspace will appear here.</p></div>;
  }
  return (
    <div className="document-rows">
      {documents.map((document) => (
        <button key={document.id} className="document-row" onClick={() => onSelect?.(document)}>
          <span className="file-icon"><FileText /></span>
          <span className="document-main">
            <strong>{document.filename}</strong>
            <small>{document.documentType || "General document"} · {document.amountDetected || "No amount detected"}</small>
          </span>
          <StatusBadge status={document.status} />
          <ChevronRight className="row-arrow" />
        </button>
      ))}
    </div>
  );
}

function Dashboard({ user, documents, dashboardStats, setActive, onSelect }) {
  const counts = dashboardStats || {
    total: 0,
    pending: 0,
    approved: 0,
    rejected: 0,
    clarification: 0
  };
  return (
    <div className="screen-stack">
      <div className="screen-heading">
        <div>
          <p className="eyebrow">{user.role} workspace</p>
          <h1>{user.role === "Employee" ? "My document activity" : user.role === "General" ? "Sent document activity" : user.role === "Admin" ? "System administration" : "Approval overview"}</h1>
            <p>{user.role === "Employee" ? "Upload invoices or general documents and follow every human decision." : user.role === "General" ? "Analyze documents and send general documents directly to a specific approval role." : user.role === "Admin" ? "Monitor routing, categories, policy outcomes, and action history." : `Review documents routed specifically to ${user.role}.`}</p>
          </div>
        <button className="primary-btn" onClick={() => setActive(user.role === "Employee" ? "Upload" : user.role === "General" ? "Send" : user.role === "Admin" ? "Admin" : "Review")}>
          {user.role === "Employee" || user.role === "General" ? <CloudUpload /> : user.role === "Admin" ? <ShieldCheck /> : <FileSearch />}
          {user.role === "Employee" ? "Upload document" : user.role === "General" ? "Send document" : user.role === "Admin" ? "Open admin" : "Open queue"}
        </button>
      </div>

      <div className="metrics-grid">
        <Metric label={user.role === "Employee" ? "Submitted" : user.role === "General" ? "Sent" : "Assigned"} value={counts.total} icon={FileText} tone="cyan" />
        <Metric label="Awaiting decision" value={counts.pending} icon={Clock3} tone="amber" />
        <Metric label="Approved" value={counts.approved} icon={CheckCircle2} tone="green" />
        <Metric label="Rejected" value={counts.rejected} icon={XCircle} tone="red" />
        <Metric label="Needs clarification" value={counts.clarification} icon={MessageSquareText} tone="violet" />
      </div>

      <Panel>
        <div className="section-heading">
          <div><h2>{user.role === "Employee" || user.role === "General" ? "Recent documents" : "Assigned documents"}</h2><p>Latest activity from PostgreSQL</p></div>
          <button className="text-btn" onClick={() => setActive(user.role === "Employee" ? "Upload" : user.role === "General" ? "Send" : user.role === "Admin" ? "Admin" : "Review")}>View workspace <ChevronRight /></button>
        </div>
        <DocumentRows documents={documents.slice(0, 8)} onSelect={(document) => {
          onSelect(document);
          if (!["Employee", "General"].includes(user.role)) setActive("Review");
        }} />
      </Panel>
    </div>
  );
}

function UploadScreen({ user, token, onCreated, setActive, libraryMetadata }) {
  const inputRef = useRef(null);
  const redirectTimerRef = useRef(null);
  const [files, setFiles] = useState([]);
  const [notes, setNotes] = useState("");
  const [notificationEmail, setNotificationEmail] = useState("");
  const [documentCategory, setDocumentCategory] = useState("Invoice Documents");
  const [libraryCategoryId, setLibraryCategoryId] = useState("");
  const [folderId, setFolderId] = useState("");
  const [tagInput, setTagInput] = useState("");
  const [targetRole, setTargetRole] = useState("HR");
  const [dragging, setDragging] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [step, setStep] = useState(0);
  const [progress, setProgress] = useState(0);
  const [progressTarget, setProgressTarget] = useState(0);
  const [processingStatus, setProcessingStatus] = useState("Waiting to start");
  const [error, setError] = useState("");
  const [warning, setWarning] = useState("");

  useEffect(() => {
    if (!processing) return undefined;
    const progressTimer = window.setInterval(() => {
      setProgress((current) => {
        if (current >= progressTarget) return current;
        const gap = progressTarget - current;
        const next = current + Math.max(0.35, gap * 0.08);
        return Math.min(progressTarget, next);
      });
    }, 120);
    return () => window.clearInterval(progressTimer);
  }, [processing, progressTarget]);

  useEffect(() => () => window.clearTimeout(redirectTimerRef.current), []);

  function addFiles(fileList) {
    const incoming = Array.from(fileList || []);
    setFiles((current) => {
      const existingKeys = new Set(current.map((item) => `${item.name}:${item.size}:${item.lastModified}`));
      const next = [...current];
      for (const item of incoming) {
        const key = `${item.name}:${item.size}:${item.lastModified}`;
        if (!existingKeys.has(key)) {
          existingKeys.add(key);
          next.push(item);
        }
      }
      return next;
    });
  }

  async function analyze() {
    if (!files.length) {
      setError("Choose one or more PDF, DOCX, PNG, or JPG documents.");
      return;
    }
    window.clearTimeout(redirectTimerRef.current);
    setProcessing(true);
    setStep(0);
    setProgress(0);
    setProgressTarget(8);
    setProcessingStatus("Preparing upload");
    setError("");
    setWarning("");
    const timer = window.setInterval(() => setStep((current) => Math.min(current + 1, processingSteps.length - 1)), 850);
    let currentOperation = "analyzing document";
    let completedSuccessfully = false;
    try {
      const savedDocuments = [];
      for (const [index, file] of files.entries()) {
        const fileStart = files.length ? (index / files.length) * 100 : 0;
        const fileShare = files.length ? 100 / files.length : 100;
        currentOperation = `analyzing ${file.name}`;
        setProcessingStatus(`Analyzing ${file.name}`);
        setProgressTarget(Math.min(88, fileStart + fileShare * 0.72));
        const formData = new FormData();
        formData.append("file", file);
        const aiResponse = await fetch(`${AI_API_URL}/analyze`, { method: "POST", body: formData });
        if (!aiResponse.ok) throw new Error(`AI analysis failed for ${file.name} (${aiResponse.status}).`);
        const analysis = await aiResponse.json();
        currentOperation = `saving ${file.name}`;
        setStep(processingSteps.length - 2);
        setProcessingStatus(`Saving ${file.name}`);
        setProgressTarget(Math.min(94, fileStart + fileShare * 0.88));
        const saved = await backendRequest("/api/documents", token, {
          method: "POST",
          body: JSON.stringify({
            filename: analysis.filename || file.name,
            documentType: analysis.document_type,
            department: analysis.department,
            priority: analysis.priority,
            amountDetected: analysis.amount_detected,
            confidenceScore: Math.round((analysis.confidence || 0) * 100),
            riskScore: analysis.risk_score,
            sensitivityScore: analysis.sensitivity_score,
            complianceScore: analysis.compliance_score,
            summary: analysis.summary,
            extractedText: analysis.extracted_text,
            agenticDecision: analysis.agentic_decision,
            notificationEmail,
            documentCategory,
            libraryCategoryId: libraryCategoryId ? Number(libraryCategoryId) : null,
            folderId: folderId ? Number(folderId) : null,
            tagNames: tagInput.split(",").map((tag) => tag.trim()).filter(Boolean),
            routingMode: documentCategory === "Invoice Documents" ? "LLM_RAG_INVOICE" : "MANUAL_GENERAL",
            approvalChain: documentCategory === "General Documents" ? targetRole : ""
          })
        });
        currentOperation = `indexing ${file.name} for RAG`;
        setStep(processingSteps.length - 1);
        setProcessingStatus(`Indexing ${file.name}`);
        setProgressTarget(Math.min(98, fileStart + fileShare * 0.96));
        const ragForm = new FormData();
        ragForm.append("file", file);
        ragForm.append("approvalDocumentId", saved.id);
        try {
          await backendRequest("/api/rag/documents", token, {
            method: "POST",
            body: ragForm
          });
        } catch (ragError) {
          setWarning(`Saved ${file.name}, but RAG indexing failed: ${ragError.message}`);
        }
        savedDocuments.push(saved);
        setProgressTarget(Math.min(98, fileStart + fileShare));
      }
      setProcessingStatus("Analysis complete");
      setProgressTarget(100);
      setProgress(100);
      onCreated(savedDocuments);
      setFiles([]);
      setNotes("");
      setNotificationEmail("");
      setDocumentCategory("Invoice Documents");
      setLibraryCategoryId("");
      setFolderId("");
      setTagInput("");
      setTargetRole("HR");
      completedSuccessfully = true;
      redirectTimerRef.current = window.setTimeout(() => {
        setProcessing(false);
        setActive("Dashboard");
      }, 500);
    } catch (uploadError) {
      setError(`${currentOperation} failed: ${uploadError.message}`);
    } finally {
      window.clearInterval(timer);
      if (!completedSuccessfully) {
        setProcessing(false);
      }
    }
  }

  if (processing) {
    const progressPercent = Math.round(progress);
    return (
      <Panel className="processing-panel">
        <div className="processing-orbit"><Sparkles /><span /></div>
        <p className="eyebrow">Agentic analysis in progress</p>
        <h1>{processingSteps[step]}</h1>
        <p>{processingStatus}</p>
        <div className="analysis-progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progressPercent}>
          <div className="analysis-progress-label">
            <span>Processing document</span>
            <strong>{progressPercent}%</strong>
          </div>
          <div className="analysis-progress-bar">
            <span style={{ width: `${progressPercent}%` }} />
          </div>
        </div>
        <div className="processing-track">
          {processingSteps.map((item, index) => (
            <div key={item} className={cx(index < step && "done", index === step && "current")}>
              <span>{index < step ? <Check /> : index + 1}</span>
              <small>{item}</small>
            </div>
          ))}
        </div>
      </Panel>
    );
  }

  return (
    <div className="upload-layout">
      <div className="screen-heading compact">
        <div>
          <p className="eyebrow">{user.role === "General" ? "Direct role assignment" : "Employee submission"}</p>
          <h1>{user.role === "General" ? "Send a document" : "Upload a document"}</h1>
          <p>Invoice documents use LLM/RAG-style routing. General documents go directly to the role you select.</p>
        </div>
      </div>
      <Panel className="upload-panel">
        <input ref={inputRef} hidden multiple type="file" accept=".pdf,.docx,.png,.jpg,.jpeg" onChange={(event) => addFiles(event.target.files)} />
        <button
          className={cx("drop-zone", dragging && "dragging")}
          onClick={() => inputRef.current?.click()}
          onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            addFiles(event.dataTransfer.files);
          }}
        >
          <span><UploadCloud /></span>
          <strong>{files.length ? `${files.length} document${files.length === 1 ? "" : "s"} selected` : "Drop your documents here"}</strong>
          <small>{files.length ? "PDF, DOCX, PNG and JPG ready for analysis" : "PDF, DOCX, PNG or JPG"}</small>
        </button>
        {!!files.length && (
          <div className="selected-files">
            {files.map((item, index) => (
              <div key={`${item.name}:${item.size}:${item.lastModified}`}>
                <span><FileText /></span>
                <strong>{item.name}</strong>
                <small>{(item.size / 1024 / 1024).toFixed(2)} MB</small>
                <button type="button" className="icon-btn" onClick={() => setFiles((current) => current.filter((_, itemIndex) => itemIndex !== index))}><X /></button>
              </div>
            ))}
          </div>
        )}
        <label className="field">
          <span>Context for the approver <em>optional</em></span>
          <textarea value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Add a short business reason or useful background." />
        </label>
        <label className="field">
          <span>Approval routing type</span>
          <select value={documentCategory} onChange={(event) => setDocumentCategory(event.target.value)}>
            <option value="Invoice Documents">Invoice Documents</option>
            <option value="General Documents">General Documents</option>
          </select>
        </label>
        <div className="two-fields">
          <label className="field">
            <span>Library category</span>
            <select value={libraryCategoryId} onChange={(event) => setLibraryCategoryId(event.target.value)}>
              <option value="">Auto detect</option>
              {(libraryMetadata.categories || []).map((category) => (
                <option value={category.id} key={category.id}>{category.name}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Folder</span>
            <select value={folderId} onChange={(event) => setFolderId(event.target.value)}>
              <option value="">Unfiled</option>
              {(libraryMetadata.folders || []).map((folder) => (
                <option value={folder.id} key={folder.id}>{folderLabel(folder)}</option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span>Tags <em>comma separated</em></span>
          <input
            value={tagInput}
            onChange={(event) => setTagInput(event.target.value)}
            placeholder="urgent, vendor, compliance"
            list="library-tags"
          />
          <datalist id="library-tags">
            {(libraryMetadata.tags || []).map((tag) => <option value={tag.name} key={tag.id} />)}
          </datalist>
        </label>
        <label className="field">
          <span>Recipient email for validation report <em>optional</em></span>
          <input
            type="email"
            value={notificationEmail}
            onChange={(event) => setNotificationEmail(event.target.value)}
            placeholder="Enter any email address"
          />
        </label>
        <div className="routing-note">
          <Bell />
          <div>
            <strong>Dynamic email delivery</strong>
            <p>The uploader is always notified. If you enter another email here, the final status and AI summary will also be sent to that address.</p>
          </div>
        </div>
        {documentCategory === "General Documents" && (
          <label className="field">
            <span>Send directly to</span>
            <select className="role-select" value={targetRole} onChange={(event) => setTargetRole(event.target.value)}>
              <option value="HR">HR</option>
              <option value="Manager">Manager</option>
              <option value="CFO">CFO</option>
            </select>
          </label>
        )}
        <div className="routing-note">
          <Bot />
          <div>
            <strong>{documentCategory === "General Documents" ? "Manual general document routing" : "Invoice LLM/RAG routing"}</strong>
            <p>{documentCategory === "General Documents" ? `This general document will be assigned directly to ${targetRole}.` : "The invoice is analyzed for amount, risk, compliance, and sensitivity, then routed to HR, Manager, or CFO by modular rules."}</p>
          </div>
        </div>
        {error && <p className="error-box">{error}</p>}
        {warning && <p className="warning-box">{warning}</p>}
        <button className="primary-btn full" onClick={analyze}><Sparkles />{documentCategory === "General Documents" ? `Analyze and send ${files.length || "documents"} to ${targetRole}` : `Analyze ${files.length || "documents"} and route`}</button>
      </Panel>
    </div>
  );
}

function ReviewScreen({ user, token, documents, selected, setSelected, onUpdated, openAssistant }) {
  const queue = documents.filter((item) => item.status === "PENDING");
  const [note, setNote] = useState("");
  const [showClarification, setShowClarification] = useState(false);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const selectedDocument = selected && queue.some((item) => item.id === selected.id)
    ? queue.find((item) => item.id === selected.id)
    : null;
  const document = selectedDocument || queue[0] || null;

  useEffect(() => {
    if (document && (!selected || selected.id !== document.id)) setSelected(document);
  }, [document, selected, setSelected]);

  async function decide(action) {
    if (!document) return;
    if (action === "CLARIFICATION" && !note.trim()) {
      setError("Describe what the employee must change or provide.");
      return;
    }
    setBusy(action);
    setError("");
    try {
      const updated = await backendRequest(`/api/documents/${document.id}/decision`, token, {
        method: "PUT",
        body: JSON.stringify({ action, note })
      });
      onUpdated(updated);
      setSelected(null);
      setNote("");
      setShowClarification(false);
    } catch (decisionError) {
      setError(decisionError.message);
    } finally {
      setBusy("");
    }
  }

  return (
    <div className="review-shell">
      <aside className="review-queue">
        <div className="queue-title"><div><p className="eyebrow">{user.role}</p><h2>Approval queue</h2></div><span>{queue.length}</span></div>
        <div className="queue-list">
          {queue.map((item) => (
            <button key={item.id} className={cx("queue-item", document?.id === item.id && "active")} onClick={() => setSelected(item)}>
              <span className="file-icon"><FileText /></span>
              <span><strong>{item.filename}</strong><small>{item.documentType} · {item.amountDetected}</small></span>
              <StatusBadge status={item.status} />
            </button>
          ))}
          {!queue.length && <div className="queue-empty"><CheckCircle2 /><strong>Queue clear</strong><p>No documents are assigned to {user.role}.</p></div>}
        </div>
      </aside>

      <div className="review-content">
        {!document ? (
          <Panel className="empty-state large"><CheckCircle2 /><strong>Nothing needs review</strong><p>New assignments will appear automatically after an employee upload.</p></Panel>
        ) : (
          <div className="split-review">
            <Panel className="document-pane">
              <div className="document-title">
                <div><p className="eyebrow">Document review</p><h1>{document.filename}</h1></div>
                <StatusBadge status={document.status} />
              </div>
              <div className="document-facts">
                <div><small>Category</small><strong>{document.documentCategory || "General Documents"}</strong></div>
                <div><small>Type</small><strong>{document.documentType}</strong></div>
                <div><small>Detected amount</small><strong>{document.amountDetected}</strong></div>
                <div><small>Assigned to</small><strong>{document.approvalChain || user.role}</strong></div>
              </div>
              <div className="document-preview">
                <div className="preview-toolbar"><FileText /><span>Extracted document text</span></div>
                <pre>{document.extractedText || "No readable text was extracted. Review the original upload before deciding."}</pre>
              </div>
              {document.status === "CLARIFICATION" && document.clarificationNote && (
                <div className="clarification-box"><MessageSquareText /><div><strong>Clarification requested</strong><p>{document.clarificationNote}</p></div></div>
              )}
              {document.status === "PENDING" && (
                <div className="decision-area">
                  <label className="field">
                    <span>{showClarification ? "Required changes or missing information" : "Decision comment"} <em>{showClarification ? "required" : "optional"}</em></span>
                    <textarea autoFocus={showClarification} value={note} onChange={(event) => setNote(event.target.value)} placeholder={showClarification ? "Give the employee specific instructions." : "Add a note for the action history."} />
                  </label>
                  {error && <p className="error-box">{error}</p>}
                  <div className="decision-buttons">
                    <button className="decision-btn approve" disabled={!!busy} onClick={() => decide("APPROVED")}><CheckCircle2 />Approve</button>
                    <button className="decision-btn reject" disabled={!!busy} onClick={() => decide("REJECTED")}><XCircle />Reject</button>
                    <button className="decision-btn clarify" disabled={!!busy} onClick={() => showClarification ? decide("CLARIFICATION") : setShowClarification(true)}><MessageSquareText />{showClarification ? "Send request" : "Clarify"}</button>
                  </div>
                </div>
              )}
            </Panel>

            <Panel className="insights-pane">
              <div className="insights-title"><span><Sparkles /></span><div><p className="eyebrow">AI insights</p><h2>Decision brief</h2></div></div>
              <div className="insight-block"><small>AI summary</small><p>{document.summary || "Summary unavailable."}</p></div>
              <div className="score-grid">
                <div><small>Risk</small><strong>{document.riskScore || 0}<span>/100</span></strong></div>
                <div><small>Compliance</small><strong>{document.complianceScore || 0}<span>/100</span></strong></div>
                <div><small>Sensitivity</small><strong>{document.sensitivityScore || 0}<span>/100</span></strong></div>
              </div>
              <div className="insight-block"><small>Routing explanation</small><p>{document.agenticDecision || `The approval engine assigned this document to ${document.approvalChain}.`}</p></div>
              <div className="insight-block"><small>Latest action</small><p>{document.lastActionAt ? `${document.lastActionRole || "System"} - ${document.lastActionComment || document.status} (${new Date(document.lastActionAt).toLocaleString()})` : "No action history yet."}</p></div>
              <div className="human-control"><ShieldCheck /><div><strong>Human decision required</strong><p>AI can extract, summarize, and route. It cannot approve or reject this document.</p></div></div>
              <button className="assistant-btn" onClick={() => openAssistant(document)}><Bot />Ask AI about this document <ChevronRight /></button>
            </Panel>
          </div>
        )}
      </div>
    </div>
  );
}

function WorkflowBuilder({ token, workflows = [], onSaved = () => {} }) {
  const emptyStep = { stepOrder: 1, approvalMode: "SEQUENTIAL", approverRoles: "HR", dueHours: 24, escalationAction: "NOTIFY", escalationRole: "Manager" };
  const [draft, setDraft] = useState({
    id: null,
    name: "",
    enabled: true,
    documentType: "",
    documentCategory: "",
    department: "",
    minAmount: "",
    maxAmount: "",
    priority: 100,
    steps: [emptyStep]
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  function updateDraft(key, value) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function updateStep(index, key, value) {
    setDraft((current) => ({
      ...current,
      steps: current.steps.map((step, stepIndex) => stepIndex === index ? { ...step, [key]: value } : step)
    }));
  }

  function addStep() {
    setDraft((current) => ({
      ...current,
      steps: [...current.steps, { ...emptyStep, stepOrder: current.steps.length + 1, approverRoles: "Manager" }]
    }));
  }

  function removeStep(index) {
    setDraft((current) => ({
      ...current,
      steps: current.steps.filter((_, stepIndex) => stepIndex !== index).map((step, stepIndex) => ({ ...step, stepOrder: stepIndex + 1 }))
    }));
  }

  async function saveWorkflow() {
    setSaving(true);
    setError("");
    try {
      const endpoint = draft.id ? `/api/workflows/${draft.id}` : "/api/workflows";
      const method = draft.id ? "PUT" : "POST";
      await backendRequest(endpoint, token, {
        method,
        body: JSON.stringify({
          ...draft,
          minAmount: draft.minAmount === "" ? null : Number(draft.minAmount),
          maxAmount: draft.maxAmount === "" ? null : Number(draft.maxAmount),
          priority: Number(draft.priority) || 100,
          steps: draft.steps.map((step, index) => ({
            ...step,
            stepOrder: index + 1,
            dueHours: Number(step.dueHours) || 24
          }))
        })
      });
      setDraft({ id: null, name: "", enabled: true, documentType: "", documentCategory: "", department: "", minAmount: "", maxAmount: "", priority: 100, steps: [emptyStep] });
      onSaved();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  function editWorkflow(workflow) {
    setDraft({
      id: workflow.id,
      name: workflow.name || "",
      enabled: workflow.enabled !== false,
      documentType: workflow.documentType || "",
      documentCategory: workflow.documentCategory || "",
      department: workflow.department || "",
      minAmount: workflow.minAmount ?? "",
      maxAmount: workflow.maxAmount ?? "",
      priority: workflow.priority || 100,
      steps: (workflow.steps?.length ? workflow.steps : [emptyStep]).map((step, index) => ({
        stepOrder: index + 1,
        approvalMode: step.approvalMode || "SEQUENTIAL",
        approverRoles: step.approverRoles || "HR",
        dueHours: step.dueHours || 24,
        escalationAction: step.escalationAction || "NOTIFY",
        escalationRole: step.escalationRole || "Manager"
      }))
    });
  }

  async function deleteWorkflow(workflowId) {
    setSaving(true);
    setError("");
    try {
      await backendRequest(`/api/workflows/${workflowId}`, token, { method: "DELETE" });
      onSaved();
    } catch (deleteError) {
      setError(deleteError.message);
    } finally {
      setSaving(false);
    }
  }

  async function toggleWorkflow(workflow) {
    setSaving(true);
    setError("");
    try {
      await backendRequest(`/api/workflows/${workflow.id}/enabled`, token, {
        method: "PATCH",
        body: JSON.stringify({ enabled: !workflow.enabled })
      });
      onSaved();
    } catch (toggleError) {
      setError(toggleError.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Panel>
      <div className="section-heading">
        <div><h2>Configurable workflows</h2><p>Match by document type, approval category, department, or amount. Blank criteria act as wildcards.</p></div>
      </div>
      <div className="workflow-builder">
        <div className="workflow-form-grid">
          <label className="field"><span>Name</span><input value={draft.name} onChange={(event) => updateDraft("name", event.target.value)} placeholder="High value finance review" /></label>
          <label className="field"><span>Document type</span><input value={draft.documentType} onChange={(event) => updateDraft("documentType", event.target.value)} placeholder="Invoice, Contract..." /></label>
          <label className="field"><span>Approval category</span><select value={draft.documentCategory} onChange={(event) => updateDraft("documentCategory", event.target.value)}><option value="">Any category</option><option value="Invoice Documents">Invoice Documents</option><option value="General Documents">General Documents</option></select></label>
          <label className="field"><span>Department</span><input value={draft.department} onChange={(event) => updateDraft("department", event.target.value)} placeholder="Finance, HR..." /></label>
          <label className="field"><span>Min amount</span><input type="number" value={draft.minAmount} onChange={(event) => updateDraft("minAmount", event.target.value)} placeholder="100000" /></label>
          <label className="field"><span>Max amount</span><input type="number" value={draft.maxAmount} onChange={(event) => updateDraft("maxAmount", event.target.value)} placeholder="1000000" /></label>
          <label className="field"><span>Priority</span><input type="number" value={draft.priority} onChange={(event) => updateDraft("priority", event.target.value)} /></label>
        </div>

        <div className="workflow-steps">
          {draft.steps.map((step, index) => (
            <div className="workflow-step" key={index}>
              <span className="step-number">{index + 1}</span>
              <label className="field"><span>Mode</span><select value={step.approvalMode} onChange={(event) => updateStep(index, "approvalMode", event.target.value)}><option value="SEQUENTIAL">Single approver</option><option value="PARALLEL">Parallel approvers</option></select></label>
              <label className="field"><span>Approver roles <em>comma separated</em></span><input value={step.approverRoles} onChange={(event) => updateStep(index, "approverRoles", event.target.value)} placeholder="HR, Manager, CFO" /></label>
              <label className="field"><span>Due hours</span><input type="number" min="1" value={step.dueHours} onChange={(event) => updateStep(index, "dueHours", event.target.value)} /></label>
              <label className="field"><span>Escalation</span><select value={step.escalationAction} onChange={(event) => updateStep(index, "escalationAction", event.target.value)}><option value="NOTIFY">Notify overdue role</option><option value="REASSIGN">Reassign overdue task</option></select></label>
              <label className="field"><span>Reassign to</span><select value={step.escalationRole} onChange={(event) => updateStep(index, "escalationRole", event.target.value)}><option value="HR">HR</option><option value="Manager">Manager</option><option value="CFO">CFO</option></select></label>
              <button type="button" className="icon-btn" onClick={() => removeStep(index)} disabled={draft.steps.length === 1}><X /></button>
            </div>
          ))}
        </div>

        {error && <p className="error-box">{error}</p>}
        <div className="workflow-actions">
          <button type="button" className="text-btn" onClick={addStep}><ChevronRight />Add approval level</button>
          <button type="button" className="primary-btn" onClick={saveWorkflow} disabled={saving}><ShieldCheck />{saving ? "Saving" : draft.id ? "Update workflow" : "Save workflow"}</button>
        </div>
      </div>

      <div className="workflow-list">
        {workflows.map((workflow) => (
          <div className="workflow-item" key={workflow.id}>
            <div><strong>{workflow.name}</strong><small>{workflow.enabled ? "Enabled" : "Disabled"} · Priority {workflow.priority}</small></div>
            <span>{workflow.documentType || "Any type"}</span>
            <span>{workflow.documentCategory || "Any category"}</span>
            <span>{workflow.department || "Any department"}</span>
            <span>{workflow.minAmount || workflow.maxAmount ? `${workflow.minAmount ?? 0} - ${workflow.maxAmount ?? "∞"}` : "Any amount"}</span>
            <span>{(workflow.steps || []).map((step) => step.approverRoles).join(" -> ")}</span>
            <span className="workflow-row-actions">
              <button type="button" className="text-btn" onClick={() => editWorkflow(workflow)}>Edit</button>
              <button type="button" className="text-btn" onClick={() => toggleWorkflow(workflow)}>{workflow.enabled ? "Disable" : "Enable"}</button>
              <button type="button" className="text-btn" onClick={() => deleteWorkflow(workflow.id)}>Delete</button>
            </span>
          </div>
        ))}
        {!workflows.length && <div className="empty-state"><ShieldCheck /><strong>No workflows configured</strong><p>The legacy routing policy will continue to handle documents until an admin creates a matching workflow.</p></div>}
      </div>
    </Panel>
  );
}

function AdminDashboard({ documents, audits, dashboardStats, workflows = [], token, onWorkflowSaved = () => {}, onSelect }) {
  const counts = dashboardStats || {
    total: 0,
    invoices: 0,
    general: 0,
    pending: 0,
    completed: 0
  };
  const rules = [
    "Invoice amount >= INR 10 lakh, risk >= 75, or compliance >= 80 routes to CFO.",
    "Invoice amount >= INR 1 lakh, risk >= 45, or compliance >= 50 routes to Manager.",
    "Lower-signal invoices route to HR.",
    "General documents use manual role selection by the submitter."
  ];

  return (
    <div className="screen-stack">
      <div className="screen-heading">
        <div>
          <p className="eyebrow">Admin dashboard</p>
          <h1>Routing, categories, and action history</h1>
          <p>Monitor invoice automation, manual general-document routing, and role decisions.</p>
        </div>
      </div>

      <div className="metrics-grid">
        <Metric label="Total documents" value={counts.total} icon={FileText} tone="cyan" />
        <Metric label="Invoice documents" value={counts.invoices} icon={FileSearch} tone="green" />
        <Metric label="General documents" value={counts.general} icon={MessageSquareText} tone="violet" />
        <Metric label="Pending" value={counts.pending} icon={Clock3} tone="amber" />
        <Metric label="Completed actions" value={counts.completed} icon={CheckCircle2} tone="red" />
      </div>

      <div className="admin-grid">
        <Panel>
          <div className="section-heading">
            <div><h2>Modular routing rules</h2><p>Designed so thresholds can be changed later in the backend routing policy.</p></div>
          </div>
          <div className="rule-list">
            {rules.map((rule, index) => <div key={rule}><span>{index + 1}</span><p>{rule}</p></div>)}
          </div>
        </Panel>

        <Panel>
          <div className="section-heading">
            <div><h2>Recent action history</h2><p>Timestamped comments from submissions and role decisions.</p></div>
          </div>
          <div className="audit-list">
            {audits.slice(0, 8).map((audit) => (
              <div key={audit.id} className="audit-item">
                <strong>{audit.action} · {audit.actorRole}</strong>
                <p>{audit.documentName || "Document"} - {audit.comment || audit.details}</p>
                <small>{new Date(audit.createdAt).toLocaleString()} · {audit.actorEmail}</small>
              </div>
            ))}
            {!audits.length && <div className="empty-state"><Clock3 /><strong>No action history yet</strong><p>Submissions and approvals will appear here.</p></div>}
          </div>
        </Panel>
      </div>

      <WorkflowBuilder token={token} workflows={workflows} onSaved={onWorkflowSaved} />

      <Panel>
        <div className="section-heading">
          <div><h2>All documents</h2><p>Category, route, owner, current status, and latest comment.</p></div>
        </div>
        <div className="admin-table">
          {documents.map((document) => (
            <button key={document.id} onClick={() => onSelect?.(document)}>
              <span><strong>{document.filename}</strong><small>{document.ownerEmail}</small></span>
              <span>{document.documentCategory || "General Documents"}</span>
              <span>{document.routingMode || "MANUAL_GENERAL"}</span>
              <span>{document.approvalChain || "Unassigned"}</span>
              <StatusBadge status={document.status} />
            </button>
          ))}
          {!documents.length && <div className="empty-state"><FileText /><strong>No documents available</strong><p>Uploaded documents will appear here.</p></div>}
        </div>
      </Panel>
    </div>
  );
}

function DocumentLibrary({ user, token, metadata, onSelect, setActive }) {
  const [filters, setFilters] = useState({
    search: "",
    categoryId: "",
    folderId: "",
    tag: "",
    status: "",
    owner: "",
    sortBy: "uploadedAt",
    direction: "desc"
  });
  const [libraryDocuments, setLibraryDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  function updateFilter(key, value) {
    setFilters((current) => ({ ...current, [key]: value }));
  }

  useEffect(() => {
    let activeRequest = true;
    async function loadLibrary() {
      setLoading(true);
      setError("");
      try {
        const params = new URLSearchParams();
        Object.entries(filters).forEach(([key, value]) => {
          if (value) params.set(key, value);
        });
        const result = await backendRequest(`/api/library/documents?${params.toString()}`, token);
        if (activeRequest) setLibraryDocuments(result);
      } catch (loadError) {
        if (activeRequest) setError(loadError.message);
      } finally {
        if (activeRequest) setLoading(false);
      }
    }
    loadLibrary();
    return () => {
      activeRequest = false;
    };
  }, [token, filters]);

  return (
    <div className="screen-stack">
      <div className="screen-heading">
        <div>
          <p className="eyebrow">Document library</p>
          <h1>Browse organized documents</h1>
          <p>Find documents by name, category, folder, tags, status, owner, and upload date.</p>
        </div>
      </div>

      <Panel className="library-toolbar">
        <label className="field search-field">
          <span>Search</span>
          <div><Search /><input value={filters.search} onChange={(event) => updateFilter("search", event.target.value)} placeholder="Search name, tags, owner, status..." /></div>
        </label>
        <label className="field">
          <span>Category</span>
          <select value={filters.categoryId} onChange={(event) => updateFilter("categoryId", event.target.value)}>
            <option value="">All categories</option>
            {(metadata.categories || []).map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Folder</span>
          <select value={filters.folderId} onChange={(event) => updateFilter("folderId", event.target.value)}>
            <option value="">All folders</option>
            {(metadata.folders || []).map((folder) => <option value={folder.id} key={folder.id}>{folderLabel(folder)}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Tag</span>
          <select value={filters.tag} onChange={(event) => updateFilter("tag", event.target.value)}>
            <option value="">All tags</option>
            {(metadata.tags || []).map((tag) => <option value={tag.name} key={tag.id}>{tag.name}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Status</span>
          <select value={filters.status} onChange={(event) => updateFilter("status", event.target.value)}>
            <option value="">All status</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="CLARIFICATION">Needs clarification</option>
          </select>
        </label>
        <label className="field">
          <span>Owner</span>
          <input value={filters.owner} onChange={(event) => updateFilter("owner", event.target.value)} placeholder="email address" />
        </label>
        <div className="two-fields library-sort">
          <label className="field">
            <span>Sort by</span>
            <select value={filters.sortBy} onChange={(event) => updateFilter("sortBy", event.target.value)}>
              <option value="uploadedAt">Upload date</option>
              <option value="name">Document name</option>
              <option value="category">Category</option>
              <option value="folder">Folder</option>
              <option value="status">Status</option>
              <option value="owner">Owner</option>
            </select>
          </label>
          <label className="field">
            <span>Order</span>
            <select value={filters.direction} onChange={(event) => updateFilter("direction", event.target.value)}>
              <option value="desc">Descending</option>
              <option value="asc">Ascending</option>
            </select>
          </label>
        </div>
      </Panel>

      <Panel>
        <div className="section-heading">
          <div><h2>Documents</h2><p>{loading ? "Loading library" : `${libraryDocuments.length} document${libraryDocuments.length === 1 ? "" : "s"} found`}</p></div>
        </div>
        {error && <p className="error-box">{error}</p>}
        <div className="library-table">
          {libraryDocuments.map((document) => (
            <button key={document.id} onClick={() => {
              onSelect(document);
              setActive(isApproverRole(user.role) ? "Review" : "Dashboard");
            }}>
              <span className="file-icon"><FileText /></span>
              <span className="library-main">
                <strong>{document.filename}</strong>
                <small>{document.libraryCategory?.name || document.documentType || "General Document"} · {folderLabel(document.folder)}</small>
              </span>
              <span className="tag-list">
                {tagNames(document).length ? tagNames(document).map((tag) => <b key={tag}>{tag}</b>) : <em>No tags</em>}
              </span>
              <span>{document.ownerEmail || "Unknown owner"}</span>
              <span>{formatDate(document.createdAt)}</span>
              <StatusBadge status={document.status} />
            </button>
          ))}
          {!libraryDocuments.length && !loading && <div className="empty-state"><FolderTree /><strong>No matching documents</strong><p>Adjust the filters or upload a new document with category, folder, and tags.</p></div>}
        </div>
      </Panel>
    </div>
  );
}

function DocumentHistory({ documents, token }) {
  const [selectedId, setSelectedId] = useState(documents[0]?.id || "");
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!selectedId && documents[0]?.id) {
      setSelectedId(documents[0].id);
    }
  }, [documents, selectedId]);

  useEffect(() => {
    let activeRequest = true;
    async function loadHistory() {
      if (!selectedId) {
        setEvents([]);
        return;
      }
      setLoading(true);
      setError("");
      try {
        const result = await backendRequest(`/api/audit/documents/${selectedId}`, token);
        if (activeRequest) setEvents(result);
      } catch (historyError) {
        if (activeRequest) setError(historyError.message);
      } finally {
        if (activeRequest) setLoading(false);
      }
    }
    loadHistory();
    return () => {
      activeRequest = false;
    };
  }, [selectedId, token]);

  const selectedDocument = documents.find((document) => String(document.id) === String(selectedId));

  return (
    <div className="history-shell">
      <aside className="history-documents">
        <div className="queue-title"><div><p className="eyebrow">Document history</p><h2>Documents</h2></div><span>{documents.length}</span></div>
        <div className="queue-list">
          {documents.map((document) => (
            <button key={document.id} className={cx("queue-item", String(selectedId) === String(document.id) && "active")} onClick={() => setSelectedId(document.id)}>
              <span className="file-icon"><FileText /></span>
              <span><strong>{document.filename}</strong><small>{document.documentType || "Document"} · {formatDate(document.createdAt)}</small></span>
              <StatusBadge status={document.status} />
            </button>
          ))}
          {!documents.length && <div className="queue-empty"><Clock3 /><strong>No documents</strong><p>History appears after documents are uploaded.</p></div>}
        </div>
      </aside>

      <Panel className="history-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Audit trail</p>
            <h2>{selectedDocument?.filename || "Select a document"}</h2>
            <p>Complete activity from upload through final decision.</p>
          </div>
          {selectedDocument && <StatusBadge status={selectedDocument.status} />}
        </div>
        {error && <p className="error-box">{error}</p>}
        {loading ? (
          <div className="page-loader"><LoaderCircle className="spin" />Loading history</div>
        ) : (
          <div className="timeline-list">
            {events.map((event) => (
              <div className="timeline-item" key={event.id}>
                <span className="timeline-dot"><Clock3 /></span>
                <div>
                  <strong>{event.action}</strong>
                  <p>{event.comment || event.details || "Activity recorded"}</p>
                  {event.details && event.details !== event.comment && <small>{event.details}</small>}
                  <em>{new Date(event.createdAt).toLocaleString()} · {event.actorRole} · {event.actorEmail}</em>
                </div>
              </div>
            ))}
            {!events.length && selectedDocument && <div className="empty-state"><Clock3 /><strong>No events yet</strong><p>This document does not have recorded history entries yet.</p></div>}
            {!selectedDocument && <div className="empty-state"><FileText /><strong>Select a document</strong><p>Choose a document to view its timeline.</p></div>}
          </div>
        )}
      </Panel>
    </div>
  );
}

function NotificationsScreen({ notifications }) {
  return (
    <div className="screen-stack">
      <div className="screen-heading compact"><div><p className="eyebrow">Activity center</p><h1>Notifications</h1><p>Assignments and document decisions relevant to your account.</p></div></div>
      <Panel>
        <div className="notification-list">
          {notifications.map((item) => (
            <div className="notification-item" key={item.id}>
              <span><Bell /></span>
              <div><strong>{item.title}</strong><p>{item.message}</p><small>{new Date(item.createdAt).toLocaleString()}</small></div>
            </div>
          ))}
          {!notifications.length && <div className="empty-state"><Bell /><strong>No notifications</strong><p>New document activity will appear here.</p></div>}
        </div>
      </Panel>
    </div>
  );
}

function AssistantModal({ document, token, onClose }) {
  const [question, setQuestion] = useState("What is the total amount and the main approval risk?");
  const [answer, setAnswer] = useState("");
  const [loading, setLoading] = useState(false);

  async function ask() {
    setLoading(true);
    setAnswer("");
    try {
      const result = await backendRequest("/api/rag/query", token, {
        method: "POST",
        body: JSON.stringify({
          question,
          documentIds: [document.id],
          topK: 5
        })
      });
      const sources = (result.sources || []).map((source) => `Source: ${source.source_document}\n${source.snippet}`).join("\n\n");
      setAnswer(sources ? `${result.answer}\n\n${sources}` : result.answer);
    } catch (assistantError) {
      setAnswer(`${assistantError.message} Check the backend RAG service on ${BACKEND_API_URL}.`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <motion.div className="modal-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <motion.div className="assistant-modal" initial={{ y: 24, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 24, opacity: 0 }}>
        <div className="modal-title"><div><span><Bot /></span><div><p className="eyebrow">Gemini assistant</p><h2>Ask about {document.filename}</h2></div></div><button className="icon-btn" onClick={onClose}><X /></button></div>
        <div className="prompt-chips">
          {["Why was this routed here?", "Summarize payment details", "What information is missing?"].map((prompt) => <button key={prompt} onClick={() => setQuestion(prompt)}>{prompt}</button>)}
        </div>
        <textarea className="assistant-input" value={question} onChange={(event) => setQuestion(event.target.value)} />
        <button className="primary-btn full" onClick={ask} disabled={loading}>{loading ? <LoaderCircle className="spin" /> : <Send />}{loading ? "Analyzing" : "Ask AI"}</button>
        {answer && <div className="assistant-answer"><Sparkles /><p>{answer}</p></div>}
      </motion.div>
    </motion.div>
  );
}

function App() {
  const [session, setSession] = useState(() => {
    try {
      return JSON.parse(window.sessionStorage.getItem("approval-session")) || null;
    } catch {
      return null;
    }
  });
  const [active, setActive] = useState("Dashboard");
  const [documents, setDocuments] = useState([]);
  const [dashboardStats, setDashboardStats] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [audits, setAudits] = useState([]);
  const [workflows, setWorkflows] = useState([]);
  const [libraryMetadata, setLibraryMetadata] = useState({ categories: [], folders: [], tags: [] });
  const [selected, setSelected] = useState(null);
  const [assistantDocument, setAssistantDocument] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");

  async function refresh() {
    if (!session) return;
    setLoading(true);
    setLoadError("");
    try {
      const requests = [
        backendRequest("/api/documents", session.token),
        backendRequest("/api/documents/dashboard-stats", session.token),
        backendRequest("/api/notifications", session.token),
        backendRequest("/api/library/metadata", session.token)
      ];
      if (session.user.role === "Admin") {
        requests.push(backendRequest("/api/workflows", session.token));
        requests.push(backendRequest("/api/audit", session.token));
      }
      const [documentData, dashboardStatsData, notificationData, metadataData, workflowData = [], auditData = []] = await Promise.all(requests);
      setDocuments(documentData);
      setDashboardStats(dashboardStatsData);
      setNotifications(notificationData);
      setLibraryMetadata(metadataData);
      setWorkflows(session.user.role === "Admin" ? workflowData : []);
      setAudits(auditData);
    } catch (error) {
      setLoadError(error.message);
      if (error.message.includes("401") || error.message.includes("403")) logout();
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, [session]);

  function login(result) {
    window.sessionStorage.setItem("approval-session", JSON.stringify(result));
    setSession(result);
    setActive("Dashboard");
  }

  function logout() {
    window.sessionStorage.removeItem("approval-session");
    setSession(null);
    setDocuments([]);
    setDashboardStats(null);
    setNotifications([]);
    setAudits([]);
    setWorkflows([]);
    setLibraryMetadata({ categories: [], folders: [], tags: [] });
    setSelected(null);
  }

  if (!session) return <Login onLogin={login} />;

  const user = session.user;
  let content;
  if (active === "Dashboard") {
    content = <Dashboard user={user} documents={documents} dashboardStats={dashboardStats} setActive={setActive} onSelect={setSelected} />;
  } else if ((active === "Upload" && user.role === "Employee") || (active === "Send" && user.role === "General")) {
    content = <UploadScreen user={user} token={session.token} setActive={setActive} libraryMetadata={libraryMetadata} onCreated={(created) => {
      const createdDocuments = Array.isArray(created) ? created : [created];
      setDocuments((current) => [...createdDocuments, ...current]);
      refresh();
    }} />;
  } else if (active === "Admin" && user.role === "Admin") {
    content = <AdminDashboard documents={documents} audits={audits} dashboardStats={dashboardStats} workflows={workflows} token={session.token} onWorkflowSaved={refresh} onSelect={setSelected} />;
  } else if (active === "Library") {
    content = <DocumentLibrary user={user} token={session.token} metadata={libraryMetadata} onSelect={setSelected} setActive={setActive} />;
  } else if (active === "History") {
    content = <DocumentHistory documents={documents} token={session.token} />;
  } else if (active === "Review" && isApproverRole(user.role)) {
    content = (
      <ReviewScreen
        user={user}
        token={session.token}
        documents={documents}
        selected={selected}
        setSelected={setSelected}
        openAssistant={setAssistantDocument}
        onUpdated={(updated) => {
          setDocuments((current) => current.map((item) => item.id === updated.id ? updated : item));
          refresh();
        }}
      />
    );
  } else {
    content = <NotificationsScreen notifications={notifications} />;
  }

  return (
    <div className="app-shell">
      <Header user={user} active={active} setActive={setActive} onLogout={logout} notificationCount={notifications.filter((item) => !item.readFlag).length} />
      <main className="app-main">
        {loadError && <div className="global-error">{loadError}<button onClick={refresh}><RefreshCw />Retry</button></div>}
        {loading && !documents.length ? <div className="page-loader"><LoaderCircle className="spin" />Loading workspace</div> : content}
      </main>
      <AnimatePresence>{assistantDocument && <AssistantModal document={assistantDocument} token={session.token} onClose={() => setAssistantDocument(null)} />}</AnimatePresence>
    </div>
  );
}

createRoot(document.getElementById("root")).render(<App />);
