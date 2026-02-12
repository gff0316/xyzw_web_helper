CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(200) NOT NULL,
  token VARCHAR(200) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS user_bins (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  file_path VARCHAR(255) NOT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS user_tokens (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  bin_id BIGINT NOT NULL,
  uuid VARCHAR(36) NOT NULL,
  name VARCHAR(100) NULL,
  token TEXT NOT NULL,
  server VARCHAR(100) NULL,
  ws_url VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS job_schedule_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_key VARCHAR(64) NOT NULL UNIQUE,
  job_name VARCHAR(128) NOT NULL,
  cron_expr VARCHAR(128) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_job_schedule_config_enabled (enabled)
);

CREATE TABLE IF NOT EXISTS job_execution_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_key VARCHAR(64) NOT NULL,
  job_name VARCHAR(128) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  trigger_source VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  message VARCHAR(255) NULL,
  details TEXT NULL,
  duration_ms BIGINT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NULL,
  created_at DATETIME NOT NULL,
  KEY idx_job_execution_log_job_key_id (job_key, id),
  KEY idx_job_execution_log_start_time (start_time)
);
