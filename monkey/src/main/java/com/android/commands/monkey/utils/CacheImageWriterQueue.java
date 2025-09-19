/*
 * Copyright 2020 Advanced Software Technologies Lab at ETH Zurich, Switzerland
 *
 * Modified - Copyright (c) 2020 Bytedance Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.commands.monkey.utils;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;


public class CacheImageWriterQueue extends ImageWriterQueue {

    @Override
    public void run() {
    }

    @Override
    public synchronized void add(Bitmap map, File dst) {
        requestQueue.add(new Req(map, dst));
        Logger.println("Adding requestQueue");
        while (requestQueue.size() > Config.flushImagesThreshold) {
            Logger.println("Removing front img of the queue");
            requestQueue.removeFirst();
        }
    }

    @Override
    public synchronized void flush() {
        Logger.println("[CacheImageWriterQueue] Flushing " + requestQueue.size() + " images");
        for (Req req: requestQueue){
            writePNG(req);
        }
    }

    @Override
    protected void writePNG(Req req) {
        Bitmap map = req.map;
        File dst = req.dst;
        if (map == null) {
            Logger.println(dst.getAbsolutePath());
            Logger.format("No screen shot for %s", dst.getAbsolutePath());
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(dst)) {
            map.compress(Bitmap.CompressFormat.PNG, 75, fos);
        } catch (IOException e) {
            e.printStackTrace();
            Logger.format("Fail to save screen shot to %s", dst.getAbsolutePath());
        } finally {
            map.recycle();
        }
    }

    public boolean isFull(){
        return requestQueue.size() >= Config.flushImagesThreshold;
    }

    public void clear() {
        requestQueue.clear();
    }

    @Override
    public synchronized void tearDown() {}

}



