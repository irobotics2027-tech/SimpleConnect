package com.irobotics.btchat

import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(
    val fromMe: Boolean,
    val text: String? = null,
    val image: Bitmap? = null,
    val isSystem: Boolean = false
)

class MessageAdapter(
    private val items: MutableList<ChatMessage>,
    private val onImageClick: (Bitmap) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val row: LinearLayout = v.findViewById(R.id.row)
        val bubble: LinearLayout = v.findViewById(R.id.bubble)
        val text: TextView = v.findViewById(R.id.msgText)
        val image: ImageView = v.findViewById(R.id.msgImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        when {
            m.isSystem -> {
                h.row.gravity = Gravity.CENTER_HORIZONTAL
                h.bubble.background = null
                h.text.setTextColor(Color.GRAY)
                h.text.textSize = 13f
            }
            m.fromMe -> {
                h.row.gravity = Gravity.END
                h.bubble.setBackgroundResource(R.drawable.bubble_me)
                h.text.setTextColor(Color.WHITE)
                h.text.textSize = 16f
            }
            else -> {
                h.row.gravity = Gravity.START
                h.bubble.setBackgroundResource(R.drawable.bubble_them)
                h.text.setTextColor(Color.BLACK)
                h.text.textSize = 16f
            }
        }

        h.text.visibility = if (m.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        h.text.text = m.text

        if (m.image != null) {
            h.image.visibility = View.VISIBLE
            h.image.setImageBitmap(m.image)
            h.image.setOnClickListener { onImageClick(m.image) }
        } else {
            h.image.visibility = View.GONE
            h.image.setImageDrawable(null)
            h.image.setOnClickListener(null)
        }
    }

    fun add(m: ChatMessage) {
        items.add(m)
        notifyItemInserted(items.size - 1)
    }
}
