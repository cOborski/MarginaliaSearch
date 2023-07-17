CREATE TABLE IF NOT EXISTS FILE_STORAGE_BASE (
    ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    NAME VARCHAR(255) NOT NULL UNIQUE,
    PATH VARCHAR(255) NOT NULL UNIQUE COMMENT 'The path to the storage base',
    TYPE ENUM ('SSD_INDEX', 'SSD_WORK', 'SLOW', 'BACKUP') NOT NULL,
    MUST_CLEAN BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'If true, the storage must be cleaned after use',
    PERMIT_TEMP BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'If true, the storage can be used for temporary files'
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_bin;

CREATE TABLE IF NOT EXISTS FILE_STORAGE (
    ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    BASE_ID BIGINT NOT NULL,
    PATH VARCHAR(255) NOT NULL COMMENT 'The path to the storage relative to the base',
    DESCRIPTION VARCHAR(255) NOT NULL,
    TYPE ENUM ('CRAWL_SPEC', 'CRAWL_DATA', 'PROCESSED_DATA', 'INDEX_STAGING', 'LEXICON_STAGING', 'INDEX_LIVE', 'LEXICON_LIVE', 'SEARCH_SETS', 'BACKUP') NOT NULL,
    DO_PURGE BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'If true, the storage may be cleaned',
    CREATE_DATE TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT CONS UNIQUE (BASE_ID, PATH),
    FOREIGN KEY (BASE_ID) REFERENCES FILE_STORAGE_BASE(ID) ON DELETE CASCADE
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_bin;

CREATE VIEW FILE_STORAGE_VIEW
AS SELECT
    CONCAT(BASE.PATH, '/', STORAGE.PATH) AS PATH,
    STORAGE.TYPE AS TYPE,
    DESCRIPTION AS DESCRIPTION,
    CREATE_DATE AS CREATE_DATE,
    STORAGE.ID AS ID,
    BASE.ID AS BASE_ID
FROM FILE_STORAGE STORAGE
INNER JOIN FILE_STORAGE_BASE BASE ON STORAGE.BASE_ID=BASE.ID;
