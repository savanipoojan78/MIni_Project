/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yummywakame.breakroom;

import android.text.TextUtils;
import android.util.Log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods related to requesting and receiving article data from The Guardian.
 */
public final class NewsQueryUtils {

    /** Tag for the log messages */
    private static final String LOG_TAG = NewsQueryUtils.class.getSimpleName() + " - LOG";

    /**
     * Create a private constructor because no one should ever create a {@link NewsQueryUtils} object.
     * This class is only meant to hold static variables and methods, which can be accessed
     * directly from the class name NewsQueryUtils (and an object instance of NewsQueryUtils is not needed).
     */
    private NewsQueryUtils() {
    }

    /**
     * Query the Guardian dataset and return a list of {@link NewsArticle} objects.
     */
    public static List<NewsArticle> fetchArticleData(String requestUrl) {
        // Create URL object
        URL url = createUrl(requestUrl);

        // Perform HTTP request to the URL and receive a JSON response back
        String jsonResponse = null;
        try {
            jsonResponse = makeHttpRequest(url);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Problem making the HTTP request.", e);
        }

        // Extract relevant fields from the JSON response and create a list of {@link NewsArticle}s
        List<NewsArticle> newsArticles = extractFeatureFromJson(jsonResponse);

        // Return the list of {@link NewsArticle}s
        return newsArticles;
    }

    /**
     * Returns new URL object from the given string URL.
     */
    private static URL createUrl(String stringUrl) {
        URL url = null;
        try {
            url = new URL(stringUrl);
        } catch (MalformedURLException e) {
        }
        return url;
    }

    /**
     * Make an HTTP request to the given URL and return a String as the response.
     */
    private static String makeHttpRequest(URL url) throws IOException {
        String jsonResponse = "";

        // If the URL is null, then return early.
        if (url == null) {
            return jsonResponse;
        }

        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(10000 /* milliseconds */);
            urlConnection.setConnectTimeout(15000 /* milliseconds */);
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // If the request was successful (response code 200),
            // then read the input stream and parse the response.
            if (urlConnection.getResponseCode() == 200) {
                inputStream = urlConnection.getInputStream();
                jsonResponse = readFromStream(inputStream);
            } else {
                Log.e(LOG_TAG, "Error response code: " + urlConnection.getResponseCode());
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Problem retrieving the article JSON results.", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (inputStream != null) {
                // Closing the input stream could throw an IOException, which is why
                // the makeHttpRequest(URL url) method signature specifies than an IOException
                // could be thrown.
                Log.v(LOG_TAG, "inputStream != null. closing input stream.");
                inputStream.close();
            }
        }
        return jsonResponse;
    }

    /**
     * Convert the {@link InputStream} into a String which contains the
     * whole JSON response from the server.
     */
    private static String readFromStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line = reader.readLine();
            while (line != null) {
                output.append(line);
                line = reader.readLine();
            }
        }
        return output.toString();
    }

    /**
     * Return a list of {@link NewsArticle} objects that has been built up from
     * parsing the given JSON response.
     */
    private static List<NewsArticle> extractFeatureFromJson(String articleJSON) {
        // If the JSON string is empty or null, then return early.
        if (TextUtils.isEmpty(articleJSON)) {
            Log.v(LOG_TAG, "The JSON string is empty or null. Returning early.");
            return null;
        }

        // Create an empty ArrayList that we can start adding newsArticles to
        List<NewsArticle> newsArticles = new ArrayList<>();

        // Try to parse the JSON response string. If there's a problem with the way the JSON
        // is formatted, a JSONException exception object will be thrown.
        // Catch the exception so the app doesn't crash, and print the error message to the logs.
        try {

            // Create a JSONObject from the JSON response string
            JSONObject jsonObjectRoot = new JSONObject(articleJSON);

            // Extract the JSONArray associated with the key called "response",
            // which represents a list of features (or newsArticles).
            JSONObject jsonObjectResponse = jsonObjectRoot.getJSONObject("response");

            // Grab the results array from the base object
            JSONArray jsonArrayResults = jsonObjectResponse.getJSONArray("results");

            // For each article in the articleArray, create an {@link NewsArticle} object
            for (int i = 0; i < jsonArrayResults.length(); i++) {
                // Get a single newsArticle at position i within the list of newsArticles
                JSONObject currentArticle = jsonArrayResults.getJSONObject(i);

                String webPublicationDate = currentArticle.getString("webPublicationDate");
                String webTitle = currentArticle.getString("webTitle");
                String webUrl = currentArticle.getString("webUrl");

                JSONObject jsonObjectFields = currentArticle.getJSONObject("fields");

                String webTrailText = jsonObjectFields.optString("trailText");
                String byLine = jsonObjectFields.optString("byline");
                String thumbnail = jsonObjectFields.optString("thumbnail");

                // Add a new NewsArticle from the data
                newsArticles.add(new NewsArticle(webPublicationDate, webTitle, webTrailText, webUrl, byLine, downloadBitmap(thumbnail)));
            }

        } catch (JSONException e) {
            // If an error is thrown when executing any of the above statements in the "try" block,
            // catch the exception here, so the app doesn't crash. Print a log message
            // with the message from the exception.
            Log.e(LOG_TAG, "NewsQueryUtils: Problem parsing the article JSON results", e);
        }

        // Return the list of newsArticles
        return newsArticles;
    }

    /**
     * Load an image from a URL and return a {@link Bitmap}
     *
     * @param url string of the URL link to the image
     * @return Bitmap of the image
     */
    private static Bitmap downloadBitmap(String url) {
        Bitmap bitmap = null;
        try {
            InputStream inputStream = new URL(url).openStream();
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
        return bitmap;
    }

}