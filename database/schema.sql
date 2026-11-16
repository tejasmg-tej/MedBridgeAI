-- MedBridge AI - database creation script (MySQL 8)
-- Run this ONCE before starting the application (or skip it: Hibernate can also create the tables on first start).

CREATE DATABASE IF NOT EXISTS medbridge_ai
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE medbridge_ai;

CREATE TABLE IF NOT EXISTS reports (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    file_name             VARCHAR(255) NOT NULL,
    patient_label         VARCHAR(100) NULL,
    status                VARCHAR(20)  NOT NULL,
    extracted_text        LONGTEXT     NULL,
    simplified_summary    TEXT         NULL,
    diagnosis_as_stated   TEXT         NULL,
    hospital_stay_summary TEXT         NULL,
    error_message         VARCHAR(1000) NULL,
    uploaded_at           DATETIME(6)  NOT NULL,
    analyzed_at           DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS medications (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    report_id    BIGINT        NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    dosage       VARCHAR(200)  NULL,
    frequency    VARCHAR(200)  NULL,
    duration     VARCHAR(200)  NULL,
    instructions VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_medications_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS follow_ups (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    report_id        BIGINT        NOT NULL,
    provider         VARCHAR(200)  NULL,
    purpose          VARCHAR(500)  NULL,
    appointment_date VARCHAR(100)  NULL,
    details          VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_follow_ups_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS checklist_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    report_id    BIGINT        NOT NULL,
    category     VARCHAR(20)   NOT NULL,
    description  VARCHAR(1000) NOT NULL,
    completed    TINYINT(1)    NOT NULL DEFAULT 0,
    completed_at DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_checklist_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS report_precautions (
    report_id  BIGINT        NOT NULL,
    precaution VARCHAR(1000) NULL,
    CONSTRAINT fk_precautions_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS report_missing_info (
    report_id BIGINT        NOT NULL,
    item      VARCHAR(1000) NULL,
    CONSTRAINT fk_missing_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
) ENGINE = InnoDB;
