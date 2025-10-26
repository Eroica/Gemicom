package app.gemicom

const val ENVIRONMENT = """
CREATE TABLE IF NOT EXISTS environment (
    "name" TEXT NOT NULL PRIMARY KEY,
    "value" TEXT
) WITHOUT ROWID;
"""

const val DOCUMENT = """
CREATE TABLE IF NOT EXISTS document (
    id INTEGER PRIMARY KEY,
    url TEXT NOT NULL UNIQUE,
    content TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now', 'localtime')) NOT NULL
);
"""

const val TABS = """
CREATE TABLE IF NOT EXISTS tab (
    id INTEGER PRIMARY KEY,
    status INTEGER NOT NULL DEFAULT 0,
    is_marked INTEGER NOT NULL DEFAULT 0,
    history TEXT NOT NULL DEFAULT '[]',
    created_at TEXT DEFAULT (datetime('now', 'localtime')) NOT NULL
);
"""

const val CACHE = """
CREATE TABLE IF NOT EXISTS cache (
    cache_id INTEGER NOT NULL,
    filename TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now', 'localtime')) NOT NULL
);
"""

const val CERTIFICATE = """
CREATE TABLE IF NOT EXISTS certificate (
    host TEXT NOT NULL PRIMARY KEY,
    hash TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now', 'localtime')) NOT NULL
) WITHOUT ROWID;
"""

enum class Sql {
    Document_Create, Document_Has,
    Document_Get,
    Document_DeleteOld,
    Tab_Create, Tab_Delete, Tab_Purge,
    Tab_Get, Tab_All,
    Tab_Count,
    Tab_GetHistory, Tab_SetHistory,
    Tab_SetStatus,
    Certificate_Create,
    Certificate_Get, Certificate_Replace,
    Certificate_DeleteAll,
    Cache_Create,
    Cache_All, Cache_GetFilename,
    Cache_Delete,
    Tmp_TabHistory_Insert,
    Env_Settings_Get,
    Env_Settings_Set_1, Env_Settings_Set_2,
    Env_Settings_Clear;

    companion object {
        operator fun invoke(sql: Sql): String = when (sql) {
            Document_Create -> """INSERT OR REPLACE INTO document (url, content) VALUES (?, ?) RETURNING id"""
            Document_Has -> """SELECT COUNT(*) FROM document WHERE url=?"""
            Document_Get -> """SELECT content FROM document WHERE url=?"""
            Document_DeleteOld -> """DELETE FROM document WHERE created_at < ?"""
            Tab_Create -> """INSERT INTO tab DEFAULT VALUES RETURNING id, created_at"""
            Tab_Delete -> """DELETE FROM tab WHERE id=?"""
            Tab_Purge -> """DELETE FROM tab"""
            Tab_Get -> """SELECT id, status, history->>'$[#-1]', created_at FROM tab WHERE id=?"""
            Tab_All -> """SELECT id, status, history->>'$[#-1]', created_at FROM tab"""
            Tab_Count -> """SELECT COUNT(*) from tab"""
            Tab_GetHistory -> """SELECT json_each.value FROM tab, json_each(tab.history) WHERE tab.id=?"""
            Tab_SetHistory -> """UPDATE tab SET history=? WHERE id=?"""
            Tab_SetStatus -> """UPDATE tab SET status=? WHERE id=?"""
            Certificate_Create -> """INSERT INTO certificate (host, hash) VALUES (?, ?)"""
            Certificate_Get -> """SELECT hash, created_at FROM certificate WHERE host=?"""
            Certificate_Replace -> """UPDATE certificate SET hash=? WHERE host=?"""
            Certificate_DeleteAll -> """DELETE FROM certificate"""
            Cache_Create -> """INSERT INTO cache (cache_id, filename, original_name) VALUES (?, ?, ?)"""
            Cache_All -> """SELECT filename FROM cache"""
            Cache_GetFilename -> """SELECT filename FROM cache WHERE cache_id=?"""
            Cache_Delete -> """DELETE FROM cache WHERE cache_id=?"""
            Tmp_TabHistory_Insert -> """INSERT INTO tab_history (tab_id, location) VALUES (?, ?)"""
            Env_Settings_Get -> """SELECT json_extract(value, '$.' || ?) FROM environment WHERE name=?"""
            Env_Settings_Set_1 -> """INSERT OR IGNORE INTO environment (name, value) VALUES (?, json_object(?, ?))"""
            Env_Settings_Set_2 -> """UPDATE environment SET value=json_set(value, '$.' || ?, ?) WHERE name=?"""
            Env_Settings_Clear -> """DELETE from environment WHERE name=?"""
        }
    }
}
