package com.cookiejarapps.smartcookieweb_ytdl.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.smartcookieweb_ytdl.R
import com.cookiejarapps.smartcookieweb_ytdl.database.Download
import kotlinx.android.synthetic.main.download_row.view.*


class DownloadsAdapter : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    private var downloadList: List<Download> = emptyList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    fun updateDataSet(items: List<Download>) {
        downloadList = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.download_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.itemView) {
            download_title.text = downloadList[position].name
            download_progress.progress = downloadList[position].downloadPercent.toInt()
            percentage.text = downloadList[position].downloadPercent.toString() + "%"
            when(downloadList[position].fileType) {
                "audio" -> icon.setImageResource(R.drawable.ic_audio)
                "video" ->icon.setImageResource(R.drawable.ic_video)
                else ->icon.setImageResource(R.drawable.ic_download)
            }
            setOnClickListener {
                openFile(downloadList[position].downloadPath, it.context)
            }
        }
    }

    override fun getItemCount() = downloadList.size

    private fun openFile(filePath: String, context: Context) {
        val intent = Intent(Intent.ACTION_VIEW)
        val fileURI = Uri.parse(filePath)
        val mimeType = context.contentResolver.getType(fileURI) ?: "*/*"
        intent.setDataAndType(fileURI, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(context.packageManager) != null) {
            startActivity(context, intent, null)
        } else {
            Toast.makeText(context, R.string.app_not_found, Toast.LENGTH_SHORT).show()
        }
    }
}