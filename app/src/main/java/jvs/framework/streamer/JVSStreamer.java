package jvs.framework.streamer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.pedro.rtplibrary.rtsp.RtspCamera2;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;

import jvs.framework.R;

/**
 * Java Video Streaming Framework Streamer implementation as Singleton.
 */

public class JVSStreamer implements ConnectCheckerRtsp {

    /* backend service encoding type */
    private enum EncType {
        MPEG_DASH_PASSTHROUGH,
        MPEG_DASH,
        WEBM_DASH_VP8,
        WEBM_DASH_VP9
    }

    /* backend service rtsp mode */
    private enum RTSPMode {
        SERVER,
        CLIENT
    }

    /* general */
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final String SERVER_PORT = "8081";
    private static final int DEFAULT_ENC_TYPE = EncType.MPEG_DASH.ordinal();
    private static final int DEFAULT_MODE = RTSPMode.SERVER.ordinal();
    private static final String TAG = "JVSStreamer";

    /* video */
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final int DEFAULT_FPS = 25;
    private static final int DEFAULT_BITRATE = 2000 * 1024;

    /* audio */
    private static final int DEFAULT_ABITRATE = 64 * 1024;
    private static final int DEFAULT_SAMPLERATE = 16000;
    private static final boolean DEFAULT_STEREO = true;

    /* extra arguments - use -180° rotation with Vuzix M300 => .put("-vf").put("transpose=2,transpose=2") */
    private static final JSONArray DEFAULT_ADVOPT = new JSONArray();

    @SuppressLint("StaticFieldLeak")
    private static JVSStreamer instance = null;

    /* keep track of the streamID in case it's needed to send a
    kill request to the jvs service, only if the mode is set to SERVER */
    private static int streamID = -1;
    /* flag to tell app to ignore connection errors if the app sent a stop request to the jvs server */
    private static boolean ignoreConnectionErrors = false;
    /* save the current status of the stream */
    private boolean isStarted = false;

    private Context context;
    private Activity activity;
    private RtspCamera2 rtspCamera2;

    /**
     *  Retrieves the static instance of the class.
     */
    public static JVSStreamer getInstance() {
        if (instance == null) {
            instance = new JVSStreamer();
        }
        return instance;
    }

    /**
     * Sets the context.
     * @param context The context
     */
    public void setContext(final Context context) {
        this.context = context;
    }

    /**
     * Sets the activity.
     * @param activity The activity
     */
    public void setActivity(final Activity activity) { this.activity = activity; }

    /**
     * Starts the streamer.
     */
    public void start() {

        if (context == null) {
            throw new IllegalStateException("Called start() method before setting the context.");
        }

        rtspCamera2 = new RtspCamera2(context, this);

        if (!rtspCamera2.isStreaming()) {
            if (rtspCamera2.prepareAudio(DEFAULT_ABITRATE, DEFAULT_SAMPLERATE, DEFAULT_STEREO, true, true) &&
                    rtspCamera2.prepareVideo(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, DEFAULT_BITRATE, true, 180 )) {
                sendRequestJVSServer();
                //rtspCamera2.startStream("rtsp://" + SERVER_ADDRESS + ":5540/listen/live");
                isStarted = true;
            } else {
                Toast.makeText(context, "Error preparing stream, This device can't do it.", Toast.LENGTH_SHORT).show();
            }
        } else {
            rtspCamera2.stopStream();
        }
    }

    /**
     * Stops the streamer.
     */
    public void stop() {
        if (isStarted) {
            try {
                if (DEFAULT_MODE == 0){
                    sendKillRequestJVSServer();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Defines whether the streamer is running or not.
     * @return True, if the stream is already began; otherwise false.
     */
    public boolean isRunning() {
        return this.isStarted;
    }

    /**
     * Sends a POST HTTP request to the JVS service
     */
    @SuppressLint("SetTextI18n")
    private synchronized void sendRequestJVSServer() {
        Log.d(TAG, "Sending add request to JVS server...");

        Date todayDate = Calendar.getInstance().getTime();

        JSONObject o = null;
        try {
            JSONObject info = null;

            //stream infos only if webm encoding selected
            if (DEFAULT_ENC_TYPE != 0 && DEFAULT_MODE == 0) {
                info = new JSONObject("{" +
                        "\"streams\": [" +
                "{" +
                    "\"index\": 0," +
                    "\"codec_name\": \"aac\"," +
                    "\"codec_type\": \"audio\"," +
                    "\"sample_rate\": \"" + DEFAULT_SAMPLERATE + "\"," +
                    "\"channels\": " + (DEFAULT_STEREO ? 2 : 1) + "," +
                    "\"bits_per_raw_sample\": 16" +
                "}," +
                "{" +
                    "\"index\": 1," +
                    "\"codec_name\": \"avc\"," +
                    "\"codec_type\": \"video\"," +
                    "\"width\": " + DEFAULT_WIDTH + "," +
                    "\"height\": " + DEFAULT_HEIGHT + "," +
                    "\"pix_fmt\": \"yuv420p\"," +
                    "\"r_frame_rate\": \"" + DEFAULT_FPS + "000\"" +
                "}" +
                "]" +
            "}");
            }

            o = new JSONObject()
                    .put("url", "rtsp://" + Utilities.getLocalIpAddress(true))
                    .put("encType", DEFAULT_ENC_TYPE)
                    .put("title", "Dummy stream #" + DEFAULT_ENC_TYPE + "-" + todayDate.getSeconds())
                    .put("descr", "stream ")
                    .put("mode", DEFAULT_MODE)
                    .put("customArgs", DEFAULT_ADVOPT);

            if (DEFAULT_ENC_TYPE != 0 && DEFAULT_MODE == 0) {
                o.put("infos", info);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, "http://" + SERVER_ADDRESS + ":" + SERVER_PORT + "/streams", o, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    if (response.getInt("status") == 0) {
                        Log.d(TAG, "Server setup completed, start announcing stream...");

                        int listeningPort = response.getInt("listPort");
                        String listeningPath = response.getString("annPath");
                        streamID = response.getInt("id");
                        Log.d(TAG, "Process started at: " + new Date().toString());
                        rtspCamera2.startStream("rtsp://" + SERVER_ADDRESS + ":" + listeningPort + "/" + listeningPath);
                        isStarted = true;

                        Log.d(TAG, "Succeeded.");
                        Toast.makeText(context, "JVS Server conversion started!", Toast.LENGTH_SHORT).show();
                    } else {
                        Utilities.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (activity != null) {
                                    TextView txtView = activity.findViewById(R.id.statusText);
                                    txtView.setText(R.string.status_server_error);
                                    Button btn = activity.findViewById(R.id.actionButton);
                                    btn.setText(R.string.start_button);
                                }
                            }
                        });
                        Log.d(TAG, "Failed: " + response.get("message"));
                        Toast.makeText(context, "JVS Server conversion failed!" + response.get("message"), Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Utilities.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity != null) {
                            TextView txtView = activity.findViewById(R.id.statusText);
                            txtView.setText(R.string.status_network_error);
                            Button btn = activity.findViewById(R.id.actionButton);
                            btn.setText(R.string.start_button);
                        }
                    }
                });
                Log.d(TAG, "Failed: " + error.getLocalizedMessage());
                Toast.makeText(context, "JVS Server is unavailable!", Toast.LENGTH_SHORT).show();
            }
        });
        Log.d(TAG, "Adding request to queue!");
        req.setRetryPolicy(new DefaultRetryPolicy(0, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(req);
    }

    /**
     * Sends a PATCH HTTP request to the JVS service to stop rtsp server if the selected mode is SERVER
     */
    @SuppressLint("SetTextI18n")
    private synchronized void sendKillRequestJVSServer() {
        Log.d(TAG, "Sending kill request to JVS server...");

        if (streamID > 0) {
            ignoreConnectionErrors = true;

            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest req = new StringRequest(Request.Method.PATCH, "http://" +
                    SERVER_ADDRESS + ":" + SERVER_PORT + "/streams/" + streamID, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, "Server killed rtsp server successfully.");

                    streamID = -1;
                    ignoreConnectionErrors = isStarted = false;
                }
            }, new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, "Failed: " + error.getLocalizedMessage());
                    Toast.makeText(context, "JVS Server is unavailable!", Toast.LENGTH_SHORT).show();

                    streamID = -1;
                    ignoreConnectionErrors = isStarted = false;
                }
            });
            Log.d(TAG, "Adding request to queue!");
            req.setRetryPolicy(new DefaultRetryPolicy(0, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            queue.add(req);
        }
    }

    @Override
    public void onConnectionSuccessRtsp() {
        Utilities.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "RTSP connection established!", Toast.LENGTH_SHORT).show();
            }
        });
        Log.d(TAG, "onConnectionSuccessRtsp");
    }

    @Override
    public void onConnectionFailedRtsp(String s) {
        Log.d(TAG, "onConnectionFailedRtsp");
        isStarted = false;

        try {
            rtspCamera2.stopStream();
            rtspCamera2.stopRecord();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Utilities.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!ignoreConnectionErrors) {
                    Toast.makeText(context, "RTSP connection error!", Toast.LENGTH_SHORT).show();
                }
                Utilities.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity != null) {
                            TextView txtView = activity.findViewById(R.id.statusText);
                            txtView.setText(R.string.status_server_error);
                            Button btn = activity.findViewById(R.id.actionButton);
                            btn.setText(R.string.start_button);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onDisconnectRtsp() {

        Log.d(TAG, "onDisconnectRtsp");
        isStarted = false;

        try {
            rtspCamera2.stopRecord();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Utilities.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Utilities.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity != null) {
                            TextView txtView = activity.findViewById(R.id.statusText);
                            txtView.setText(R.string.status_server_error);
                            Button btn = activity.findViewById(R.id.actionButton);
                            btn.setText(R.string.start_button);
                        }
                    }
                });
                Toast.makeText(context, "RTSP disconnected!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAuthErrorRtsp() {
        //Toast.makeText(context, "RTSP authorization failed!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onAuthErrorRtsp");
        isStarted = false;
    }

    @Override
    public void onAuthSuccessRtsp() {
        //Toast.makeText(context, "RTSP authorization succeeded!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onAuthSuccessRtsp");
    }
}
