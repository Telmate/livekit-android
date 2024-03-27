package com.telmate.telmatevvwrapper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.ConnectOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.PeerConnection
import kotlinx.coroutines.launch
import java.util.ArrayList
import java.util.Arrays
import java.util.Iterator
import java.util.List
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.net.HttpURLConnection ;
import java.net.URL ;
import java.io.BufferedReader ;
import java.io.InputStreamReader ;
import java.util.Collections;


class MainActivity : AppCompatActivity() {

    lateinit var room: Room
    var PERMISSION_REQUEST_CODE = 100
    var permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    var jsonObject = JSONObject();
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Create Room object.
        room = LiveKit.create(applicationContext)

        // Setup the video renderer
        room.initVideoRenderer(findViewById<SurfaceViewRenderer>(R.id.renderer))
        Thread({
            //Do some Network Request
            var roomName = "x7yc-pzmx"
            var userName = "inmate"
            jsonObject = getJSONObjectFromURL("https://twilio-replacement-demos-qa-k8s.gtldev.net/api/token?identity="+userName+"&name="+userName+"&roomName="+roomName);            
        }).start()
        requestNeededPermissions {
            Thread.sleep(10_000)
            connectToRoom()
        }

    }

    private fun getJSONObjectFromURL(urlString: String) : JSONObject {
        val url = URL(urlString);
        val urlConnection = url.openConnection() as? HttpURLConnection;
        urlConnection?.setRequestMethod("GET");
        urlConnection?.setReadTimeout(10000 /* milliseconds */ );
        urlConnection?.setConnectTimeout(15000 /* milliseconds */ );
        urlConnection?.setDoOutput(true);
        urlConnection?.connect();

        val br = BufferedReader( InputStreamReader(url.openStream()));
        val sb = StringBuilder();

        var line = br.readLine();
        while (line != null) {
            sb.append(line + "\n");
            line = br.readLine();
        }
        br.close();

        val jsonString = sb.toString();
        System.out.println("JSON: " + jsonString);

        return JSONObject(jsonString);
    }

    private fun connectToRoom() {
        val url = "wss://test-app-p1xt5b19.livekit.cloud"

        var token = ""
        try {
            token = jsonObject.getString("accessToken");
        }
        catch (e: JSONException) {
            System.out.println("JSONException")
        }

        lifecycleScope.launch {
            // Setup event handling.
            launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> onTrackSubscribed(event)
                        else -> {}
                    }
                }
            }

            // Connect to server.
            room.connect(
                url = url,
                token = token,
                options = ConnectOptions(
                    rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                        networkPreference = PeerConnection.AdapterType.VPN
                    },
                ),
            )


            // Turn on audio/video recording.
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(true)
            localParticipant.setCameraEnabled(true)

            // Attach video of remote participant if already available.
            val remoteVideoTrack = room.remoteParticipants.values.firstOrNull()
                ?.getTrackPublication(Track.Source.CAMERA)
                ?.track as? VideoTrack

            if (remoteVideoTrack != null) {
                attachVideo(remoteVideoTrack)
            }
        }
    }

    private fun onTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        val track = event.track
        if (track is VideoTrack) {
            attachVideo(track)
        }
    }

    private fun attachVideo(videoTrack: VideoTrack) {
        videoTrack.addRenderer(findViewById<SurfaceViewRenderer>(R.id.renderer))
        findViewById<View>(R.id.progress).visibility = View.GONE
    }

    private fun requestNeededPermissions(onHasPermissions: () -> Unit) {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                var hasDenied = false
                // Check if any permissions weren't granted.
                for (grant in grants.entries) {
                    if (!grant.value) {
                        Toast.makeText(this, "Missing permission: ${grant.key}", Toast.LENGTH_SHORT).show()

                        hasDenied = true
                    }
                }

                if (!hasDenied) {
                    onHasPermissions()
                }
            }

        // Assemble the needed permissions to request
        val neededPermissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            .filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED }
            .toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions)
        } else {
            onHasPermissions()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        room.disconnect()
    }
}
