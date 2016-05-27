package image.mapreduce;
import org.hipi.image.FloatImage;
import org.hipi.image.HipiImageHeader;
import org.hipi.imagebundle.mapreduce.HibInputFormat;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import java.io.IOException;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.HOGDescriptor;

public class ImageMapReduce extends Configured implements Tool {

  public static class ImageMapper extends Mapper<HipiImageHeader, FloatImage, IntWritable, FloatImage> {
      public Mat convertFloatImageToOpenCVMat(FloatImage floatImage) {

       // Get dimensions of image
       int w = floatImage.getWidth();
       int h = floatImage.getHeight();


       // Get pointer to image data
       float[] valData = floatImage.getData();


       // Initialize 3 element array to hold RGB pixel average
       double[] rgb = {0.0,0.0,0.0};


       Mat mat = new Mat(h, w, CvType.CV_8UC3);


       // Traverse image pixel data in raster-scan order and update running average
       for (int j = 0; j < h; j++) {
           for (int i = 0; i < w; i++) {
               rgb[0] = (double) valData[(j*w+i)*3+0] * 255.0; // R
               rgb[1] = (double) valData[(j*w+i)*3+1] * 255.0; // G
               rgb[2] = (double) valData[(j*w+i)*3+2] * 255.0; // B
               mat.put(j, i, rgb);
           }
       }


       return mat;
   }
      
    public void map(HipiImageHeader key, FloatImage value, Context context)
      throws IOException, InterruptedException {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            Mat m = this.convertFloatImageToOpenCVMat(value);
            MatOfRect found = new MatOfRect();
            MatOfDouble weights = new MatOfDouble();
            HOGDescriptor hog = new HOGDescriptor();
            hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector());
            hog.detectMultiScale(m, found, weights, 0, new Size(8, 8), new Size(32, 32), 1.05, 2, false);
            Rect [] rects = found.toArray();
            for(int i = 0; i < rects.length; i++) {
                System.out.println(String.format("%s Found %d %d %d %d", key.getMetaData("source"), rects[i].x, rects[i].y, rects[i].width, rects[i].height));
            }

        }
  }

  public static class ImageReducer extends Reducer<IntWritable, FloatImage, IntWritable, Text> {
    public void reduce(IntWritable key, Iterable<FloatImage> values, Context context)
      throws IOException, InterruptedException {
    }
  }

  public int run(String[] args) throws Exception {
    // Check input arguments
    if (args.length < 2) {
      System.out.println("Usage: helloWorld <input HIB> <output directory>");
      System.exit(0);
    }

    // Initialize and configure MapReduce job
    Job job = Job.getInstance();
    job.getConfiguration().set( "mapred.child.java.opts" , "-Djava.library.path=/home/david/source/opencv/build/lib");
    // Set input format class which parses the input HIB and spawns map tasks
    job.setInputFormatClass(HibInputFormat.class);
    // Set the driver, mapper, and reducer classes which express the computation
    job.setJarByClass(ImageMapReduce.class);
    job.setMapperClass(ImageMapper.class);
    job.setReducerClass(ImageReducer.class);
    // Ovdje promijeniti output tipove u npr.  KeyPointWriteable, HipiImageHeader itd., ovisi kakvog su tipa rezultati
    job.setMapOutputKeyClass(IntWritable.class);
    job.setMapOutputValueClass(FloatImage.class);
    job.setOutputKeyClass(IntWritable.class);
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
    ToolRunner.run(new ImageMapReduce(), args);
    System.exit(0);
  }
}