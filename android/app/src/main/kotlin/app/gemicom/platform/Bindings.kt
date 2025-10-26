package app.gemicom.platform

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.IdRes
import coil.ImageLoader
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class ViewRefs {
    private var root: View? = null
    private val views = mutableMapOf<Int, View>()

    fun setRoot(root: View) {
        this@ViewRefs.root = root
    }

    fun <T : View> bind(@IdRes id: Int): () -> T {
        @Suppress("UNCHECKED_CAST")
        return {
            val view = views[id] ?: root!!.findViewById<T>(id).also { views[id] = it }
            view as T
        }
    }

    fun clear() {
        views.clear()
        root = null
    }
}

inline fun View.onClickLaunch(scope: CoroutineScope, crossinline block: suspend () -> Unit) {
    setOnClickListener {
        scope.launch { block() }
    }
}

fun EditText.textChanges(): Flow<CharSequence?> = callbackFlow {
    val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            trySend(s)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
    addTextChangedListener(watcher)
    awaitClose { removeTextChangedListener(watcher) }
}

fun ClipboardManager.addListener(): Flow<Unit> = callbackFlow {
    val listener = ClipboardManager.OnPrimaryClipChangedListener {
        trySend(Unit)
    }
    addPrimaryClipChangedListener(listener)
    awaitClose { removePrimaryClipChangedListener(listener) }
}

fun ClipboardManager.content() = primaryClip?.getItemAt(0)?.text?.toString() ?: ""

fun ImageView.loadOrToast(
    data: Any?,
    imageLoader: ImageLoader,
    context: Context,
    errorMessage: String
) {
    load(data, imageLoader) {
        listener(onError = { _, _ ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        })
    }
}
