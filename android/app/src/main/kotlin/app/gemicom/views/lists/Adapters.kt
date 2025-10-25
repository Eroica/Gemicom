package app.gemicom.views.lists

import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import app.gemicom.InvalidGeminiUri
import app.gemicom.R
import app.gemicom.fragments.ITabsDialog
import app.gemicom.models.*
import app.gemicom.platform.ClickableAnchor
import app.gemicom.platform.GeminiUri
import app.gemicom.platform.NonGeminiAnchor
import app.gemicom.platform.bulletedText
import app.gemicom.ui.toDp
import java.time.format.DateTimeFormatter

interface IGemtextClickListener {
    fun onAnchorClicked(anchor: Anchor)
    fun onImageClicked(image: Image, imageView: ImageView)
}

class BindingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

class GemtextDiffCallback : DiffUtil.ItemCallback<IGemtext>() {
    override fun areItemsTheSame(oldItem: IGemtext, newItem: IGemtext) = oldItem == newItem
    override fun areContentsTheSame(oldItem: IGemtext, newItem: IGemtext) = oldItem.content == newItem.content
}

class GeminiAdapter(
    private val listener: IGemtextClickListener,
    private val appSettings: AppSettings
) : ListAdapter<IGemtext, BindingViewHolder>(GemtextDiffCallback()) {
    private val isShowImagesInline = appSettings.isShowImagesInline

    private val spans = object : Spannable.Factory() {
        override fun newSpannable(source: CharSequence?): Spannable {
            return source as Spannable
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        val holder = BindingViewHolder(view)

        if (viewType == R.layout.gemtext_anchor_block) {
            view.findViewById<TextView?>(R.id.gemtext)
                ?.setSpannableFactory(spans)
        }

        return holder
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.itemView.findViewById<TextView?>(R.id.gemtextAnchor)?.let {
            it.movementMethod = null
        }

        when (val item = getItem(position)) {
            is Text -> bindText(holder.itemView, item)
            is H1 -> bindH1(holder.itemView, item)
            is H2 -> bindH2(holder.itemView, item)
            is H3 -> bindH3(holder.itemView, item)
            is ListItem -> {}
            is ListItemBlock -> bindUl(holder.itemView, item)
            is Anchor -> {}
            is AnchorBlock -> bindAnchorBlock(holder.itemView, item)
            is Blockquote -> bindBlockquote(holder.itemView, item)
            is Image -> bindImage(holder.itemView, item)
            Newline -> {}
            is Preformat -> {}
            is PreformatBlock -> bindPre(holder.itemView, item)
            EmptyPageBlock -> {}
            InvalidDocumentBlock -> {}
            SecurityIssueBlock -> {}
            CertificateInvalidBlock -> {}
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is H1 -> R.layout.gemtext_h1
            is H2 -> R.layout.gemtext_h2
            is H3 -> R.layout.gemtext_h3
            is ListItemBlock -> R.layout.gemtext_li_block
            is AnchorBlock -> R.layout.gemtext_anchor_block
            is Blockquote -> R.layout.gemtext_blockquote
            is Image -> R.layout.gemtext_image
            is PreformatBlock -> R.layout.gemtext_pre
            EmptyPageBlock -> R.layout.gemtext_page_empty
            InvalidDocumentBlock -> R.layout.gemtext_page_invalid
            SecurityIssueBlock -> R.layout.gemtext_page_security_issue
            CertificateInvalidBlock -> R.layout.gemtext_page_certificate_error
            else -> R.layout.gemtext_paragraph
        }
    }

    private fun bindText(view: View, token: Text) {
        (view as TextView).text = token.content
    }

    private fun bindH1(view: View, token: H1) {
        (view as TextView).text = token.content
    }

    private fun bindH2(view: View, token: H2) {
        (view as TextView).text = token.content
    }

    private fun bindH3(view: View, token: H3) {
        (view as TextView).text = token.content
    }

    private fun bindUl(view: View, block: ListItemBlock) {
        (view as TextView).apply {
            val htmlSpans = HtmlCompat.fromHtml(
                block.content,
                FROM_HTML_MODE_COMPACT
            )
            setText(
                bulletedText(htmlSpans, 3f.toDp(view.context), 8f.toDp(view.context)).trim(),
                TextView.BufferType.SPANNABLE
            )
        }
    }

    private fun bindAnchorBlock(view: View, block: AnchorBlock) {
        view.findViewById<TextView>(R.id.gemtextAnchor).apply {
            val typedValue = TypedValue()
            view.context.theme.resolveAttribute(
                androidx.appcompat.R.attr.colorPrimary,
                typedValue,
                true
            )

            val builder = SpannableStringBuilder()
            block.anchors.forEach {
                val clickableSpan = try {
                    GeminiUri.fromAddress(it.url)
                    ClickableAnchor(it, typedValue.data, listener)
                } catch (_: InvalidGeminiUri) {
                    NonGeminiAnchor(it.url)
                }
                val span = SpannableString(
                    """${it.content}
"""
                )
                span.setSpan(
                    clickableSpan, 0, it.content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(span)
            }

            setText(builder.trim(), TextView.BufferType.SPANNABLE)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun bindBlockquote(view: View, token: Blockquote) {
        view.findViewById<TextView>(R.id.gemtext).text = token.content
    }

    private fun bindImage(view: View, token: Image) {
        view.findViewById<TextView>(R.id.gemtextImageLabel).text = token.content
        val imageView = view.findViewById<ImageView>(R.id.gemtextImage)

        if (isShowImagesInline) {
            imageView.visibility = VISIBLE
            listener.onImageClicked(token, imageView)
        } else {
            view.setOnClickListener {
                view.findViewById<ImageView>(R.id.gemtextImage).apply {
                    token.isExpanded = !token.isExpanded

                    if (token.isExpanded) {
                        TransitionManager.beginDelayedTransition(view as ViewGroup, AutoTransition())
                        listener.onImageClicked(token, this)
                        visibility = VISIBLE
                    } else {
                        TransitionManager.beginDelayedTransition(view as ViewGroup, AutoTransition())
                        visibility = GONE
                    }
                }
            }


            if (!token.isExpanded) {
                imageView.visibility = GONE
            } else {
                imageView.visibility = VISIBLE
                listener.onImageClicked(token, imageView)
            }
        }
    }

    private fun bindPre(view: View, block: PreformatBlock) {
        view.findViewById<TextView>(R.id.gemtext).apply {
            text = block.content.trim()
        }
    }
}

class TabDiffCallback : DiffUtil.ItemCallback<ITab>() {
    override fun areItemsTheSame(oldItem: ITab, newItem: ITab): Boolean {
        return oldItem.uniqueId == newItem.uniqueId
    }

    override fun areContentsTheSame(oldItem: ITab, newItem: ITab): Boolean {
        return oldItem.currentLocation == newItem.currentLocation
    }
}

class TabsAdapter(
    private val listener: ITabsDialog,
    formatString: String,
) : ListAdapter<ITab, BindingViewHolder>(TabDiffCallback()) {
    private var dateFormatter = DateTimeFormatter.ofPattern(formatString)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        view.isClickable = true
        val backgroundValue = TypedValue()
        view.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, backgroundValue, true)
        view.setBackgroundResource(backgroundValue.resourceId)
        return BindingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.itemView.findViewById<TextView>(android.R.id.text1).apply {
            text = getItem(position).currentLocation.ifBlank { holder.itemView.context.getString(R.string.dialog_tabs_blank_tab) }
        }
        holder.itemView.findViewById<TextView>(android.R.id.text2).apply {
            text = dateFormatter.format(getItem(position).createdAt)
        }
        holder.itemView.setOnClickListener { listener.onTabSelected(holder.bindingAdapterPosition) }
    }
}
