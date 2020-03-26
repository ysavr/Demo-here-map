package com.savr.demoheremap

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.here.android.mpa.common.ApplicationContext
import com.here.android.mpa.common.MapEngine
import com.here.android.mpa.common.MapSettings
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.odml.MapLoader
import com.here.android.mpa.odml.MapPackage
import kotlinx.android.synthetic.main.activity_area.*
import kotlinx.android.synthetic.main.list_item_area.view.*
import java.io.File
import java.util.ArrayList

class AreaActivity : AppCompatActivity() {

    private val tag = AreaActivity::class.qualifiedName

    private var mapLoader: MapLoader? = null
    private var children: List<MapPackage> = ArrayList()

    private var adapter:MapAreaAdapter?=null
    private var layoutManager: RecyclerView.LayoutManager?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_area)

        initMapEngine()

        initView()
    }

    private fun initView() {
        cancelBtn.setOnClickListener {
            mapLoader?.cancelCurrentOperation()
        }

        mapUpdateBtn.setOnClickListener {
            val success: Boolean = mapLoader!!.checkForMapDataUpdate()
            if (!success) {
                Toast.makeText(
                    this@AreaActivity, "MapLoader is being busy with other operations",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun initMapEngine() {
        val diskCacheRoot =
            this.filesDir.path + File.separator + ".isolated-here-maps"

        var intentName = ""
        try {
            val ai = packageManager.getApplicationInfo(this.packageName, PackageManager.GET_META_DATA)
            val bundle = ai.metaData
            intentName = bundle.getString("INTENT_NAME").toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(tag, "Failed to find intent name, NameNotFound: " + e.message)
        }

        val success = MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot, intentName)
        if (!success) {
            Toast.makeText(applicationContext, "Unable to set isolated disk cache path.", Toast.LENGTH_LONG).show()
        } else {
            MapEngine.getInstance().init(ApplicationContext(applicationContext)) { error ->
                if (error == OnEngineInitListener.Error.NONE) {
                    getMapPackages()
                } else {
                    AlertDialog.Builder(this@AreaActivity).setMessage(
                            "Error : " + error.name + "\n\n" + error.details)
                        .setTitle("Error")
                        .setNegativeButton(
                            android.R.string.cancel
                        ) { dialog, which -> finish() }.create().show()
                }
            }
        }
    }

    private fun getMapPackages() {
        mapLoader = MapLoader.getInstance()
        mapLoader?.addListener(listener)
        mapLoader?.mapPackages
    }

    private val listener: MapLoader.Listener = object : MapLoader.Listener {
        override fun onProgress(i: Int) {
            ext_progressBar.progress = 0
            ext_progressBar.visibility = View.VISIBLE
            ext_progressBar.progress = i
            progressTextView.visibility  = View.VISIBLE
            Log.d("download", "progress: $i%")
            if (i < 100) {
                val progress = "Progress: $i %"
                progressTextView.text = progress
            } else {
                progressTextView.text = "Installing..."
            }
            Log.d(tag, "onProgress()")
        }

        override fun onInstallationSize(l: Long, l1: Long) {}
        override fun onGetMapPackagesComplete(
            mapPackage: MapPackage,
            resultCode: MapLoader.ResultCode
        ) {
            if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                children = mapPackage.children
                refreshListView(ArrayList(children))
            } else if (resultCode == MapLoader.ResultCode.OPERATION_BUSY) { // The map loader is still busy, just try again.
                mapLoader!!.mapPackages
            }
        }

        override fun onCheckForUpdateComplete(
            updateAvailable: Boolean,
            oldVersion: String,
            newVersion: String,
            resultCode: MapLoader.ResultCode
        ) {
            if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                if (updateAvailable) { /* Update the map if there is a new version available */
                    val success = mapLoader!!.performMapDataUpdate()
                    if (!success) {
                        Toast.makeText(
                            this@AreaActivity,
                            "MapLoader is being busy with other operations",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@AreaActivity,
                            "Starting map update from current version:$oldVersion to $newVersion",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@AreaActivity,
                        "Current map version: $oldVersion is the latest",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (resultCode == MapLoader.ResultCode.OPERATION_BUSY) {
                mapLoader!!.checkForMapDataUpdate()
            }
        }

        override fun onPerformMapDataUpdateComplete(
            mapPackage: MapPackage,
            resultCode: MapLoader.ResultCode
        ) {
            if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                Toast.makeText(this@AreaActivity, "Map update is completed", Toast.LENGTH_SHORT).show()
                refreshListView(ArrayList(mapPackage.children))
            }
        }

        override fun onInstallMapPackagesComplete(
            mapPackage: MapPackage?,
            resultCode: MapLoader.ResultCode
        ) {
            progressTextView.text = ""
            ext_progressBar.progress = 0
            progressTextView.visibility = View.VISIBLE
            if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                Toast.makeText(this@AreaActivity, "Installation is completed", Toast.LENGTH_SHORT).show()
                val children = mapPackage?.children
                refreshListView(ArrayList(children!!))
            } else if (resultCode == MapLoader.ResultCode.OPERATION_CANCELLED) {
                Toast.makeText(
                    this@AreaActivity,
                    "Installation is cancelled...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onUninstallMapPackagesComplete(
            mapPackage: MapPackage?,
            resultCode: MapLoader.ResultCode
        ) {
            progressTextView.text = ""
            ext_progressBar.progress = 0
            progressTextView.visibility = View.VISIBLE
            if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                Toast.makeText(this@AreaActivity, "Uninstalling is completed", Toast.LENGTH_SHORT)
                    .show()
                val children = mapPackage?.children
                refreshListView(ArrayList(children!!))
            } else if (resultCode == MapLoader.ResultCode.OPERATION_CANCELLED) {
                Toast.makeText(
                    this@AreaActivity,
                    "Uninstalling is cancelled...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun refreshListView(children: ArrayList<MapPackage>) {
        layoutManager= LinearLayoutManager(applicationContext)
        adapter= MapAreaAdapter(children)

        recycler.layoutManager=layoutManager
        recycler.setHasFixedSize(true)
        recycler.adapter=adapter
        recycler.adapter?.notifyDataSetChanged()
    }

    inner class MapAreaAdapter(private var mapPackageList: ArrayList<MapPackage>) : RecyclerView.Adapter<MapAreaAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_area, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return mapPackageList.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindItems(mapPackageList[position])
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private lateinit var context : Context

            fun bindItems(mapPackage: MapPackage) {
                itemView.mapPackageName.text = mapPackage.title
                itemView.mapPackageSize.text = "${mapPackage.size} KB"
                itemView.mapPackageState.text = mapPackage.installationState.toString()

                val clickedMapPackage: MapPackage = mapPackage
                val children = clickedMapPackage.children

                if (children.size > 0) {
                    itemView.button_more.visibility = View.VISIBLE
                } else itemView.button_more.visibility = View.INVISIBLE

                if (clickedMapPackage.installationState == MapPackage.InstallationState.INSTALLED) {
                    itemView.button_uninstall.text = "Uninstall"
                } else {
                    itemView.button_uninstall.text = "Install"
                }

                context = super.itemView.context

                itemView.button_more.setOnClickListener {

                    if (children.size > 0) {
                        refreshListView(ArrayList(children))
                    }
                }

                itemView.button_uninstall.setOnClickListener {
                    val idList = ArrayList<Int>()
                    idList.add(clickedMapPackage.id)

                    if (clickedMapPackage.installationState == MapPackage.InstallationState.INSTALLED) {
                        val success = mapLoader!!.uninstallMapPackages(idList)
                        if (!success) {
                            Toast.makeText(this@AreaActivity, "MapLoader is being busy with other operations", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AreaActivity, "Uninstalling...", Toast.LENGTH_SHORT).show()
                        }

                    } else {
                        val success = mapLoader!!.installMapPackages(idList)
                        if (!success) {
                            Toast.makeText(this@AreaActivity, "MapLoader is being busy with other operations", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AreaActivity, "Downloading "+clickedMapPackage.title, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
