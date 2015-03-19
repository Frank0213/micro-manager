package acq;

import coordinates.PositionManager;
import gui.SettingsDialog;
import imagedisplay.DisplayPlus;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition
 */
public abstract class Acquisition implements AcquisitionEventSource{

   //max numberof images that are held in queue to be saved
   private static final int OUTPUT_QUEUE_SIZE = 40;
   //number of acquisiton events held at any given time
   public static final int ACQ_EVENT_QUEUE_SIZE = 40;
   
   protected volatile double zStep_ = 1;
   private BlockingQueue<TaggedImage> engineOutputQueue_;
   protected CMMCore core_ = MMStudio.getInstance().getCore();
   protected String xyStage_, zStage_;
   protected PositionManager posManager_;
   protected BlockingQueue<AcquisitionEvent> events_;
   protected TaggedImageSink imageSink_;
   protected String pixelSizeConfig_;
   protected volatile boolean finished_ = false;
   private String name_;
   private long startTime_ms_ = -1;

   public Acquisition(double zStep ) {
            xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      zStep_ = zStep;
         events_ = new LinkedBlockingQueue<AcquisitionEvent>(ACQ_EVENT_QUEUE_SIZE);
      try {
         pixelSizeConfig_ = MMStudio.getInstance().getCore().getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         ReportingUtils.showError("couldnt get pixel size config");
      }
   }


   public String getXYStageName() {
       return xyStage_;
   }
   
   public String getZStageName() {
       return zStage_;
   }
   
   /**
    * indices are 1 based and positive
    *
    * @param sliceIndex -
    * @param frameIndex -
    * @return
    */
   public abstract double getZCoordinateOfSlice(int displaySliceIndex, int displayFrameIndex);

   public abstract int getDisplaySliceIndexFromZCoordinate(double z, int displayFrameIndex);

   //TODO: change this when number of channels acutally implemented
   public int getNumChannels() {
      return (int) MMStudio.getInstance().getCore().getNumberOfCameraChannels();
   }
   
   public boolean isFinished() {
      return finished_;
   }
   
   @Override
   public AcquisitionEvent getNextEvent() throws InterruptedException {
      return events_.take();
   }
   
   public abstract void abort();
   
   public void markAsFinished() {
      finished_ = true;
   }
   public long getStartTime_ms() {
      return startTime_ms_;
   }
   
   public void setStartTime_ms(long time) {
      startTime_ms_ = time;
   }
   
   protected void initialize(String dir, String name) {
      int xOverlap = SettingsDialog.getOverlapX();
      int yOverlap = SettingsDialog.getOverlapY();
      
      engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>(OUTPUT_QUEUE_SIZE);

      JSONObject summaryMetadata = CustomAcqEngine.makeSummaryMD(this, name);
      MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(dir, true, summaryMetadata, xOverlap, yOverlap, pixelSizeConfig_);
      //storage class has determined unique acq name, so it can now be stored
      name_ = storage.getUniqueAcqName();
      MMImageCache imageCache = new MMImageCache(storage);
      imageCache.setSummaryMetadata(summaryMetadata);
      posManager_ = storage.getPositionManager();      
      new DisplayPlus(imageCache, this, summaryMetadata, storage);         
      imageSink_ = new TaggedImageSink(engineOutputQueue_, imageCache, this);
      imageSink_.start();
   }
   
   public BlockingQueue<TaggedImage> getImageSavingQueue() {
      return engineOutputQueue_;
   }
   
   public String getName() {
      return name_;
   }

   public double getZStep() {
      return zStep_;
   }

   public PositionManager getPositionManager() {
      return posManager_;
   }

   public void addImage(TaggedImage img) {
      try {
         engineOutputQueue_.put(img);
      } catch (InterruptedException ex) {
         ReportingUtils.showError("Acquisition engine thread interrupted");
      }
   }

   protected abstract JSONArray createInitialPositionList();

}