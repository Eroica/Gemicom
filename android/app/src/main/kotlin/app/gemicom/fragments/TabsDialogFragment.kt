package app.gemicom.fragments

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.Button
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import app.gemicom.R
import app.gemicom.controllers.CustomDialog
import app.gemicom.controllers.ICancelListener
import app.gemicom.views.lists.TabsAdapter
import app.gemicom.views.models.TabsDialogViewModel
import kotlinx.coroutines.launch

interface ITabsDialog {
    val adapter: TabsAdapter

    fun onTabSwiped(position: Int)
    fun onTabSelected(position: Int)
}

interface ITabListener {
    fun onTabSelected(id: Long)
    fun onTabClosed(id: Long)
    fun onNewTab()
    fun onClosedAll()
}

class SimpleItemTouchHelperCallback(private val listener: ITabsDialog) : ItemTouchHelper.Callback() {
    override fun isLongPressDragEnabled(): Boolean {
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return true
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
        listener.onTabSwiped(viewHolder.bindingAdapterPosition)
    }

    override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        viewHolder?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

class TabsDialogFragment : AppCompatDialogFragment(),
    ITabsDialog,
    ICancelListener {
    private val viewModel: TabsDialogViewModel by viewModels()
    private var listener: ITabListener? = null

    override val adapter = TabsAdapter(this)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as ITabListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = CustomDialog(requireContext())
        dialog.window?.let {
            it.setBackgroundDrawableResource(android.R.color.transparent)
            it.setGravity(Gravity.BOTTOM)
            it.setWindowAnimations(R.style.BottomDialogStyle)
        }

        val layout = layoutInflater.inflate(R.layout.dialog_tabs, dialog.findViewById(R.id.container), false)
        val list = layout.findViewById<RecyclerView>(android.R.id.list)
        list.setHasFixedSize(true)
        list.adapter = adapter
        val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(this))
        itemTouchHelper.attachToRecyclerView(list)

        layout.findViewById<Button>(R.id.buttonNewTab).setOnClickListener {
            viewModel.viewModelScope.launch {
                viewModel.new()
                listener?.onNewTab()
            }
        }

        dialog.setTitle(getString(R.string.dialog_tabs_title))
            .setCancel(getString(R.string.dialog_tabs_button_close_all), this)
            .addView(layout)

        return dialog
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            viewModel.initialization.join()
            viewModel.tabs.observe(this@TabsDialogFragment) {
                adapter.submitList(it)
            }
        }
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    override fun onCancel() {
        lifecycleScope.launch {
            viewModel.closeAll()
            listener?.onClosedAll()
            dismiss()
        }
    }

    override fun onTabSwiped(position: Int) {
        lifecycleScope.launch {
            viewModel.tabs.value.orEmpty().getOrNull(position)?.let {
                viewModel.close(it.id)
                listener?.onTabClosed(it.id)
            }
        }
    }

    override fun onTabSelected(position: Int) {
        viewModel.tabs.value.orEmpty().getOrNull(position)?.let {
            listener?.onTabSelected(it.id)
        }
        dismiss()
    }
}
