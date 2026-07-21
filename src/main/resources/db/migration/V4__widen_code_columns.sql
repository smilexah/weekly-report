-- Реальные Daribar-слаги (daribar_code = pharmacy_code) доходят до 80+ символов, что превышает
-- VARCHAR(64) - подтверждено падением загрузки реального daribar_crosswalk.xlsx на слаге длиной
-- 81 символ. pharmacy_code в daily_metrics - то же самое значение (см. описание daribar_crosswalk
-- в CLAUDE.md), поэтому под тем же риском. Расширяем все "кодовые" колонки разом до VARCHAR(255) -
-- как задел, чтобы не наступить на тот же класс ошибки на другом значении в будущем.
ALTER TABLE daily_metrics ALTER COLUMN pharmacy_code TYPE VARCHAR(255);
ALTER TABLE daily_metrics ALTER COLUMN branch_code TYPE VARCHAR(255);
ALTER TABLE pharmacy_directory ALTER COLUMN branch_code TYPE VARCHAR(255);
ALTER TABLE daribar_crosswalk ALTER COLUMN daribar_code TYPE VARCHAR(255);
ALTER TABLE daribar_crosswalk ALTER COLUMN branch_code TYPE VARCHAR(255);
