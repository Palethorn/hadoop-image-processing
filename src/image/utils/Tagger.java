package image.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author david
 */
public class Tagger {
    public static void main(String[] args) throws IOException, FileNotFoundException {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        File result;
        File image_dir = new File(args[1]);
        File result_dir = new File(args[0]);
        File img_output_dir = new File(args[2]);
        for(String s : result_dir.list()) {
            result = new File(result_dir.getAbsolutePath() + "/" + s);
            BufferedReader br = null;
            String line;
            br = new BufferedReader(new FileReader(result));
            
            while((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                JSONArray a = (JSONArray)JSONValue.parse(parts[1]);
                Mat m = Imgcodecs.imread(parts[0]);
                for(int i = 0; i < a.size(); i++) {
                    long x,y,w,h;
                    JSONObject o = (JSONObject) a.get(i);
                    x = (long) o.get("x");
                    y = (long) o.get("y");
                    w = (long) o.get("width");
                    h = (long) o.get("height");
                    System.out.println(String.format("%s %d %d %d %d", parts[0], x, y, w, h));
                    Rect r = new Rect((int)x, (int)y, (int)w, (int)h);
                    Imgproc.rectangle(m, new Point(r.x, r.y), new Point(r.width, r.height), new Scalar(255,0,0), 2);
                    Imgcodecs.imwrite(img_output_dir + "/" + new String(Base64.getEncoder().encode(parts[0].getBytes())) + ".jpg", m);
                }
            }
        }
    }
}
