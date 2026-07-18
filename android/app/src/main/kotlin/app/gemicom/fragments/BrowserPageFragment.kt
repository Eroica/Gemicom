package app.gemicom.fragments

import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import app.gemicom.*
import app.gemicom.models.*
import app.gemicom.platform.*
import app.gemicom.ui.FluentInterpolator
import app.gemicom.views.GeminiView
import app.gemicom.views.IViewInteraction
import app.gemicom.views.TabsButton
import app.gemicom.views.lists.IGemtextClickListener
import app.gemicom.views.models.BrowserPageViewModel
import app.gemicom.views.models.BrowserViewModel
import coil.ImageLoader
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*
import org.kodein.di.conf.DIGlobalAware
import org.kodein.di.instance
import kotlin.time.Duration.Companion.seconds

class BrowserPageFragment : Fragment(R.layout.fragment_browser_page),
    TabsButton.IClickTabs,
    IGemtextClickListener,
    IInputListener,
    ISecurityDialogListener,
    IImagePool,
    DIGlobalAware {
        companion object {
            const val ARG_TAB_ID = "TAB_ID"

            fun newInstance(tabId: Long): BrowserPageFragment {
                val fragment = BrowserPageFragment()
                fragment.arguments = Bundle().apply {
                    putLong(ARG_TAB_ID, tabId)
                }
                return fragment
            }
        }

    private val AppSettings: AppSettings by instance()
    private val Certificates: ICertificates by instance()
    private val Dispatcher: CoroutineDispatcher by instance()

    private val viewModel: BrowserPageViewModel by viewModels()
    private val browserViewModel: BrowserViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val viewRefs = ViewRefs()
    private lateinit var tabsButton: () -> TabsButton
    private lateinit var addressBar: () -> EditText
    private lateinit var clearButton: () -> ImageView
    private lateinit var geminiView: () -> GeminiView
    private lateinit var progressBar: () -> ProgressBar
    private lateinit var bottomBarHeader: () -> ViewGroup
    private lateinit var toolbar: () -> MaterialToolbar
    private lateinit var homeButton: () -> Button
    private lateinit var pasteButton: () -> Button

    private lateinit var co: CoroutineScope

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            co.launch { viewModel.back() }
        }
    }

    private var navigation: INavigation? = null

    private val geminiClient = GeminiClient(Certificates)
    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(requireContext())
            .components {
                add(GeminiImageFetcher.Factory(geminiClient, this@BrowserPageFragment))
                add(GeminiImageKeyer())
            }
            .build()
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is InvalidGeminiUri, is InvalidHostError, is InvalidGeminiResponse -> {
                geminiView().show(InvalidGeminiDocument)
                Toast.makeText(
                    context,
                    getString(R.string.browser_error_invalid_url),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is TooManyRedirects -> {
                geminiView().show(InvalidGeminiDocument)
                Toast.makeText(
                    context,
                    getString(R.string.browser_error_invalid_url),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is NoResponseError -> geminiView().show(EmptyGeminiDocument)

            is RequestRefusedError -> {
                geminiView().show(InvalidGeminiDocument)
                Toast.makeText(
                    context,
                    getString(R.string.browser_error_refused),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is InvalidDocument -> geminiView().show(InvalidGeminiDocument)

            is NoMoreHistory -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (backPressedCallback.isEnabled) {
                        backPressedCallback.isEnabled = false
                        Toast.makeText(context, getString(R.string.browser_exit_press), Toast.LENGTH_SHORT).show()
                        delay(1.seconds)
                        backPressedCallback.isEnabled = true
                    }
                }
            }

            is InputRequired -> if (childFragmentManager.findFragmentByTag(InputDialogFragment.TAG) == null) {
                InputDialogFragment(throwable.currentUri, throwable.meta)
                    .show(childFragmentManager, InputDialogFragment.TAG)
            }

            is SensitiveInputRequired -> if (childFragmentManager.findFragmentByTag(InputDialogFragment.TAG) == null) {
                InputDialogFragment(throwable.currentUri, throwable.meta, true)
                    .show(childFragmentManager, InputDialogFragment.TAG)
            }

            is CertificateMismatchError -> {
                geminiView().show(SecurityIssueGeminiDocument)
                SecurityIssueDialogFragment(throwable.host, throwable.newHash)
                    .show(childFragmentManager, "SecurityIssue")
            }

            is CertificateDateError -> geminiView().show(CertificateInvalidDocument)

            else -> geminiView().show(InvalidGeminiDocument)
        }
    }

    private val addressBarTransition = ChangeBounds().apply {
        duration = 200
        interpolator = FluentInterpolator
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigation = context as INavigation
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewRefs.setRoot(view)
        addressBar = viewRefs.bind(R.id.addressBar)
        clearButton = viewRefs.bind(R.id.addressBarClearButton)
        geminiView = viewRefs.bind(R.id.geminiView)
        tabsButton = viewRefs.bind(R.id.tabsButton)
        progressBar = viewRefs.bind(R.id.progressBar)
        bottomBarHeader = viewRefs.bind(R.id.bottomBarHeader)
        toolbar = viewRefs.bind(R.id.bottomBar)
        homeButton = viewRefs.bind(R.id.bottomHomeButton)
        pasteButton = viewRefs.bind(R.id.bottomPasteButton)

        setupBackpress()
        setupListeners()
        setupActionListeners()
        setupAddressBarTransition()

        co = viewLifecycleOwner.lifecycleScope + exceptionHandler
        co.launch {
            if (!viewModel.isInitialized) {
                viewModel.load(browserViewModel.currentTab.value!!)
            }
            setupObservers()
            withContext(Dispatchers.Main) {
                setupMenu()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        backPressedCallback.isEnabled = true
    }

    override fun onPause() {
        backPressedCallback.isEnabled = false
        super.onPause()
    }

    override fun onDestroyView() {
        unfocusAddressBar()
        clearButton().setOnClickListener(null)
        addressBar().setOnEditorActionListener(null)
        addressBar().onFocusChangeListener = null
        tabsButton().listener = null
        geminiView().listener = null
        geminiView().scrollListener = null
        pasteButton().setOnClickListener(null)
        viewRefs.clear()
        super.onDestroyView()
    }

    override fun onDetach() {
        navigation = null
        super.onDetach()
    }

    override fun onTabsClicked() {
        TabsDialogFragment().show(parentFragmentManager, "Tabs")
    }

    override fun onAnchorClicked(anchor: Anchor) {
        if (viewModel.isLoading.value) {
            return
        }
        co.launch { onNavigate(anchor.url, pushToHistory = true, isCheckCache = false) }
    }

    override fun onImageClicked(image: Image, imageView: ImageView) {
        try {
            imageView.loadOrToast(
                GeminiUri.fromAddress(viewModel.tab.resolve(image.url)), imageLoader,
                requireContext(),
                getString(R.string.browser_load_image_error)
            )
        } catch (_: InvalidGeminiUri) {
            imageView.load(image.url)
        }
    }

    override fun onInput(input: String) {
        co.launch { viewModel.input(input) }
    }

    override fun onContinue(host: String, hash: String) {
        co.launch {
            viewModel.updateCertificate(host, hash)
            onNavigate(viewModel.tab.currentLocation, pushToHistory = false, isCheckCache = false)
        }
    }

    override fun getCache(): SqliteCache {
        return viewModel.tab.cache
    }

    private fun setupMenu() {
        toolbar().inflateMenu(R.menu.main_bottom_app_bar)
        toolbar().menu.setGroupDividerEnabled(true)
        setupMenuListeners(toolbar().menu)
        toolbar().setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.browser_back -> co.launch { viewModel.back() }
                R.id.browser_forward -> co.launch { viewModel.forward() }
                R.id.browser_refresh -> co.launch {
                    onNavigate(viewModel.tab.currentLocation, pushToHistory = false, isCheckCache = false)
                }

                R.id.about -> navigation?.onAboutClick()
                R.id.settings -> navigation?.onSettingsClick()
                else -> return@setOnMenuItemClickListener false
            }

            true
        }
    }

    private fun setupMenuListeners(menu: Menu) {
        val backItem = menu.findItem(R.id.browser_back)
        val forwardItem = menu.findItem(R.id.browser_forward)
        val refreshItem = menu.findItem(R.id.browser_refresh)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentUrl.collect {
                    backItem.isEnabled = viewModel.tab.canGoBack()
                    forwardItem.isEnabled = viewModel.tab.canGoForward()
                    refreshItem.isEnabled = viewModel.tab.status != TabStatus.BLANK
                }
            }
        }
    }

    private fun setupBackpress() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun setupListeners() {
        clearButton().setOnClickListener {
            addressBar().setText("")
            focusAddressBar()
        }
        tabsButton().listener = this@BrowserPageFragment
        geminiView().listener = this@BrowserPageFragment
        geminiView().scrollListener = IViewInteraction { unfocusAddressBar() }
        homeButton().setOnClickListener {
            co.launch {
                val homeCapsule = withContext(Dispatcher) { AppSettings.home }
                if (homeCapsule.isBlank()) {
                    Toast.makeText(context, getString(R.string.browser_toast_set_home), Toast.LENGTH_SHORT).show()
                } else {
                    onNavigate(homeCapsule)
                }
            }
        }

        pasteButton().setOnClickListener {
            val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            co.launch { onNavigate(clipboard.content()) }
        }
    }

    private fun setupActionListeners() {
        addressBar().setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                unfocusAddressBar()
                onStartNavigation()
                return@OnEditorActionListener true
            }
            false
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.document.collect {
                        geminiView().show(it)
                    }
                }
                launch {
                    browserViewModel.tabs.collect {
                        tabsButton().setCount(it.size)
                    }
                }
                launch {
                    browserViewModel.hasClipboardContent.collect {
                        pasteButton().isEnabled = it
                    }
                }
                launch {
                    viewModel.currentUrl.collect {
                        addressBar().setText(it)
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        progressBar().visibility = if (isLoading) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setupAddressBarTransition() {
        addressBar().setOnFocusChangeListener { _, hasFocus ->
            TransitionManager.beginDelayedTransition(view as ViewGroup, addressBarTransition)
            bottomBarHeader().visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
    }

    private fun unfocusAddressBar() {
        if (addressBar().isFocused) {
            addressBar().clearFocus()
            (requireContext().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(addressBar().windowToken, 0)
        }
    }

    private fun focusAddressBar() {
        addressBar().requestFocus()
        (requireContext().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(addressBar(), InputMethodManager.SHOW_IMPLICIT)
    }

    private fun onStartNavigation() {
        addressBar().setText(addressBar().text.trim())
        co.launch { viewModel.start(addressBar().text.toString()) }
    }

    private suspend fun onNavigate(
        address: String, pushToHistory: Boolean = true, isCheckCache: Boolean = true
    ) {
        viewModel.navigate(address, pushToHistory, isCheckCache)
    }
}
