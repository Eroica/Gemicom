package app.gemicom.models

import app.gemicom.IDb
import app.gemicom.Sql
import java.sql.Types

interface IPreferences {
    operator fun get(name: String): String?
    operator fun set(name: String, value: String?)
    operator fun set(name: String, value: Boolean)
    fun clear()
}

class SqlPreferences(
    private val prefs: String,
    private val db: IDb
) : IPreferences {
    override fun get(name: String): String? {
        return db.query(Sql.Env_Settings_Get, {
            it.setString(1, name)
            it.setString(2, prefs)
        }) {
            if (it.next()) {
                it.getString(1)
            } else {
                null
            }
        }
    }

    override fun set(name: String, value: String?) {
        db.transaction {
            db.update(Sql.Env_Settings_Set_1) {
                it.setString(1, prefs)
                it.setString(2, name)
                if (value == null) {
                    it.setNull(3, Types.VARCHAR)
                } else {
                    it.setString(3, value)
                }
            }
            db.update(Sql.Env_Settings_Set_2) {
                it.setString(1, name)
                if (value == null) {
                    it.setNull(2, Types.VARCHAR)
                } else {
                    it.setString(2, value)
                }
                it.setString(3, prefs)
            }
        }
    }

    override fun set(name: String, value: Boolean) {
        db.transaction {
            db.update(Sql.Env_Settings_Set_1) {
                it.setString(1, prefs)
                it.setString(2, name)
                it.setBoolean(3, value)
            }
            db.update(Sql.Env_Settings_Set_2) {
                it.setString(1, name)
                it.setBoolean(2, value)
                it.setString(3, prefs)
            }
        }
    }

    override fun clear() {
        db.update(Sql.Env_Settings_Clear) {
            it.setString(1, prefs)
        }
    }
}

class AppSettings(private val prefs: IPreferences) {
    var isDarkTheme: Boolean
        get() = prefs["isDarkTheme"] == "1"
        set(value) {
            prefs["isDarkTheme"] = if (value) "1" else "0"
        }

    var home: String
        get() = prefs["home"] ?: ""
        set(value) {
            prefs["home"] = value
        }

    var isShowImagesInline: Boolean
        get() = prefs["isShowImagesInline"] == "1"
        set(value) {
            prefs["isShowImagesInline"] = if (value) "1" else "0"
        }

    var selectedTab: Int
        get() = prefs["selectedTab"]?.toInt() ?: 0
        set(value) {
            prefs["selectedTab"] = value.toString()
        }

    var isDebug: Boolean
        get() = prefs["isDebug"] == "1"
        set(value) {
            prefs["isDebug"] = if (value) "1" else "0"
        }

    fun clear() {
        prefs.clear()
    }
}
