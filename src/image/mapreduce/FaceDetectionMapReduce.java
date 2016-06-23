package image.mapreduce;

import java.io.DataInput;
import java.io.DataOutput;
import org.hipi.image.FloatImage;
import org.hipi.image.HipiImageHeader;
import org.hipi.imagebundle.mapreduce.HibInputFormat;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import java.io.IOException;
import java.net.URI;
import org.apache.hadoop.io.Writable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

public class FaceDetectionMapReduce extends Configured implements Tool {
    
    public static class RectWritable implements Writable {

        public int x;
        public int y;
        public int width;
        public int height;

        public RectWritable(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public RectWritable() {
            this(0, 0, 0, 0);
        }

        @Override
        public void write(DataOutput out) throws IOException {
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(width);
            out.writeInt(height);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            x = in.readInt();
            y = in.readInt();
            width = in.readInt();
            height = in.readInt();
        }

        @Override
        public String toString() {
            return Integer.toString(x) + ", "
                    + Integer.toString(y) + ", "
                    + Integer.toString(width) + ", "
                    + Integer.toString(height);
        }
        
        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            o.put("x", x);
            o.put("y", y);
            o.put("width", width);
            o.put("height", height);
            return o;
        }
    }

    public static class FaceDetectionMapper extends Mapper<HipiImageHeader, FloatImage, Text, RectWritable> {

        public Mat convertFloatImageToOpenCVMat(FloatImage floatImage) {

            // Get dimensions of image
            int w = floatImage.getWidth();
            int h = floatImage.getHeight();

            // Get pointer to image data
            float[] valData = floatImage.getData();

            // Initialize 3 element array to hold RGB pixel average
            double[] rgb = {0.0, 0.0, 0.0};

            Mat mat = new Mat(h, w, CvType.CV_8UC3);

            // Traverse image pixel data in raster-scan order and update running average
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    rgb[0] = (double) valData[(j * w + i) * 3 + 0] * 255.0; // R
                    rgb[1] = (double) valData[(j * w + i) * 3 + 1] * 255.0; // G
                    rgb[2] = (double) valData[(j * w + i) * 3 + 2] * 255.0; // B
                    mat.put(j, i, rgb);
                }
            }

            return mat;
        }

        @Override
        public void map(HipiImageHeader key, FloatImage value, Context context)
                throws IOException, InterruptedException {
            System.load("/home/hduser/hadoop-image-processing/lib/jni/libopencv_java310.so");
            Mat m = this.convertFloatImageToOpenCVMat(value);
            MatOfRect found = new MatOfRect();
            CascadeClassifier faceDetector = new CascadeClassifier("haarcascade.xml");
            faceDetector.detectMultiScale(m, found);
            Rect[] rects = found.toArray();
            RectWritable rectWritable;
            for (Rect rect : rects) {
                rectWritable = new RectWritable(rect.x, rect.y, rect.width, rect.height);
                System.out.println(rectWritable.toString());
                context.write(new Text(key.getMetaData("source")), rectWritable);
            }
        }
    }

    public static class FaceDetectionReducer extends Reducer<Text, FaceDetectionMapReduce.RectWritable, Text, Text> {

        @Override
        public void reduce(Text key, Iterable<FaceDetectionMapReduce.RectWritable> values, Context context)
                throws IOException, InterruptedException {
            JSONArray a = new JSONArray();
            for(RectWritable rect: values) {
                a.add(rect.toJson());
            }
            context.write(key, new Text(a.toJSONString()));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        // Check input arguments
        if (args.length < 2) {
            System.out.println("Usage: helloWorld <input HIB> <output directory>");
            System.exit(0);
        }

        // Initialize and configure MapReduce job
        Job job = Job.getInstance();
        job.getConfiguration().set("mapred.child.java.opts", "-Djava.library.path=/home/hduser/hadoop-image-processing/lib/jni");
        job.addCacheFile(new URI("hdfs://localhost:9000/user/hduser/libopencv_java310.so#libopencv_java310.so"));
        job.addCacheFile(new URI("hdfs://localhost:9000/data/haarcascade_frontalface_default.xml#haarcascade.xml"));
        // Set input format class which parses the input HIB and spawns map tasks
        job.setInputFormatClass(HibInputFormat.class);
        // Set the driver, mapper, and reducer classes which express the computation
        job.setJarByClass(FaceDetectionMapReduce.class);
        job.setMapperClass(FaceDetectionMapReduce.FaceDetectionMapper.class);
        job.setReducerClass(FaceDetectionMapReduce.FaceDetectionReducer.class);
        // Ovdje promijeniti output tipove u npr.  KeyPointWriteable, HipiImageHeader itd., ovisi kakvog su tipa rezultati
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(FaceDetectionMapReduce.RectWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // Set the input and output paths on the HDFS
        FileInputFormat.setInputPaths(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // Execute the MapReduce job and block until it complets
        boolean success = job.waitForCompletion(true);
        // Return success or failure
        return success ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new FaceDetectionMapReduce(), args);
        System.exit(0);
    }
}
