package com.android.commands.monkey.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.android.commands.monkey.fastbot.client.Operate;
import com.android.commands.monkey.source.CoverageData;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

// import static com.android.commands.monkey.utils.Config.takeScreenshotForEveryStep;

import org.json.JSONException;
import org.json.JSONObject;


public class ProxyServer extends NanoHTTPD {

    private final OkHttpClient client;
    private final ScriptDriverClient scriptDriverClient;
    private final static Gson gson = new Gson();
    private boolean useCache = false;
    private String hierarchyResponseCache;
    public boolean takeScreenshots = false;
    public String logStamp;
    public int stepsCount = 0;

    private File outputDir;
    private ImageWriterQueue mImageWriter;


    public boolean monkeyIsOver;
    public List<String> blockWidgets;

    public List<String> blockTrees;

    private CoverageData mCoverageData;
    private int mVerbose = 1;

    private HashSet<String> u2ExtMethods = new HashSet<>(
            Arrays.asList("click", "setText", "swipe", "drag", "setOrientation", "pressKey")
    );
    private Operate mOperate;


    public boolean shouldUseCache() {
        return this.useCache;
    }

    public String getHierarchyResponseCache() {
        return this.hierarchyResponseCache;
    }

    public ProxyServer(int port, ScriptDriverClient scriptDriverClient) {
        super(port);
        this.client = OkHttpClient.getInstance();
        this.scriptDriverClient = scriptDriverClient;

        // start the image writer thread
        mImageWriter = new ImageWriterQueue();
        Thread imageThread = new Thread(mImageWriter);
        imageThread.start();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String uri = session.getUri();

        Logger.println("get method:" + method);
        Logger.println("uri:" + uri);

        if (session.getMethod() == Method.GET && uri.equals("/getStat")) {
            return getCoverageStatistics();
        }

        if (session.getMethod() == Method.GET && uri.equals("/stopMonkey"))
        {
            monkeyIsOver = true;
            MonkeySemaphore.stepMonkey.release();
            return newFixedLengthResponse(
                Response.Status.OK,
                "text/plain",
                "Monkey Stopped"
            );
        }

        // parse the request body data
        Map<String, String> data = new HashMap<>();
        String requestBody = "";


        // parse the request body
        if (session.getMethod() == Method.POST ||
                session.getMethod() == Method.PUT ||
                session.getMethod() == Method.DELETE
        ) {
            try {
                session.parseBody(data);
                requestBody = data.get("postData");
                if (requestBody == null) {
                    requestBody = "";
                }
                if (mVerbose > 3){
                    Logger.println(requestBody);
                }
            } catch (IOException | ResponseException e) {
                return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "text/plain",
                        "Error when parsing post data: " + e.getMessage());
            }
        }

        if (uri.equals("/init") && session.getMethod() == Method.POST){
            InitRequest req = new Gson().fromJson(requestBody, InitRequest.class);
            takeScreenshots = req.isTakeScreenshots();
            logStamp = req.getLogStamp();
            outputDir = new File(
                    Environment.getExternalStorageDirectory(), "output_" + logStamp
            );
            Logger.println("Init: ");
            Logger.println("    takeScreenshots: " + takeScreenshots);
            Logger.println("    logStamp: " + logStamp);
            Logger.println("    outputDir: " + outputDir);
            return newFixedLengthResponse(
                    Response.Status.OK,
                    "text/plain",
                    "outputDir:" + outputDir
            );
        }

        if (uri.equals("/stepMonkey") && session.getMethod() == Method.POST)
        {
            StepMonkeyRequest req = new Gson().fromJson(requestBody, StepMonkeyRequest.class);
            stepsCount = req.getStepsCount();
            Logger.println("[ProxyServer] stepsCount: " + stepsCount);
            return stepMonkey(req.getBlockWidgets(), req.getBlockTrees());
        }

        Logger.println("[Proxy Server] Forwarding");
        Response res = forward(uri, method, requestBody);
        if (!uri.equals("/ping"))
        {
            if (takeScreenshots && u2ExtMethods.contains(method)) {
                // save Screenshot while forwarding the request
                Logger.println("[Proxy Server] Detected script method: " + method +  ", saving screenshot.");
                okhttp3.Response screenshotResponse = scriptDriverClient.takeScreenshot();
                String screenshot_file = saveScreenshot(screenshotResponse);
                if (screenshot_file != null){
                    recordLog(requestBody, screenshot_file);
                }
            }
        }
        return res;
    }

    public void setCoverageStatistics(CoverageData coverageData){
        mCoverageData = coverageData;
    }

    private Response getCoverageStatistics() {
        try {
            String data = gson.toJson(mCoverageData);
            return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    data
            );
        } catch (Exception e) {
            Logger.println("Error generating coverage statistics: " + e.getMessage());
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    "{\"error\": \"Failed to get coverage statistics\"}"
            );
        }
    }

    private Response stepMonkey(List<String> blockWidgets, List<String> blockTrees){
        this.blockWidgets = blockWidgets;
        this.blockTrees = blockTrees;
        Logger.println("[ProxyServer] receive request: stepMonkey");
        if (!blockWidgets.isEmpty()){
            Logger.println("              blockWidgets: " + blockWidgets);
        }
        if (!blockTrees.isEmpty()){
            Logger.println("              blockTrees: " + blockTrees);
        }
        MonkeySemaphore.stepMonkey.release();
        if (mVerbose > 3) {
            Logger.println("[ProxyServer] release semaphore: stepMonkey");
        }
        try {
            MonkeySemaphore.doneMonkey.acquire();
            if (mVerbose > 3){
                Logger.println("[ProxyServer] acquired semaphore: doneMonkey");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            Logger.println("[ProxyServer] Finish monkey step. Dumping hierarchy");
            okhttp3.Response hierarchyResponse = scriptDriverClient.dumpHierarchy();

            if (takeScreenshots){
                Logger.println("[ProxyServer] Taking Screenshot");
                okhttp3.Response screenshotResponse = scriptDriverClient.takeScreenshot();
                String screenshot_file = saveScreenshot(screenshotResponse);
                if (screenshot_file != null){
                    recordLog(mOperate, screenshot_file);
                }
            }

            this.useCache = true;
            return generateServerResponse(hierarchyResponse, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean recordLog(Operate op, String screenshot_file){
        JSONObject obj = new JSONObject();
        try {
            obj.put("Type", "Monkey");
            obj.put("Info", op.toJson());
            obj.put("Screenshot", screenshot_file);
            saveLog(obj);
        } catch (JSONException e){
            Logger.errorPrintln("Error when recording log.");
            return false;
        }
        return true;
    }

    private boolean recordLog(String U2ReqBody, String screenshot_file){
        JSONObject obj = new JSONObject();
        try {
            obj.put("Type", "Script");
            obj.put("Info", U2ReqBody);
            obj.put("Screenshot", screenshot_file);
            saveLog(obj);
        } catch (JSONException e){
            Logger.errorPrintln("Error when recording log.");
            return false;
        }
        return true;
    }


    private void saveLog(JSONObject obj){
        String logFile = String.valueOf(new File(outputDir, "steps.log"));
        try {
            StoneUtils.writeStringToFile(logFile, obj.toString(), true);
        } catch (IOException e){
            Logger.errorPrintln("Error when saving log: " + logFile);
        }
    }

    /**
     * Generate proxy response from the ui automation server, which finally respond to PC
     * @param okhttpResponse the okhttp3.response from ui automation server
     * @return The NanoHttpD response
     * @throws IOException .
     */
    private Response generateServerResponse(okhttp3.Response okhttpResponse) throws IOException{
        if (okhttpResponse != null && okhttpResponse.body() != null) {
            String body = okhttpResponse.body().string();

            Response.Status status = Response.Status.lookup(okhttpResponse.code());
            if (status == null) {
                status = Response.Status.OK;
            }

            String contentType = okhttpResponse.header("Content-Type", "application/json");
            return newFixedLengthResponse(status, contentType, body);
        } else {
            return newFixedLengthResponse(
                    Response.Status.NO_CONTENT,
                    "text/plain",
                    ""
            );
        }
    }

    /**
     * Save the screenshot to /sdcard/screenshots
     * @param screenshotResponse The okhttp3.Response from ui automation server
     * @return the screenshot file_name
     */
    private String saveScreenshot(okhttp3.Response screenshotResponse) {
        if (mVerbose > 3){
            Logger.println("[ProxyServer] Parsing bitmap with base64.");
        }
        String res;
        try {
            res = screenshotResponse.body().string();
        }
        catch (IOException e) {
            Logger.errorPrintln("[ProxyServer] [Error]");
            return null;
        }

        JsonRPCResponse res_obj = gson.fromJson(res, JsonRPCResponse.class);
        String base64Data = res_obj.getResult();

        // Logger.println("[ProxyServer] Got base64Data: " + base64Data);
        // Decode the response with android.util.Base64
        byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

        Logger.println("[ProxyServer] base64 Decoded");
        // Parse the bytes into bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        String screenshot_file = getScreenshotName();

        if (bitmap == null){
            Logger.println("[ProxyServer][Error] Failed to parse screenshot response to bitmap");
            return null;
        } else {
            // Ensure screenshots dir
            File screenshotDir = new File(outputDir, "screenshots");
            Logger.println("Screenshots will be saved to: " + screenshotDir);
            if (!screenshotDir.exists()) {
                boolean created = screenshotDir.mkdirs();
                if (!created) {
                    Logger.println("[ProxyServer][Error] Failed to create screenshots directory");
                    return null;
                }
            }

            // create the screenshot file
            File screenshotFile = new File(
                screenshotDir,
                screenshot_file
            );
            Logger.println("[ProxyServer] Adding the screenshot to ImageWriter");
            mImageWriter.add(bitmap, screenshotFile);
            return screenshot_file;
        }
    }

    private String getScreenshotName(){
        SimpleDateFormat sdf = new SimpleDateFormat("HHmmssSSS", Locale.ENGLISH);
        String currentDateTime = sdf.format(new Date());
        return String.format(Locale.ENGLISH, "screenshot-%d-%s.png", stepsCount, currentDateTime);
    }

    /**
     * Generate proxy response from the ui automation server, which finally respond to PC.
     * Meanwhile, cache the hierarchy to accelerate the stepMonkey request
     * @param okhttpResponse the okhttp3.response from ui automation server
     * @param setHierarchyCache cache the hierarchy when doing stepMonkey
     * @return The NanoHttpD response
     * @throws IOException .
     */
    private Response generateServerResponse(okhttp3.Response okhttpResponse, boolean setHierarchyCache) throws IOException{
        // read the response from the server and generate response
        if (okhttpResponse != null && okhttpResponse.body() != null) {
            String body = okhttpResponse.body().string();
            if (setHierarchyCache) this.hierarchyResponseCache = body;
            // check the response code
            Response.Status status = Response.Status.lookup(okhttpResponse.code());
            if (status == null) {
                status = Response.Status.OK;
            }

            String contentType = okhttpResponse.header("Content-Type", "application/json");
            return newFixedLengthResponse(status, contentType, body);
        } else {
            return newFixedLengthResponse(
                    Response.Status.NO_CONTENT,
                    "text/plain",
                    "");
        }
    }

    private Response forward(String uri, String method, String requestBody){
        String targetUrl = client.get_url_builder()
                .addPathSegments(uri.startsWith("/") ? uri.substring(1) : uri)
                .build()
                .toString();

        try {
            okhttp3.Response forwardedResponse;

            if ("GET".equalsIgnoreCase(method)) {
                forwardedResponse = client.get(targetUrl);
            } else if ("POST".equalsIgnoreCase(method)) {
                forwardedResponse = client.post(targetUrl, requestBody);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                forwardedResponse = client.delete(targetUrl, requestBody);
            } else {
                return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "text/plain",
                        "Unsupport method: " + method);
            }

            return generateServerResponse(forwardedResponse);
        } catch (IOException ex) {
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "请求转发错误: " + ex.getMessage());
        }
        finally {
            this.useCache = false;
        }
    }

    public void tearDown(){
        mImageWriter.tearDown();
    }

    public void setVerbose(int verbose) {
        this.mVerbose = verbose;
    }

    public void setMonkeyOperate(Operate operate) {
        mOperate = operate;
    }
}