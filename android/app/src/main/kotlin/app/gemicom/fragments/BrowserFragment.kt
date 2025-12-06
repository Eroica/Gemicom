package app.gemicom.fragments

import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.gemicom.*
import app.gemicom.models.ITab
import app.gemicom.platform.*
import app.gemicom.views.models.BrowserViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.kodein.di.conf.DIGlobalAware

class BrowserPageAdapter(
    fragment: Fragment,
    private var tabs: List<ITab>
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun createFragment(position: Int): Fragment {
        return BrowserPageFragment.newInstance(tabs[position].id)
    }

    fun submitList(newTabs: List<ITab>) {
        val diffResult = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = tabs.size
                override fun getNewListSize() = newTabs.size

                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val oldTab = tabs[oldPos]
                    val newTab = newTabs[newPos]
                    return oldTab.uniqueId == newTab.uniqueId
                }

                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val oldTab = tabs[oldPos]
                    val newTab = newTabs[newPos]
                    return oldTab.currentLocation == newTab.currentLocation
                }
            }
        )
        tabs = newTabs
        diffResult.dispatchUpdatesTo(this)
    }
}

class BrowserFragment : Fragment(R.layout.fragment_browser), ITabListener, DIGlobalAware {
    private val viewModel: BrowserViewModel by viewModels()
    private val viewRefs = ViewRefs()
    private lateinit var viewPager: () -> ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewRefs.setRoot(view)
        viewPager = viewRefs.bind(R.id.viewPager)
        viewPager().isUserInputEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.initialization.join()
            viewPager().adapter = BrowserPageAdapter(this@BrowserFragment, viewModel.tabs.value ?: emptyList())
            setupListeners()
            setupObservers()
        }
    }

    override fun onDestroyView() {
        viewPager().adapter = null
        viewRefs.clear()
        super.onDestroyView()
    }

    override fun onTabSelected(position: Int) {
        viewLifecycleOwner.lifecycleScope.launch { viewModel.select(position) }
    }

    override fun onTabClosed(position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentSize = viewModel.tabs.value?.size ?: return@launch
            viewModel.close(position)

            if (currentSize == 1) {
                viewModel.reset()
            } else {
                val nextPosition = if (position == 0) 0 else position - 1
                viewModel.select(nextPosition)
            }
        }
    }

    override fun onNewTab() {
        val currentSize = viewModel.tabs.value?.size ?: 0
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.new()
            viewPager().adapter?.notifyItemInserted(currentSize + 1)
        }
    }

    override fun onClosedAll() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.reset()
        }
    }

    private fun setupListeners() {
        parentFragmentManager.setFragmentResultListener(
            SettingsFragment.RESULT_SETTINGS,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(SettingsFragment.ARG_CLEAR_CACHE)) {
                onClosedAll()
            }
        }

        val clipboard = requireContext().applicationContext.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addListener()
            .onEach { viewModel.hasClipboardContent.postValue(true) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupObservers() {
        viewModel.tabs.observe(viewLifecycleOwner) {
            (viewPager().adapter as BrowserPageAdapter).submitList(it)
        }
        viewModel.currentTab.observe(viewLifecycleOwner) {
            viewModel.tabs.value?.indexOf(it)?.let {
                viewPager().setCurrentItem(it, false)
            }
        }
    }
}
