-- Дневные значения метрик, накопленные из monthly_report.xlsx.
-- Файл каждый раз содержит всю историю с начала месяца/года, поэтому ключ
-- (pharmacy_code, metric_num, metric_date) уникален и используется для upsert (ON CONFLICT).
CREATE TABLE daily_metrics (
    pharmacy_code VARCHAR(64)  NOT NULL,
    branch_code   VARCHAR(64)  NOT NULL,
    metric_num    SMALLINT     NOT NULL,
    metric_date   DATE         NOT NULL,
    value         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (pharmacy_code, metric_num, metric_date)
);

CREATE INDEX idx_daily_metrics_date ON daily_metrics (metric_date);
CREATE INDEX idx_daily_metrics_metric_num ON daily_metrics (metric_num);

-- Аудит обработанных файлов из Telegram-канала. telegram_file_id уникален и служит
-- ключом идемпотентности: если update с тем же файлом придёт повторно (ретрай/рестарт бота),
-- файл не будет обработан заново.
CREATE TABLE ingested_files (
    id                BIGSERIAL PRIMARY KEY,
    telegram_file_id  VARCHAR(255) NOT NULL UNIQUE,
    file_name         VARCHAR(255) NOT NULL,
    ingested_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_count         INTEGER      NOT NULL
);
