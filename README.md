# Java Video Streaming Framework

Vert.x and ffmpeg based java framework to enable webcasting capabilities for wearable and eyewear devices.

## Components

### Service

RESTful and reactive service to convert RTSP input stream into a flexible adaptive bitrate streaming in MPEG-DASH\WEBM-DASH

### Consumer Client

Web app to view, manage and play available streams via [shaka-player](https://opensource.google.com/projects/shaka-player)

### Producer Client (example)

Android app to record and stream both audio and video data via RTSP from the device and microphone camera (tested on Vuzix M300 and Honor 5X with Marshmallow 6.1 OS).
Uses JVSStreamer class to handle all communication with the service, that calls [pedroSG94's library](https://github.com/pedroSG94/rtmp-rtsp-stream-client-java) to record, encode and stream the content.

## Deployment

To configure the backend service it's sufficient to modify the config.json file in which it's possible to declare ffmpeg advanced arguments for each conversion type, the REST API routes, the binding address (Optional) and the ports used for API access and for ffmpeg RTSP listening. While to setup the Android application, at least if the provided test app is used, it's needed to declare the remote server address and port using the "SERVER_ADDRESS" and "SERVER_PORT" constants inside the JVSStreamer class. As for the web app, once again, the only setup needed is the declaration of the service remote address using the "server_address" variable inside the script.js file under "Server/assets" directory.

``` sh
java -jar jvs.service.jar
```
