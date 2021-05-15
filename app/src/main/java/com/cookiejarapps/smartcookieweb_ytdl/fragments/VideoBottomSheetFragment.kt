package com.cookiejarapps.smartcookieweb_ytdl.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.work.*
import com.cookiejarapps.smartcookieweb_ytdl.MainActivity
import com.cookiejarapps.smartcookieweb_ytdl.R
import com.cookiejarapps.smartcookieweb_ytdl.adapters.VideoAdapter
import com.cookiejarapps.smartcookieweb_ytdl.adapters.VideoInfoListener
import com.cookiejarapps.smartcookieweb_ytdl.item.VideoInfoItem
import com.cookiejarapps.smartcookieweb_ytdl.models.LoadState
import com.cookiejarapps.smartcookieweb_ytdl.models.VideoInfoViewModel
import com.cookiejarapps.smartcookieweb_ytdl.worker.DownloadWorker
import com.google.android.exoplayer2.MediaItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_video_info.*
import kotlinx.android.synthetic.main.fragment_video_info.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


class VideoBottomSheetFragment : BottomSheetDialogFragment(),
    SAFDialogFragment.DialogListener {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // get the views and attach the listener
        return inflater.inflate(
            R.layout.fragment_video_info, container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = arguments?.getString("url")

        val vidFormatsVm =
            ViewModelProvider(activity as MainActivity).get(VideoInfoViewModel::class.java)
        if (url != null) {
            vidFormatsVm.fetchInfo(url)
        }

        val videoFormatsModel =
            ViewModelProvider(activity as MainActivity).get(VideoInfoViewModel::class.java)
        with(view.video_list) {
            adapter =
                VideoAdapter(VideoInfoListener listener@{
                    videoFormatsModel.selectedItem = it
                    if (!isStoragePermissionGranted()) {
                        return@listener
                    }
                    SAFDialogFragment().show(
                        childFragmentManager,
                        downloadLocationDialogTag
                    )

                })
            layoutManager = GridLayoutManager(context, 4)
        }
        videoFormatsModel.vidFormats.observe(viewLifecycleOwner, { t ->
            (video_list.adapter as VideoAdapter).updateAdapter(t)

            if(t != null){
                videoName.text = t.title
            }
        })
        videoFormatsModel.loadState.observe(viewLifecycleOwner, { t ->
            when (t) {
                LoadState.INITIAL -> {
                    videoThumbnail.visibility = View.GONE
                    videoName.visibility = View.GONE
                    video_list.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                }
                LoadState.LOADING -> {
                    videoThumbnail.visibility = View.GONE
                    videoName.visibility = View.GONE
                    video_list.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                }
                LoadState.LOADED -> {
                    videoThumbnail.visibility = View.VISIBLE
                    videoName.visibility = View.VISIBLE
                    video_list.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                }
                LoadState.ERRORED -> {
                    videoThumbnail.visibility = View.GONE
                    videoName.visibility = View.GONE
                    video_list.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            }
        })
        vidFormatsVm.thumbnail.observe(viewLifecycleOwner, {
            it?.apply {
                val picasso = Picasso.get()
                picasso.load(this)
                    .into(videoThumbnail)
            } ?: videoThumbnail.setImageResource(R.drawable.ic_video)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OPEN_DIRECTORY_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let {
                        activity?.contentResolver?.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        setDownloadLocation(it.toString())
                        val videoFormatsModel =
                            ViewModelProvider(activity as MainActivity).get(VideoInfoViewModel::class.java)
                        startDownload(videoFormatsModel.selectedItem, it.toString())
                    }
                }
            }
        }
    }
    private fun startDownload(vidFormatItem: VideoInfoItem.VideoFormatItem, downloadDir: String) {
        val videoInfo = vidFormatItem.vidInfo
        val videoFormat = vidFormatItem.vidFormat
        val workTag = videoInfo.id
        val workManager = WorkManager.getInstance(activity?.applicationContext!!)
        val state = workManager.getWorkInfosByTag(workTag).get()?.getOrNull(0)?.state
        val running = state === WorkInfo.State.RUNNING || state === WorkInfo.State.ENQUEUED
        if (running) {
            Toast.makeText(
                context,
                R.string.download_already_running,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val workData = workDataOf(
            DownloadWorker.urlKey to videoInfo.webpageUrl,
            DownloadWorker.nameKey to videoInfo.title,
            DownloadWorker.formatIdKey to videoFormat.formatId,
            DownloadWorker.audioCodecKey to videoFormat.acodec,
            DownloadWorker.videoCodecKey to videoFormat.vcodec,
            DownloadWorker.downloadDirKey to downloadDir,
            DownloadWorker.sizeKey to videoFormat.filesize,
            DownloadWorker.videoId to videoInfo.id
        )
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(workTag)
            .setInputData(workData)
            .build()

        workManager.enqueueUniqueWork(
            workTag,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        Toast.makeText(
            context,
            R.string.download_queued,
            Toast.LENGTH_LONG
        ).show()


        val navController = Navigation.findNavController(
            requireActivity(),
            R.id.nav_host_fragment
        )
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        navController.navigate(R.id.downloads_fragment, null, navOptions)

        dismiss()
    }

    private fun setDownloadLocation(path: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.getString(DOWNLOAD_LOCATION, null) ?: prefs.edit()
            .putString(DOWNLOAD_LOCATION, path).apply()
    }

    override fun onAccept(dialog: SAFDialogFragment) {
        val videoFormatsModel =
            ViewModelProvider(activity as MainActivity).get(VideoInfoViewModel::class.java)
        val path = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(DOWNLOAD_LOCATION, null)
        if (path == null) {
            Toast.makeText(context, R.string.invalid_download_location, Toast.LENGTH_SHORT).show()
            return
        }
        startDownload(videoFormatsModel.selectedItem, path)
    }

    override fun onPickFile(dialog: SAFDialogFragment) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE)
    }

    private fun isStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
                false
            }
        } else {
            true
        }
    }

    companion object {
        fun newInstance(url: String): VideoBottomSheetFragment {
            val video = VideoBottomSheetFragment()

            val args = Bundle()
            args.putString("url", url)
            video.arguments = args

            return video
        }

        const val downloadLocationDialogTag = "download_location_chooser_dialog"
        private const val OPEN_DIRECTORY_REQUEST_CODE = 37321
        private const val DOWNLOAD_LOCATION = "download"
    }
}