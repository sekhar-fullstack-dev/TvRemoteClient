package com.example.tvremoteclient.services;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD;

public class AndroidWebServer extends NanoHTTPD {
    private final Context context;

    public AndroidWebServer(int port, Context context) {
        super("0.0.0.0",port);
        this.context = context;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if ("/getVideos".equals(uri)) {
            String response = getVideoListJson();
            return newFixedLengthResponse(Response.Status.OK, "application/json", response);
        }
        else if (uri.startsWith("/getThumbnail/")) {
            String videoId = uri.substring("/getThumbnail/".length());
            return getThumbnailResponse(videoId);
        }
        else if (uri.contains("/streamVideo/")) {
            String videoId = uri.substring("/streamVideo/".length());
            Map<String, String> headers = session.getHeaders();
            return serveVideoFile(videoId, headers);
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }
    private Response getThumbnailResponse(String videoId) {
        long vidId = Long.parseLong(videoId);  // Convert string ID to long
        Bitmap thumbnail = MediaStore.Video.Thumbnails.getThumbnail(
                context.getContentResolver(),
                vidId,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null);

        if (thumbnail != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            thumbnail.compress(Bitmap.CompressFormat.PNG, 0, bos);  // Compress to PNG, lossless
            byte[] bitmapData = bos.toByteArray();
            ByteArrayInputStream bis = new ByteArrayInputStream(bitmapData);

            return newChunkedResponse(Response.Status.OK, "image/png", bis);
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Thumbnail not found");
        }
    }
    @SuppressLint("Range")
    private String getVideoListJson() {
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
        };

        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        // JSON array to hold video details
        JSONArray videoArray = new JSONArray();

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, // URI for video media
                projection, // Projection array
                null, // Selection clause
                null, // Selection arguments
                sortOrder  // Sort order
        )) {
            while (cursor != null && cursor.moveToNext()) {
                JSONObject videoObject = new JSONObject();
                videoObject.put("id", cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media._ID)));
                videoObject.put("name", cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)));
                videoObject.put("duration", cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.DURATION)));
                videoObject.put("size", cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.SIZE)));
                videoObject.put("dateAdded", cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)));

                videoArray.put(videoObject); // Add video details to the array
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return videoArray.toString(); // Convert JSON array to string and return
    }
   /* private Response serveVideoFile(String videoId, Map<String, String> headers) {
        try {
            File videoFile = new File(Objects.requireNonNull(getVideoFilePath(Long.parseLong(videoId))));
            if (!videoFile.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Video not found");
            }

            String range = headers.get("range");
            long fileLength = videoFile.length();
            String mimeType = "video/mp4";
            Response response;

            if (range != null && range.startsWith("bytes=")) {
                String[] ranges = range.substring("bytes=".length()).split("-");
                long startFrom = Long.parseLong(ranges[0]);
                long endAt = ranges.length > 1 ? Long.parseLong(ranges[1]) : fileLength - 1;

                if (startFrom > fileLength - 1 || startFrom < 0) {
                    return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                }

                FileInputStream fileInputStream = new FileInputStream(videoFile);
                fileInputStream.skip(startFrom);

                response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fileInputStream, endAt - startFrom + 1);
                response.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLength);
                response.addHeader("Content-Length", String.valueOf(endAt - startFrom + 1));
            } else {
                FileInputStream fileInputStream = new FileInputStream(videoFile);
                response = newChunkedResponse(Response.Status.OK, mimeType, fileInputStream);
                response.addHeader("Content-Length", String.valueOf(fileLength));
            }

            return response;
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal error");
        }
    }*/
   @SuppressLint("Range")
   private Response serveVideoFile(String videoId, Map<String, String> headers) {
       try {
           InputStream inputStream;
           long fileLength;
           String mimeType = "video/*";

           if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               Uri videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Long.parseLong(videoId));
               inputStream = context.getContentResolver().openInputStream(videoUri);
               //mimeType = context.getContentResolver().getType(videoUri);
               Cursor cursor = context.getContentResolver().query(videoUri, new String[]{MediaStore.Video.Media.SIZE}, null, null, null);
               if (cursor != null && cursor.moveToFirst()) {
                   fileLength = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.SIZE));
                   cursor.close();
               } else {
                   return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Video not found");
               }
           } else {
               File videoFile = new File(getVideoFilePath(Long.parseLong(videoId)));
               if (!videoFile.exists()) {
                   return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Video not found");
               }
               fileLength = videoFile.length();
               inputStream = new FileInputStream(videoFile);
               //mimeType = URLConnection.guessContentTypeFromName(videoFile.getName());
           }

//           if (mimeType == null) {
//               mimeType = "video/mp4"; // Default to video/mp4 if MIME type is not found
//           }

           // Handle range requests
           String range = headers.get("range");
           Response response;
           if (range != null && range.startsWith("bytes=")) {
               String[] ranges = range.substring("bytes=".length()).split("-");
               long startFrom = Long.parseLong(ranges[0]);
               long endAt = ranges.length > 1 ? Long.parseLong(ranges[1]) : fileLength - 1;

               if (startFrom > fileLength - 1 || startFrom < 0 || endAt > fileLength - 1) {
                   return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "Range Not Satisfiable");
               }

               // Skip to the start of the range in the input stream
               inputStream.skip(startFrom);

               // Create a partial response with the specified byte range
               long contentLength = endAt - startFrom + 1;
               response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, contentLength);
               response.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLength);
               response.addHeader("Content-Length", String.valueOf(contentLength));
               response.addHeader("Accept-Ranges", "bytes");
           } else {
               // Send the complete file if no range is specified
               response = newChunkedResponse(Response.Status.OK, mimeType, inputStream);
               response.addHeader("Content-Length", String.valueOf(fileLength));
               response.addHeader("Accept-Ranges", "bytes");
           }
           return response;
       } catch (Exception e) {
           e.printStackTrace();
           return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal server error: " + e.getMessage());
       }
   }

    private String getVideoFilePath(long videoId) {
        Context context = this.context;  // Assuming there's a context field in your class
        Uri videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above, use content URI directly
            return videoUri.toString();
        } else {
            // For older versions, continue using the DATA column
            String[] projection = { MediaStore.Video.Media.DATA };
            try (Cursor cursor = context.getContentResolver().query(videoUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            }
        }
        return null; // Return null or throw an exception if the file path is not found
    }

}

