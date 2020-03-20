package com.savr.demoheremap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.here.android.mpa.common.*
import com.here.android.mpa.common.PositioningManager.*
import com.here.android.mpa.mapping.AndroidXMapFragment
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapState
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity(), Map.OnTransformListener {

    private var map: Map? = null
    private var mapFragment: AndroidXMapFragment? = null
    private var mPositioningManager: PositioningManager? = null

    private var mTransforming = true
    private var mPendingUpdate: Runnable? = null
    private var currentPosition: GeoCoordinate? = null

    private var permissionID = 101
    var permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

    }

    private fun checkPermissions(): Boolean {
        var result: Int
        val listPermissionsNeeded: MutableList<String> = ArrayList()
        for (p in permissions) {
            result = ContextCompat.checkSelfPermission(applicationContext, p)
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p)
            }
        }
        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray(),
                permissionID
            )
            return false
        }
        initMap()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == permissionID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initMap()
            } else {
                var perStr = ""
                for (per in permissions) {
                    perStr += per.trimIndent()
                }
                checkPermissions()
            }
        }
    }

    private fun initMap() {
        mapFragment = (supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment?)

        val diskCacheRoot =
            this.filesDir.path + File.separator + ".isolated-here-maps"

        var intentName = ""
        try {
            val ai = packageManager.getApplicationInfo(this.packageName, PackageManager.GET_META_DATA)
            val bundle = ai.metaData
            intentName = bundle.getString("INTENT_NAME").toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("Main", "Failed to find intent name, NameNotFound: " + e.message)
        }

        val success = MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot, intentName)

        if (!success) {
            Toast.makeText(
                applicationContext,
                "Unable to set isolated disk cache path.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            mapFragment?.init { error ->
                if (error == OnEngineInitListener.Error.NONE) {
                    map = mapFragment?.map
                    map?.setCenter(GeoCoordinate(-6.175392, 106.827156, 0.0), Map.Animation.NONE)
                    map?.zoomLevel = (map?.maxZoomLevel?.plus(map?.minZoomLevel!!))?.div(2)!!
                    map?.projectionMode = Map.Projection.MERCATOR
                    map!!.addTransformListener(this@MainActivity)

                    mPositioningManager = getInstance()

                    getInstance().addListener(
                        WeakReference(positionListener)
                    )

                    // start position updates, accepting GPS, network or indoor positions
                    if (mPositioningManager?.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)!!) {
                        mapFragment!!.positionIndicator.isVisible = true
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "PositioningManager.start: failed, exiting",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                } else AlertDialog.Builder(this@MainActivity)
                    .setMessage("Error : " + error.name + "\n\n" + error.details)
                    .setTitle(error.details)
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        finish()
                    }.create()
                    .show()
            }
        }
    }

    override fun onMapTransformStart() {
        mTransforming = true
    }

    override fun onMapTransformEnd(p0: MapState?) {
        mTransforming = false
        if (mPendingUpdate != null) {
            mPendingUpdate!!.run()
            mPendingUpdate = null
        }
    }

    private val positionListener: OnPositionChangedListener = object : OnPositionChangedListener{
        override fun onPositionFixChanged(p0: LocationMethod?, p1: LocationStatus?) {

        }

        override fun onPositionUpdated(locationMethod: LocationMethod,
                                       geoPosition: GeoPosition,
                                       mapMatched: Boolean) {

            currentPosition = geoPosition.coordinate

            map?.setCenter(
                geoPosition.coordinate,
                Map.Animation.BOW
            )
        }
    }
}
