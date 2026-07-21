-- branch_code больше не обязателен: если monthly_report.xlsx не указал его явно, он резолвится
-- через daribar_crosswalk (см. DailyMetricRepository.UPSERT_SQL); если сопоставить не удалось -
-- остаётся NULL (аптека попадает в строку "Без дивизиона / склад" листа "Дивизионы").
ALTER TABLE daily_metrics ALTER COLUMN branch_code DROP NOT NULL;

-- Заготовка из V2 ничем не используется - заменяется таблицами ниже.
DROP TABLE pharmacy_divisions;

-- Справочник аптека -> дивизион -> директор, загружается из divisions.xlsx при старте приложения
-- (см. ReferenceDataLoader), полная перезапись при каждой загрузке.
CREATE TABLE pharmacy_directory (
    branch_code   VARCHAR(64)  PRIMARY KEY,
    address       VARCHAR(255) NOT NULL,
    city          VARCHAR(255) NOT NULL,
    division_num  VARCHAR(32)  NOT NULL,
    director_name VARCHAR(255) NOT NULL
);

-- Сопоставление кода аптеки в Daribar (= pharmacy_code из monthly_report) с branch_code,
-- загружается из daribar_crosswalk.xlsx при старте приложения. Используется как fallback резолва
-- branch_code, когда monthly_report.xlsx не указал "Код филиала" явно.
CREATE TABLE daribar_crosswalk (
    daribar_code  VARCHAR(64)  PRIMARY KEY,
    branch_code   VARCHAR(64),
    pharmacy_name VARCHAR(255),
    comment       VARCHAR(500)
);
