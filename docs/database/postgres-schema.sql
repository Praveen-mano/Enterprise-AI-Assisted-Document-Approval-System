CREATE TABLE IF NOT EXISTS app_users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255) NOT NULL,
  role_name VARCHAR(80) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  department VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS approval_documents (
  id BIGSERIAL PRIMARY KEY,
  filename VARCHAR(255) NOT NULL,
  document_type VARCHAR(120),
  document_category VARCHAR(120),
  routing_mode VARCHAR(120),
  library_category_id BIGINT,
  folder_id BIGINT,
  department VARCHAR(120),
  priority VARCHAR(40),
  status VARCHAR(120),
  amount_detected VARCHAR(80),
  confidence_score INTEGER,
  risk_score INTEGER,
  sensitivity_score INTEGER,
  compliance_score INTEGER,
  summary VARCHAR(4000),
  extracted_text VARCHAR(8000),
  agentic_decision VARCHAR(2000),
  approval_chain VARCHAR(2000),
  current_approver_role VARCHAR(120),
  clarification_note VARCHAR(2000),
  last_action_by VARCHAR(255),
  last_action_role VARCHAR(80),
  last_action_comment VARCHAR(2000),
  last_action_at TIMESTAMPTZ,
  owner_email VARCHAR(255),
  owner_role VARCHAR(80),
  notification_email VARCHAR(255),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_categories (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  description VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_folders (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  parent_id BIGINT REFERENCES document_folders(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_tags (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS approval_document_tags (
  document_id BIGINT NOT NULL REFERENCES approval_documents(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL REFERENCES document_tags(id) ON DELETE CASCADE,
  PRIMARY KEY (document_id, tag_id)
);


CREATE TABLE IF NOT EXISTS notification_records (
  id BIGSERIAL PRIMARY KEY,
  recipient_role VARCHAR(80) NOT NULL,
  channel VARCHAR(40) NOT NULL,
  title VARCHAR(255) NOT NULL,
  message VARCHAR(1200),
  read_flag BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_email VARCHAR(255) NOT NULL,
  actor_role VARCHAR(80) NOT NULL,
  action VARCHAR(120) NOT NULL,
  details VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Demo users are seeded by Spring Boot DataSeeder with BCrypt password:
-- enterprise-ai
