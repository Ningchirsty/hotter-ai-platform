-- Feed 凭据不存数据库。请求账本至少保留30天，不自动删除。
CREATE TABLE IF NOT EXISTS ai_template_request (
  request_id VARCHAR(36) NOT NULL,
  tenant_id VARCHAR(20) NOT NULL,
  user_id BIGINT NOT NULL,
  client_request_id VARCHAR(36) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  template_id VARCHAR(128) NOT NULL,
  revision INT NOT NULL,
  task_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  confirmed_not_submitted BOOLEAN NOT NULL DEFAULT FALSE,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (request_id),
  UNIQUE KEY uq_template_request_owner (tenant_id, user_id, client_request_id),
  KEY ix_template_request_recovery (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ai_template_validation_audit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id VARCHAR(20) NOT NULL,
  user_id BIGINT NOT NULL,
  template_id VARCHAR(128) NOT NULL,
  rules VARCHAR(1024) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_template_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
