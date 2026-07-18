package app.gemicom

object Sql {
    const val ENVIRONMENT = """
CREATE TABLE IF NOT EXISTS environment (
    "name" TEXT NOT NULL PRIMARY KEY,
    "value" TEXT
) WITHOUT ROWID;
"""

    const val DOCUMENT = """
CREATE TABLE IF NOT EXISTS document (
    id INTEGER PRIMARY KEY,
    tab_id INTEGER NOT NULL,
    url TEXT NOT NULL UNIQUE,
    content TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now', 'localtime')) NOT NULL,
    FOREIGN KEY(tab_id) REFERENCES tab(id) ON DELETE CASCADE
);
"""

    const val TABS = """
CREATE TABLE IF NOT EXISTS tab (
    id INTEGER PRIMARY KEY,
    status INTEGER NOT NULL DEFAULT 0,
    is_marked INTEGER NOT NULL DEFAULT 0,
    history TEXT NOT NULL DEFAULT '[]',
    position INTEGER NOT NULL DEFAULT 0,
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

    const val Document_Create = """INSERT OR REPLACE INTO document (tab_id, url, content) VALUES (?, ?, ?) RETURNING id"""
    const val Document_Has = """SELECT COUNT(*) FROM document WHERE tab_id=? AND url=?"""
    const val Document_Get = """SELECT content FROM document WHERE tab_id=? AND url=?"""
    const val Document_DeleteOld = """DELETE FROM document WHERE created_at < ?"""

    const val Tab_Create = """INSERT INTO tab DEFAULT VALUES RETURNING id, created_at"""
    const val Tab_Delete = """DELETE FROM tab WHERE id=?"""
    const val Tab_Purge = """DELETE FROM tab"""
    const val Tab_Get = """SELECT id, status, history->>'$[#-1]', created_at FROM tab WHERE id=?"""
    const val Tab_All = """SELECT id, status, history->>'$[#-1]', created_at FROM tab"""
    const val Tab_Count = """SELECT COUNT(*) from tab"""
    const val Tab_GetHistory = """SELECT json_each.value FROM tab, json_each(tab.history) WHERE tab.id=?"""
    const val Tab_SetHistory = """UPDATE tab SET history=? WHERE id=?"""
    const val Tab_GetPosition = """SELECT position FROM tab WHERE id=?"""
    const val Tab_SetPosition = """UPDATE tab SET position=? WHERE id=?"""
    const val Tab_SetStatus = """UPDATE tab SET status=? WHERE id=?"""

    const val Certificate_Create = """INSERT INTO certificate (host, hash) VALUES (?, ?)"""
    const val Certificate_Get = """SELECT hash, created_at FROM certificate WHERE host=?"""
    const val Certificate_Replace = """UPDATE certificate SET hash=? WHERE host=?"""
    const val Certificate_DeleteAll = """DELETE FROM certificate"""

    const val Cache_Create = """INSERT INTO cache (cache_id, filename, original_name) VALUES (?, ?, ?)"""
    const val Cache_All = """SELECT filename FROM cache"""
    const val Cache_GetFilename = """SELECT filename FROM cache WHERE cache_id=?"""
    const val Cache_Delete = """DELETE FROM cache WHERE cache_id=?"""

    const val Tmp_TabHistory_Insert = """INSERT INTO tab_history (tab_id, location) VALUES (?, ?)"""

    const val Env_Settings_Get = """SELECT json_extract(value, '$.' || ?) FROM environment WHERE name=?"""
    const val Env_Settings_Set_1 = """INSERT OR IGNORE INTO environment (name, value) VALUES (?, json_object(?, ?))"""
    const val Env_Settings_Set_2 = """UPDATE environment SET value=json_set(value, '$.' || ?, ?) WHERE name=?"""
    const val Env_Settings_Clear = """DELETE from environment WHERE name=?"""
}
