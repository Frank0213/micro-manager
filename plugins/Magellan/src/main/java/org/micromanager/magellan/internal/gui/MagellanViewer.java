/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.magellan.internal.gui;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import javax.swing.JPanel;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.magellan.internal.magellanacq.ExploreAcquisition;
import org.micromanager.magellan.internal.magellanacq.MagellanDatasetAndAcquisition;
import org.micromanager.magellan.internal.magellanacq.MagellanMD;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.api.ControlsPanelInterface;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;
import org.micromanager.ndviewer.api.ViewerInterface;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.overlay.Overlay;

/**
 * Extends the ND viewer with the information that the image is made up of a
 * bunch of tiles with Row and column indices
 *
 * @author henrypinkard
 */
public class MagellanViewer implements ViewerInterface {

   private ViewerInterface viewer_;
   private MagellanDatasetAndAcquisition manager_;

   public MagellanViewer(MagellanDatasetAndAcquisition cache, ViewerAcquisitionInterface acq,
                         JSONObject summmaryMD) {
      viewer_ = new NDViewer(cache, acq, summmaryMD, MagellanMD.getPixelSizeUm(summmaryMD),
            MagellanMD.isRGB(summmaryMD)) {
            public void setImageEvent(HashMap<String, Object> axes, boolean fromHuman) {
               super.setImageEvent(axes, fromHuman);
               if (axes.containsKey(AcqEngMetadata.Z_AXIS) && acq instanceof ExploreAcquisition) {
                  Integer i = (Integer) axes.get(AcqEngMetadata.Z_AXIS);
                  cache.updateExploreZControls(i);
               }
            }
         };
      manager_ = cache;
   }

   private void moveViewToVisibleArea() {
      //check for valid tiles (at lowest res) at this slice        
      Set<Point> tiles = manager_.getTileIndicesWithDataAt(
              (Integer) viewer_.getAxisPosition(AcqEngMetadata.Z_AXIS));
      if (tiles.size() == 0) {
         return;
      }
      // center of one tile must be within corners of current view
      double minDistance = Integer.MAX_VALUE;
      //do all calculations at full resolution
      long currentX = (long) viewer_.getViewOffset().x;
      long currentY = (long) viewer_.getViewOffset().y;

      //Check if any point is visible, if so return
      for (Point p : tiles) {
         //calclcate limits on margin of tile that must remain in view
         long tileX1 = (long) ((0.1 + p.x) * manager_.getDisplayTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * manager_.getDisplayTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * manager_.getDisplayTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * manager_.getDisplayTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) viewer_.getViewOffset().x;
         long fovY1 = (long) viewer_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + viewer_.getFullResSourceDataSize().x);
         long fovY2 = (long) (fovY1 + viewer_.getFullResSourceDataSize().y);

         //check if tile and fov intersect
         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;
         boolean intersection = xInView && yInView;

         if (intersection) {
            return; //at least one tile is in view, don't need to do anything
         }
      }

      //Go through all tiles and find minium move to reset visible criteria
      ArrayList<Point2D.Double> newPos = new ArrayList<Point2D.Double>();
      for (Point p : tiles) {
         //do all calculations at full resolution
         currentX = (long) viewer_.getViewOffset().x;
         currentY = (long) viewer_.getViewOffset().y;

         //calclcate limits on margin of tile that must remain in view
         long tileX1 = (long) ((0.1 + p.x) * manager_.getDisplayTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * manager_.getDisplayTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * manager_.getDisplayTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * manager_.getDisplayTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) viewer_.getViewOffset().x;
         long fovY1 = (long) viewer_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + viewer_.getFullResSourceDataSize().x);
         long fovY2 = (long) (fovY1 + viewer_.getFullResSourceDataSize().y);

         //check if tile and fov intersect
         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;

         //tile to fov corner to corner distances
         double tl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY1 - fovY2)
               * (tileY1 - fovY2)); //top left tile, botom right fov
         double tr = ((tileX2 - fovX1) * (tileX2 - fovX1) + (tileY1 - fovY2)
               * (tileY1 - fovY2)); // top right tile, bottom left fov
         double bl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY2 - fovY1)
               * (tileY2 - fovY1)); // bottom left tile, top right fov
         double br = ((tileX1 - fovX1) * (tileX1 - fovX1) + (tileY2 - fovY1)
               * (tileY2 - fovY1)); //bottom right tile, top left fov

         double closestCornerDistance = Math.min(Math.min(tl, tr), Math.min(bl, br));
         if (closestCornerDistance < minDistance) {
            minDistance = closestCornerDistance;
            long newX;
            long newY;
            if (tl <= tr && tl <= bl && tl <= br) { //top left tile, botom right fov
               newX = (long) (xInView ? currentX : tileX1 - viewer_.getFullResSourceDataSize().x);
               newY = (long) (yInView ? currentY : tileY1 - viewer_.getFullResSourceDataSize().y);
            } else if (tr <= tl && tr <= bl && tr <= br) { // top right tile, bottom left fov
               newX = xInView ? currentX : tileX2;
               newY = (long) (yInView ? currentY : tileY1 - viewer_.getFullResSourceDataSize().y);
            } else if (bl <= tl && bl <= tr && bl <= br) { // bottom left tile, top right fov
               newX = (long) (xInView ? currentX : tileX1 - viewer_.getFullResSourceDataSize().x);
               newY = yInView ? currentY : tileY2;
            } else { //bottom right tile, top left fov
               newX = xInView ? currentX : tileX2;
               newY = yInView ? currentY : tileY2;
            }
            newPos.add(new Point2D.Double(newX, newY));
         }
      }

      long finalCurrentX = currentX;
      long finalCurrentY = currentY;
      DoubleStream dists = newPos.stream().mapToDouble(value -> Math.pow(value.x - finalCurrentX, 2)
              + Math.pow(value.y - finalCurrentY, 2));

      double minDist = dists.min().getAsDouble();
      Point2D.Double newPoint =  newPos.stream().filter(
              value -> (Math.pow(value.x - finalCurrentX, 2)
              + Math.pow(value.y - finalCurrentY, 2))
                    == minDist).collect(Collectors.toList()).get(0);

      viewer_.setViewOffset(newPoint.x, newPoint.y);
   }

   public Point getTileIndicesFromDisplayedPixel(int x, int y) {
      double scale = viewer_.getMagnification();
      int fullResX = (int) ((x / scale) + viewer_.getViewOffset().x);
      int fullResY = (int) ((y / scale) + viewer_.getViewOffset().y);
      int xTileIndex = fullResX / manager_.getDisplayTileWidth() - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / manager_.getDisplayTileHeight() - (fullResY >= 0 ? 0 : 1);
      return new Point(xTileIndex, yTileIndex);
   }

   /**
    * return the pixel location in coordinates at appropriate res level of the
    * top left pixel for the given row/column
    *
    * @param row
    * @param col
    * @return
    */
   public Point getDisplayedPixel(long row, long col) {
      double scale = viewer_.getMagnification();
      int x = (int) ((col * manager_.getDisplayTileWidth() - viewer_.getViewOffset().x) * scale);
      int y = (int) ((row * manager_.getDisplayTileHeight() - viewer_.getViewOffset().y) * scale);
      return new Point(x, y);
   }

   //OVerride zoom and pan to restrain viewer to explored region in explore acqs
   public void pan(int dx, int dy) {
      viewer_.pan(dx, dy);
      if (manager_.getBounds() == null) {
         moveViewToVisibleArea();
         viewer_.update();
      }
   }

   public void zoom(double factor, Point mouseLocation) {
      viewer_.zoom(factor, mouseLocation);
      if (manager_.getBounds() == null) {
         moveViewToVisibleArea();
         viewer_.update();
      }
   }

   @Override
   public Object getAxisPosition(String z) {
      return viewer_.getAxisPosition(z);
   }

   @Override
   public Point2D.Double getViewOffset() {
      return viewer_.getViewOffset();
   }

   @Override
   public Point2D.Double getFullResSourceDataSize() {
      return viewer_.getFullResSourceDataSize();
   }

   @Override
   public double getMagnification() {
      return viewer_.getMagnification();
   }

   @Override
   public void update() {
      viewer_.update();
   }

   @Override
   public void setViewOffset(double newX, double newY) {
      viewer_.setViewOffset(newX, newY);
   }

   @Override
   public void setWindowTitle(String string) {
      viewer_.setWindowTitle(string);
   }

   @Override
   public JSONObject getDisplaySettingsJSON() {
      return viewer_.getDisplaySettingsJSON();
   }

   @Override
   public void close() {
      viewer_.close();
   }

   @Override
   public void newImageArrived(HashMap<String, Object> axes) {
      viewer_.newImageArrived(axes);
   }

   @Override
   public void setAxisPosition(String axis, int pos) {
      viewer_.setAxisPosition(axis, pos);
   }

   @Override
   public void setChannelColor(String chName, Color c) {
      viewer_.setChannelColor(chName, c);
   }

   @Override
   public void setReadTimeMetadataFunction(Function<JSONObject, Long> fn) {
      viewer_.setReadTimeMetadataFunction(fn);
   }

   @Override
   public void setReadZMetadataFunction(Function<JSONObject, Double> fn) {
      viewer_.setReadZMetadataFunction(fn);
   }

   @Override
   public JPanel getCanvasJPanel() {
      return viewer_.getCanvasJPanel();
   }

   @Override
   public void redrawOverlay() {
      viewer_.redrawOverlay();
   }

   public void setCustomCanvasMouseListener(CanvasMouseListenerInterface m) {
      viewer_.setCustomCanvasMouseListener(m);
   }

   @Override
   public Point2D.Double getDisplayImageSize() {
      return viewer_.getDisplayImageSize();
   }

   @Override
   public void setOverlay(Overlay overlay) {
      viewer_.setOverlay(overlay);
   }

   @Override
   public void setOverlayerPlugin(OverlayerPlugin overlayer) {
      viewer_.setOverlayerPlugin(overlayer);
   }

   @Override
   public void addControlPanel(ControlsPanelInterface exploreChannelsPanel) {
      viewer_.addControlPanel(exploreChannelsPanel);
   }

   @Override
   public void initializeViewerToLoaded(List<String> channelNames, JSONObject dispSettings,
                                        HashMap<String, Object> axisMins,
                                        HashMap<String, Object> axisMaxs) {
      viewer_.initializeViewerToLoaded(channelNames, dispSettings, axisMins, axisMaxs);
   }
}
