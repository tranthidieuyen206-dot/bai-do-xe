CREATE TABLE IF NOT EXISTS gate_processed_job (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coordinator_job_id VARCHAR(64) NOT NULL,
  plate VARCHAR(64) NOT NULL,
  job_type VARCHAR(16) NOT NULL,
  processed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_coordinator_job (coordinator_job_id)
);
