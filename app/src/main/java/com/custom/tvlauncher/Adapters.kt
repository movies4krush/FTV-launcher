package com.custom.tvlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class RowsAdapter(
    private val rows: List<CustomRow>,
    private val pm: PackageManager,
    private val onLaunch: (String) -> Unit
) : RecyclerView.Adapter<RowsAdapter.RowVH>() {

    class RowVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvRowTitle)
        val rvItems: RecyclerView = view.findViewById(R.id.rvRowItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        return RowVH(v)
    }

    override fun onBindViewHolder(holder: RowVH, position: Int) {
        val row = rows[position]
        holder.tvTitle.text = row.title
        holder.rvItems.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvItems.adapter = if (row.isChannelRow) {
            ChannelItemsAdapter(row.channelItems, onLaunch)
        } else {
            AppCardsAdapter(row.packages, pm, onLaunch)
        }
    }

    override fun getItemCount() = rows.size
}

class AppCardsAdapter(
    private val packages: List<String>,
    private val pm: PackageManager,
    private val onLaunch: (String) -> Unit
) : RecyclerView.Adapter<AppCardsAdapter.CardVH>() {

    class CardVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivBanner: ImageView = view.findViewById(R.id.ivBanner)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        return CardVH(v)
    }

    override fun onBindViewHolder(holder: CardVH, position: Int) {
        val pkg = packages[position]
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            holder.tvName.text = pm.getApplicationLabel(info)
            holder.ivBanner.setImageDrawable(pm.getApplicationBanner(pkg) ?: pm.getApplicationIcon(pkg))
        } catch (_: Exception) {
            holder.tvName.text = pkg
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(s).scaleY(s).setDuration(140).start()
        }
        holder.itemView.setOnClickListener { onLaunch(pkg) }
    }

    override fun getItemCount() = packages.size
}

class ChannelItemsAdapter(
    private val items: List<ChannelCard>,
    private val onLaunch: (String) -> Unit
) : RecyclerView.Adapter<ChannelItemsAdapter.ChanVH>() {

    class ChanVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivBanner: ImageView = view.findViewById(R.id.ivBanner)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChanVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        return ChanVH(v)
    }

    override fun onBindViewHolder(holder: ChanVH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.title
        if (!item.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context).load(item.imageUrl).into(holder.ivBanner)
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(s).scaleY(s).setDuration(140).start()
        }
        holder.itemView.setOnClickListener {
            try {
                if (!item.intentUri.isNullOrEmpty()) {
                    holder.itemView.context.startActivity(Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME))
                } else {
                    onLaunch(item.packageName)
                }
            } catch (_: Exception) {
                onLaunch(item.packageName)
            }
        }
    }

    override fun getItemCount() = items.size
}

class DockAdapter(
    private val packages: List<String>,
    private val pm: PackageManager,
    private val onLaunch: (String) -> Unit
) : RecyclerView.Adapter<DockAdapter.DockVH>() {

    class DockVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivDockIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false)
        return DockVH(v)
    }

    override fun onBindViewHolder(holder: DockVH, position: Int) {
        val pkg = packages[position]
        try {
            holder.ivIcon.setImageDrawable(pm.getApplicationIcon(pkg))
        } catch (_: Exception) {}
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.15f else 1.0f
            v.animate().scaleX(s).scaleY(s).setDuration(140).start()
        }
        holder.itemView.setOnClickListener { onLaunch(pkg) }
    }

    override fun getItemCount() = packages.size
}
