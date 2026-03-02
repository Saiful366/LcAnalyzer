package sc.fiji.lc_analyzer;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * LcAnalyzer — Chemi / Membrane ROI Threshold & Particle Analyzer
 *
 * @author Saiful Islam
 * @version 1.0.5
 */
public class Lc_Analyzer implements PlugIn {

    static String    baseTitle   = "";
    static String    mode        = "";
    static String    imagePath   = "";
    static LcPanel   panel       = null;
    static ImagePlus lastDrawing    = null;
    static ImagePlus lastSelectedROI = null;
    static ImagePlus lastOverlay    = null;
    static File      lastDir     = null;   // remembers last browsed directory
    static boolean   selectClicked = false; // true once user clicks Select in Threshold Color
    static int       roiCounter    = 0;     // running count across all membrane analyses

    @Override
    public void run(String arg) {
        if (panel == null || !panel.isDisplayable()) {
            panel = new LcPanel();
        }
        panel.toFront();
        panel.setVisible(true);

    }

    /* ------------------------------------------------------------------ */
    /*  F1                                                                 */
    /* ------------------------------------------------------------------ */
    static void doF1() {
        closeLogIfOpen();
        if (WindowManager.getImageCount() == 0) {
            IJ.showStatus("Open an image first."); return;
        }
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null || imp.getRoi() == null ||
                imp.getRoi().getType() != ij.gui.Roi.RECTANGLE) {
            IJ.showStatus("Draw a rectangle ROI, then press F1."); return;
        }

        baseTitle = imp.getTitle();
        ensureMode(imp);
        closeAnySelectedROI();

        IJ.run(imp, "Duplicate...", "title=SelectedROI");
        ImagePlus roi = WindowManager.getImage("SelectedROI");
        if (roi == null) { IJ.error("LcAnalyzer", "Duplicate failed."); return; }

        IJ.run(roi, "Remove Overlay", "");
        if ("chemi".equals(mode)) IJ.run(roi, "Invert", "");

        if (WindowManager.getWindow("Threshold Color") != null) {
            IJ.selectWindow("Threshold Color");
            IJ.run("Close");
        }
        IJ.run("Color Threshold...");
        selectClicked = false;  // reset — user must click Select before F2
        startSelectWatcher();   // watch for Select button click in Threshold Color panel

        IJ.showStatus("[" + mode.toUpperCase() + "] Threshold ready. Click Select, then press F2.");
        if (panel != null) panel.setStatus("[" + mode.toUpperCase() + "] Threshold ready → click Select → F2");
    }

    /* ------------------------------------------------------------------ */
    /*  Watch for "Select" click in Threshold Color panel                  */
    /* ------------------------------------------------------------------ */
    static java.awt.event.AWTEventListener selectWatcher = null;

    static void startSelectWatcher() {
        // Remove previous watcher if any
        if (selectWatcher != null) {
            try { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(selectWatcher); }
            catch (Exception ignored) {}
        }
        selectWatcher = event -> {
            if (!(event instanceof ActionEvent)) return;
            ActionEvent ae = (ActionEvent) event;
            Object src = ae.getSource();
            // Check if source is the "Select" button inside the Threshold Color window
            if (src instanceof java.awt.Button) {
                java.awt.Button btn = (java.awt.Button) src;
                if ("Select".equals(btn.getLabel())) {
                    selectClicked = true;
                    if (panel != null) panel.setStatus(
                        "[" + mode.toUpperCase() + "] Select done → press F2");
                }
            }
            if (src instanceof javax.swing.JButton) {
                javax.swing.JButton btn = (javax.swing.JButton) src;
                if ("Select".equals(btn.getText())) {
                    selectClicked = true;
                    if (panel != null) panel.setStatus(
                        "[" + mode.toUpperCase() + "] Select done → press F2");
                }
            }
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
            selectWatcher, java.awt.AWTEvent.ACTION_EVENT_MASK);
    }

    /* ------------------------------------------------------------------ */
    /*  F2                                                                 */
    /* ------------------------------------------------------------------ */
    static void doF2() {
        closeLogIfOpen();
        String roiName = getLatestSelectedROI();
        if (roiName == null) {
            IJ.showStatus("Press F1 first to create the ROI duplicate."); return;
        }
        if (!selectClicked) {
            IJ.showStatus("Click 'Select' in the Threshold Color panel first.");
            if (panel != null) panel.setStatus("Click 'Select' in Threshold Color first.");
            return;
        }

        ensureMode(null);

        // Close Threshold Color before membrane analysis
        if ("membrane".equals(mode) &&
                WindowManager.getWindow("Threshold Color") != null) {
            IJ.selectWindow("Threshold Color");
            IJ.run("Close");
            IJ.wait(50);
        }

        ImagePlus roiImp = WindowManager.getImage(roiName);
        if (roiImp != null) roiImp.getWindow().toFront();

        if ("chemi".equals(mode)) {
            IJ.run("Measure");
            // Explicitly show Results window — after Close All the window reference is stale
            javax.swing.SwingUtilities.invokeLater(() -> {
                ResultsTable rt2 = ResultsTable.getResultsTable();
                if (rt2 != null && rt2.size() > 0) rt2.show("Results");
                markResultsWindowClean();
                suppressResultsSavePrompt();
            });
        } else {
            // Close any existing ROI Manager to start fresh
            RoiManager existingRM = RoiManager.getInstance();
            if (existingRM != null) existingRM.close();

            // Run Analyze Particles — add ROIs to manager for sorting, no auto-drawing
            IJ.run("Analyze Particles...",
                   "size=80-Infinity pixel show=Nothing display add");
            IJ.wait(150);
            // Strip auto-generated overlay (removes numbers added by "display")
            ImagePlus roiImpStrip = WindowManager.getImage(roiName);
            if (roiImpStrip != null) {
                roiImpStrip.setOverlay(null);
                roiImpStrip.updateAndDraw();
            }

            // Sort ROIs left-to-right so Drawing labels match Results table row numbers
            RoiManager rm = RoiManager.getInstance();
            if (rm != null && rm.getCount() > 0) {
                Roi[] rois = rm.getRoisAsArray();

                // Sort index array by bounding-box X (left to right)
                Integer[] indices = new Integer[rois.length];
                for (int i = 0; i < indices.length; i++) indices[i] = i;
                Arrays.sort(indices, Comparator.comparingInt(i -> rois[i].getBounds().x));

                // Deep-copy the NEW rows just added by Analyze Particles
                // (rows from previousRowCount onward) — keep older rows untouched
                ResultsTable rtOld = ResultsTable.getResultsTable();
                int totalRows = (rtOld != null) ? rtOld.size() : 0;
                int newRowCount = indices.length;          // rows added this run
                int previousRowCount = totalRows - newRowCount; // rows from prior runs
                String[] headings = (rtOld != null) ? rtOld.getHeadings() : new String[0];

                // Copy only the new rows into local arrays
                double[][] numVals = new double[newRowCount][headings.length];
                String[][] strVals = new String[newRowCount][headings.length];
                for (int row = 0; row < newRowCount; row++) {
                    int srcRow = previousRowCount + row;
                    for (int col = 0; col < headings.length; col++) {
                        try { numVals[row][col] = rtOld.getValue(headings[col], srcRow); }
                        catch (Exception e) { numVals[row][col] = Double.NaN; }
                        try { strVals[row][col] = rtOld.getStringValue(headings[col], srcRow); }
                        catch (Exception e) { strVals[row][col] = ""; }
                    }
                }

                // Remove only the new (unsorted) rows, keep previous rows intact
                ResultsTable globalRT = ResultsTable.getResultsTable();
                // Truncate back to previousRowCount by deleting from the end
                for (int i = totalRows - 1; i >= previousRowCount; i--) {
                    globalRT.deleteRow(i);
                }
                // Append new rows in sorted (left-to-right) order
                for (int rank = 0; rank < indices.length; rank++) {
                    int origIdx = indices[rank];
                    if (origIdx >= newRowCount) continue;
                    globalRT.incrementCounter();
                    for (int col = 0; col < headings.length; col++) {
                        double v = numVals[origIdx][col];
                        if (!Double.isNaN(v)) globalRT.addValue(headings[col], v);
                        else globalRT.addValue(headings[col], strVals[origIdx][col]);
                    }
                }

                // Build Drawing image with labels matching sorted Results row numbers
                ImagePlus roiImpForDraw = WindowManager.getImage(roiName);
                if (roiImpForDraw != null) {
                    int w = roiImpForDraw.getWidth();
                    int h = roiImpForDraw.getHeight();
                    ImagePlus drawing = IJ.createImage(
                        "Drawing of SelectedROI", "8-bit white", w, h, 1);
                    ij.process.ImageProcessor drawIP = drawing.getProcessor();
                    drawIP.setColor(0);
                    drawIP.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9));

                    for (int rank = 0; rank < indices.length; rank++) {
                        Roi r = rois[indices[rank]];

                        // Drawing: black outline + cumulative number across all analyses
                        drawIP.draw(r);
                        java.awt.Rectangle b = r.getBounds();
                        int lx = b.x + b.width / 2 - 3;
                        int ly = b.y + b.height / 2 + 4;
                        drawIP.drawString(String.valueOf(roiCounter + rank + 1), lx, ly);
                    }
                    roiCounter += indices.length; // advance counter for next analysis

                    // Draw yellow outlines directly onto SelectedROI pixels — no overlay,
                    // no labels possible since there is no overlay object at all
                    IJ.run(roiImpForDraw, "Remove Overlay", "");
                    IJ.run(roiImpForDraw, "RGB Color", "");
                    // Re-fetch after RGB conversion (window may have been replaced)
                    roiImpForDraw = WindowManager.getImage(roiName);
                    if (roiImpForDraw != null) {
                        ij.process.ImageProcessor rgbIP = roiImpForDraw.getProcessor();
                        rgbIP.setColor(java.awt.Color.YELLOW);
                        rgbIP.setLineWidth(1);
                        for (int rank = 0; rank < indices.length; rank++) {
                            rgbIP.draw(rois[indices[rank]]);
                        }
                        roiImpForDraw.updateAndDraw();
                    }

                    drawing.show();
                    lastDrawing = drawing;
                }

                rm.close();

            } else {
                // Fallback if ROI manager empty
                Set<Integer> before = getOpenImageIds();
                IJ.run("Analyze Particles...",
                       "size=80-Infinity pixel show=Outlines display");
                IJ.wait(150);
                lastDrawing = findNewDrawing(before);
            }

            // Show sorted Results table
            ResultsTable rt = ResultsTable.getResultsTable();
            if (rt != null && rt.size() > 0) {
                rt.show("Results");
                markResultsWindowClean();
                suppressResultsSavePrompt();
            }
        }

        // Ensure listeners are stripped (covers chemi path too)
        suppressResultsSavePrompt();

        // Update Export Drawing button state
        if (panel != null) {
            panel.updateDrawingBtn("membrane".equals(mode) && lastDrawing != null);
            // (overlay button removed from panel)
        }

        // Close Threshold Color if it re-opened
        if (WindowManager.getWindow("Threshold Color") != null) {
            IJ.selectWindow("Threshold Color");
            IJ.run("Close");
        }

        // Keep SelectedROI window open for user inspection
        roiImp = WindowManager.getImage(roiName);

        // Keep SelectedROI open for BOTH chemi and membrane — user may want to inspect it
        lastSelectedROI = roiImp;
        if (lastSelectedROI != null) {
            lastSelectedROI.changes = false;
            java.awt.Window win = lastSelectedROI.getWindow();
            if (win != null) {
                for (java.awt.event.WindowListener wl : win.getWindowListeners())
                    win.removeWindowListener(wl);
                win.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowClosing(java.awt.event.WindowEvent e) {
                        lastSelectedROI.changes = false;
                        lastSelectedROI.close();
                        lastSelectedROI = null;
                        if (panel != null) panel.updateSelectedROIBtn(false);
                    }
                });
            }
        }
        if (panel != null) panel.updateSelectedROIBtn(lastSelectedROI != null);

        cleanBase();
        IJ.showStatus("[" + mode.toUpperCase() + "] Analysis complete. Draw next ROI → F1.");
        if (panel != null) panel.setStatus("[" + mode.toUpperCase() + "] Done. Draw next ROI → F1");
    }

    /* ------------------------------------------------------------------ */
    /*  Export Results as CSV                                              */
    /* ------------------------------------------------------------------ */
    static void exportCSV() {
        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt == null || rt.size() == 0) {
            JOptionPane.showMessageDialog(panel,
                "No results to export yet.\nRun an analysis first.",
                "LcAnalyzer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Save Results as CSV");
        fc.setSelectedFile(new File(lastDir != null ? lastDir.getAbsolutePath() : "",
            "LcAnalyzer_Results.csv"));
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (fc.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        lastDir = out.getParentFile();  // remember for next time
        if (!out.getName().toLowerCase().endsWith(".csv"))
            out = new File(out.getAbsolutePath() + ".csv");

        try (FileWriter fw = new FileWriter(out)) {
            String[] cols = rt.getHeadings();
            StringBuilder sb = new StringBuilder("Row");
            for (String col : cols) sb.append(",").append(col);
            sb.append("\n");
            for (int i = 0; i < rt.size(); i++) {
                sb.append(i + 1);
                for (String col : cols) {
                    sb.append(",");
                    try {
                        double val = rt.getValue(col, i);
                        if (Double.isNaN(val))
                            sb.append(rt.getStringValue(col, i));
                        else if (val == Math.floor(val) && !Double.isInfinite(val))
                            sb.append((long) val);
                        else
                            sb.append(String.format("%.4f", val));
                    } catch (Exception e) {
                        sb.append(rt.getStringValue(col, i));
                    }
                }
                sb.append("\n");
            }
            fw.write(sb.toString());
            // no success popup — silent export
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel,
                "Failed to save:\n" + ex.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Export Drawing as PNG (membrane only)                              */
    /* ------------------------------------------------------------------ */
    static void exportDrawing() {
        if (lastDrawing == null) {
            JOptionPane.showMessageDialog(panel,
                "No drawing available.\nRun a membrane analysis first.",
                "LcAnalyzer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Save Drawing as PNG");
        fc.setSelectedFile(new File(lastDir != null ? lastDir.getAbsolutePath() : "",
            "Drawing_SelectedROI.png"));
        fc.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
        if (fc.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".png"))
            out = new File(out.getAbsolutePath() + ".png");
        lastDir = out.getParentFile();  // remember for next time

        IJ.saveAs(lastDrawing, "PNG", out.getAbsolutePath());
        // no success popup — silent export
    }

    /* ------------------------------------------------------------------ */
    /*  Export SelectedROI as PNG                                          */
    /* ------------------------------------------------------------------ */
    static void exportSelectedROI() {
        if (lastSelectedROI == null || !lastSelectedROI.isVisible()) {
            JOptionPane.showMessageDialog(panel,
                "No SelectedROI image available.\nRun F1 and F2 first.",
                "LcAnalyzer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Save SelectedROI as PNG");
        fc.setSelectedFile(new File(lastDir != null ? lastDir.getAbsolutePath() : "",
            "SelectedROI.png"));
        fc.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
        if (fc.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".png"))
            out = new File(out.getAbsolutePath() + ".png");
        lastDir = out.getParentFile();

        IJ.saveAs(lastSelectedROI, "PNG", out.getAbsolutePath());
    }

    /* ------------------------------------------------------------------ */
    /*  Overlay SelectedROI + Drawing                                      */
    /* ------------------------------------------------------------------ */
    static void overlayImages() {
        if (lastSelectedROI == null) {
            JOptionPane.showMessageDialog(panel,
                "No SelectedROI available.\nRun F1 + F2 first.",
                "LcAnalyzer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lastDrawing == null) {
            JOptionPane.showMessageDialog(panel,
                "No Drawing available.\nRun a membrane analysis first.",
                "LcAnalyzer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get dimensions from SelectedROI
        int w = lastSelectedROI.getWidth();
        int h = lastSelectedROI.getHeight();

        // Convert SelectedROI to RGB
        ImagePlus base = lastSelectedROI.duplicate();
        IJ.run(base, "RGB Color", "");

        // Scale Drawing to match SelectedROI size if needed
        ImagePlus drawing = lastDrawing.duplicate();
        if (drawing.getWidth() != w || drawing.getHeight() != h) {
            drawing.getProcessor().setInterpolationMethod(ij.process.ImageProcessor.BILINEAR);
            drawing.setProcessor(drawing.getProcessor().resize(w, h, true));
        }
        IJ.run(drawing, "RGB Color", "");

        // Composite: wherever Drawing pixel is non-black, paint it cyan on base
        java.awt.image.BufferedImage baseBI    = base.getBufferedImage();
        java.awt.image.BufferedImage drawingBI = drawing.getBufferedImage();
        java.awt.image.BufferedImage result    =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int bp = baseBI.getRGB(x, y);
                int dp = drawingBI.getRGB(x, y);
                int dr = (dp >> 16) & 0xff;
                int dg = (dp >>  8) & 0xff;
                int db =  dp        & 0xff;
                // If drawing pixel is bright (outline) — paint cyan overlay
                if (dr + dg + db > 60) {
                    result.setRGB(x, y, 0x00BFFF); // deep sky blue outline
                } else {
                    result.setRGB(x, y, bp);         // original base pixel
                }
            }
        }

        ImagePlus overlay = new ImagePlus("Overlay_ROI+Drawing", result);
        overlay.show();

        // Silent close on X
        java.awt.Window win = overlay.getWindow();
        if (win != null) {
            for (java.awt.event.WindowListener wl : win.getWindowListeners())
                win.removeWindowListener(wl);
            win.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    overlay.changes = false;
                    overlay.close();
                }
            });
        }

        // Store reference so Export Overlay button can save it
        lastOverlay = overlay;
        if (panel != null) panel.updateOverlayBtn(true);

        base.close();
        drawing.close();
    }

    static void exportOverlay() {
        if (lastOverlay == null) {
            JOptionPane.showMessageDialog(panel,
                "No overlay available. Click Overlay ROI + Drawing first.",
                "LcAnalyzer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Save Overlay as PNG");
        fc.setSelectedFile(new File(lastDir != null ? lastDir.getAbsolutePath() : "",
            "Overlay_ROI_Drawing.png"));
        fc.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
        if (fc.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".png"))
            out = new File(out.getAbsolutePath() + ".png");
        lastDir = out.getParentFile();
        IJ.saveAs(lastOverlay, "PNG", out.getAbsolutePath());
    }

    /* ------------------------------------------------------------------ */
    /*  Close individual windows silently                                  */
    /* ------------------------------------------------------------------ */
    static void closeSelectedROI() {
        if (lastSelectedROI != null) {
            lastSelectedROI.changes = false;
            lastSelectedROI.close();
            lastSelectedROI = null;
        }
        if (panel != null) panel.updateSelectedROIBtn(false);
    }

    static void closeDrawing() {
        if (lastDrawing != null) {
            lastDrawing.changes = false;
            lastDrawing.close();
            lastDrawing = null;
        }
        if (panel != null) panel.updateDrawingBtn(false);
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                             */
    /* ------------------------------------------------------------------ */
    static void closeAll() {
        // Close all open images silently
        int[] ids = WindowManager.getIDList();
        if (ids != null) {
            for (int id : ids) {
                ImagePlus ip = WindowManager.getImage(id);
                if (ip != null) {
                    ip.changes = false;
                    ip.close();
                }
            }
        }
        // Reset ResultsTable fully so rt.show() creates a fresh window next run
        ResultsTable.getResultsTable().reset();
        ij.text.TextWindow existingResults = (ij.text.TextWindow) WindowManager.getFrame("Results");
        if (existingResults != null) existingResults.close(false);

        // Close Threshold Color if open
        if (WindowManager.getWindow("Threshold Color") != null) {
            IJ.selectWindow("Threshold Color");
            IJ.run("Close");
        }

        // Reset state
        baseTitle  = "";
        mode       = "";
        imagePath  = "";
        lastDrawing     = null;
        lastSelectedROI = null;
        lastOverlay     = null;
        roiCounter      = 0;
        if (panel != null) {
            panel.updateDrawingBtn(false);
            panel.updateSelectedROIBtn(false);
            panel.setStatus("Open an image to begin.");
        }
    }

    /**
     * Marks the ResultsTable as having no unsaved data so ImageJ never
     * shows the "Save N measurements?" dialog when the window is closed.
     * Uses reflection to set the private unsavedMeasurements field to false.
     */
    /**
     * Clears all "unsaved changes" flags on the Results window and ResultsTable
     * so ImageJ never thinks there is anything to save — dialog never spawns.
     */
    static void markResultsWindowClean() {
        try {
            java.awt.Frame rf = WindowManager.getFrame("Results");
            if (rf != null) {
                // Clear "changes" flag on the TextWindow frame itself
                try {
                    java.lang.reflect.Field f = rf.getClass().getDeclaredField("changes");
                    f.setAccessible(true);
                    f.setBoolean(rf, false);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Clear flags on ResultsTable (field name varies by ImageJ build)
        try {
            ResultsTable rt = ResultsTable.getResultsTable();
            if (rt != null) {
                for (String fieldName : new String[]{"unsavedMeasurements", "changed", "dirty", "modified"}) {
                    try {
                        java.lang.reflect.Field f2 = rt.getClass().getDeclaredField(fieldName);
                        f2.setAccessible(true);
                        if (f2.getType() == boolean.class) f2.setBoolean(rt, false);
                    } catch (Exception ignored2) {}
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Removes ImageJ's WindowListeners from the Results window (prevents save dialog)
     * and marks the window clean so no dirty flag triggers the dialog either.
     */
    static void suppressResultsSavePrompt() {
        markResultsWindowClean();
        try {
            java.awt.Frame rf = WindowManager.getFrame("Results");
            if (rf != null) {
                // Remove ALL of ImageJ's own listeners
                for (java.awt.event.WindowListener wl : rf.getWindowListeners())
                    rf.removeWindowListener(wl);
                // Replace with listener that calls closeResults() — same as the button
                // so X button and Close Results button behave identically: silent, no dialog
                rf.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowClosing(java.awt.event.WindowEvent e) {
                        closeResults();
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    /** Silently closes the Results window — called from the "Close Results" panel button. */
    static void closeResults() {
        java.awt.Frame rf = WindowManager.getFrame("Results");
        if (rf == null) return;
        markResultsWindowClean();
        try {
            if (rf instanceof ij.text.TextWindow) {
                ((ij.text.TextWindow) rf).close(false); // closes without "Save measurements?"
                return;
            }
        } catch (Exception ignored) {}
        rf.dispose();
    }

    static Set<Integer> getOpenImageIds() {
        Set<Integer> ids = new HashSet<>();
        int[] arr = WindowManager.getIDList();
        if (arr != null) for (int id : arr) ids.add(id);
        return ids;
    }

    static ImagePlus findNewDrawing(Set<Integer> before) {
        int[] arr = WindowManager.getIDList();
        if (arr == null) return null;
        ImagePlus found = null;
        for (int id : arr) {
            if (!before.contains(id)) {
                ImagePlus ip = WindowManager.getImage(id);
                if (ip != null && ip.getTitle().startsWith("Drawing of")) {
                    found = ip;
                }
            }
        }
        return found;
    }

    static void setModeFrom(String text) {
        String t = text.toLowerCase();
        if      (t.contains("membrane")) mode = "membrane";
        else if (t.contains("chemi"))    mode = "chemi";
        else                             mode = "chemi";
    }

    static void ensureMode(ImagePlus imp) {
        if (!mode.isEmpty()) return;
        if (imp != null)               setModeFrom(imp.getTitle());
        else if (!imagePath.isEmpty()) setModeFrom(imagePath);
        else                           mode = "chemi";
    }

    static void closeAnySelectedROI() {
        int[] ids = WindowManager.getIDList();
        if (ids == null) return;
        for (int id : ids) {
            ImagePlus ip = WindowManager.getImage(id);
            if (ip != null && ip.getTitle().startsWith("SelectedROI")) {
                ip.changes = false;
                ip.close();
            }
        }
    }

    static String getLatestSelectedROI() {
        int[] ids = WindowManager.getIDList();
        if (ids == null) return null;
        String latest = null;
        for (int id : ids) {
            ImagePlus ip = WindowManager.getImage(id);
            if (ip != null && ip.getTitle().startsWith("SelectedROI")) latest = ip.getTitle();
        }
        return latest;
    }

    static void cleanBase() {
        if (!baseTitle.isEmpty()) {
            ImagePlus base = WindowManager.getImage(baseTitle);
            if (base != null) IJ.run(base, "Remove Overlay", "");
        }
        closeLogIfOpen();
    }

    static void closeLogIfOpen() {
        if (WindowManager.getWindow("Log") != null) {
            IJ.selectWindow("Log");
            IJ.run("Close");
        }
    }

    /* ================================================================== */
    /*  Floating Control Panel                                             */
    /* ================================================================== */
    static class LcPanel extends JFrame {

        private JLabel  statusLabel;
        private JButton drawingBtn;
        private JButton selectedROIBtn;

        LcPanel() {
            super("LcAnalyzer");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setResizable(false);
            setAlwaysOnTop(true);
            buildUI();
            registerGlobalHotkeys();
            pack();
            setLocationRelativeTo(null);
        }

        private void buildUI() {
            JPanel main = new JPanel();
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

            // Title
            JLabel title = new JLabel("LcAnalyzer");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            main.add(title);
            main.add(Box.createVerticalStrut(12));

            // Browse
            JButton browseBtn = makeButton("Browse Image…", new Color(70, 130, 180));
            browseBtn.addActionListener(e -> browseAndOpen());
            main.add(browseBtn);
            main.add(Box.createVerticalStrut(8));

            addSep(main);

            // F1
            JButton f1Btn = makeButton("ROI to Threshold  [F1]", new Color(60, 160, 90));
            f1Btn.addActionListener(e -> Lc_Analyzer.doF1());
            main.add(f1Btn);
            main.add(Box.createVerticalStrut(8));

            // F2
            JButton f2Btn = makeButton("Apply and Analyze  [F2]", new Color(200, 100, 50));
            f2Btn.addActionListener(e -> Lc_Analyzer.doF2());
            main.add(f2Btn);
            main.add(Box.createVerticalStrut(8));

            addSep(main);

            // Export CSV
            JButton csvBtn = makeButton("Export Results as CSV", new Color(100, 60, 160));
            csvBtn.addActionListener(e -> Lc_Analyzer.exportCSV());
            main.add(csvBtn);
            main.add(Box.createVerticalStrut(8));

            // Export Drawing — starts greyed out
            drawingBtn = makeButton("Export Drawing of ROI", new Color(150, 150, 150));
            drawingBtn.setEnabled(false);
            drawingBtn.addActionListener(e -> Lc_Analyzer.exportDrawing());
            main.add(drawingBtn);
            main.add(Box.createVerticalStrut(8));

            // Export SelectedROI — starts greyed out
            selectedROIBtn = makeButton("Export SelectedROI", new Color(150, 150, 150));
            selectedROIBtn.setEnabled(false);
            selectedROIBtn.addActionListener(e -> Lc_Analyzer.exportSelectedROI());
            main.add(selectedROIBtn);
            main.add(Box.createVerticalStrut(8));

            addSep(main);

            // Close Results
            JButton closeResultsBtn = makeButton("Close Results", new Color(90, 90, 90));
            closeResultsBtn.addActionListener(e -> Lc_Analyzer.closeResults());
            main.add(closeResultsBtn);
            main.add(Box.createVerticalStrut(8));

            // Close SelectedROI
            JButton closeROIBtn = makeButton("Close SelectedROI", new Color(90, 90, 90));
            closeROIBtn.addActionListener(e -> Lc_Analyzer.closeSelectedROI());
            main.add(closeROIBtn);
            main.add(Box.createVerticalStrut(8));

            // Close Drawing
            JButton closeDrawingBtn = makeButton("Close Drawing of ROI", new Color(90, 90, 90));
            closeDrawingBtn.addActionListener(e -> Lc_Analyzer.closeDrawing());
            main.add(closeDrawingBtn);
            main.add(Box.createVerticalStrut(8));

            addSep(main);

            // Close All
            JButton closeAllBtn = makeButton("Close All", new Color(180, 50, 50));
            closeAllBtn.addActionListener(e -> Lc_Analyzer.closeAll());
            main.add(closeAllBtn);
            main.add(Box.createVerticalStrut(12));

            // Status
            statusLabel = new JLabel("Open an image to begin.");
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
            statusLabel.setForeground(Color.GRAY);
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            main.add(statusLabel);

            setContentPane(main);
        }

        private void addSep(JPanel p) {
            JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
            p.add(sep);
            p.add(Box.createVerticalStrut(8));
        }

        private JButton makeButton(String text, Color bg) {
            JButton btn = new JButton(text);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(260, 36));
            btn.setPreferredSize(new Dimension(260, 36));
            btn.setBackground(bg);
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return btn;
        }

        /** Enable or disable the Export Drawing button and change its colour */
        void updateDrawingBtn(boolean enabled) {
            SwingUtilities.invokeLater(() -> {
                drawingBtn.setEnabled(enabled);
                drawingBtn.setBackground(enabled
                    ? new Color(30, 160, 180)    // teal = active
                    : new Color(150, 150, 150));  // grey = inactive
            });
        }

        /** Enable or disable the Export SelectedROI button and change its colour */
        void updateSelectedROIBtn(boolean enabled) {
            SwingUtilities.invokeLater(() -> {
                selectedROIBtn.setEnabled(enabled);
                selectedROIBtn.setBackground(enabled
                    ? new Color(180, 120, 30)    // amber = active
                    : new Color(150, 150, 150));  // grey = inactive
            });
        }

        /** No-op: overlay buttons removed from panel */
        void updateOverlayBtn(boolean enabled) {}

        void setStatus(String text) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        }

        private void registerGlobalHotkeys() {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (e.getKeyCode() == KeyEvent.VK_F1) { Lc_Analyzer.doF1(); return true; }
                    if (e.getKeyCode() == KeyEvent.VK_F2) { Lc_Analyzer.doF2(); return true; }
                    return false;
                });
        }

        private void browseAndOpen() {
            OpenDialog od = new OpenDialog("Select Image File", "");
            if (od.getFileName() == null) return;

            imagePath = od.getPath();
            if (imagePath == null || imagePath.isEmpty() || !new File(imagePath).exists()) {
                IJ.error("LcAnalyzer", "No valid image file selected.");
                return;
            }
            // Remember this directory for CSV/Drawing exports
            lastDir = new File(imagePath).getParentFile();

            mode = "";
            lastDrawing     = null;
            lastSelectedROI = null;
            lastOverlay     = null;
            updateDrawingBtn(false);
            updateSelectedROIBtn(false);
            // overlay button removed from panel

            IJ.open(imagePath);
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) { IJ.error("LcAnalyzer", "Could not open image."); return; }

            baseTitle = imp.getTitle();
            setModeFrom(baseTitle);

            IJ.run("Set Measurements...",
                   "area mean standard min integrated median redirect=None decimal=3");
            closeLogIfOpen();

            setStatus("[" + mode.toUpperCase() + "] Ready — draw ROI → F1");
            IJ.showStatus("[" + mode.toUpperCase() + "] Ready. Draw ROI → F1.");
        }
    }
}
