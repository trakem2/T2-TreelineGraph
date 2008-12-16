/**

TrakEM2 plugin for ImageJ(C).
Copyright (C) 2005, 2006 Albert Cardona and Rodney Douglas.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. 

You may contact Albert Cardona at acardona at ini.phys.ethz.ch
Institute of Neuroinformatics, University of Zurich / ETH, Switzerland.
**/

package ini.trakem2.display;


import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.*;
import ini.trakem2.utils.Utils;
import ini.trakem2.utils.IJError;
import ini.trakem2.imaging.PatchStack;
import ini.trakem2.imaging.LayerStack;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.awt.image.ColorModel;
import java.awt.geom.Point2D;

/** Need a non-null ImagePlus for the ImageCanvas, even if fake. */
public class FakeImagePlus extends ImagePlus {
	
	private int w;
	private int h;
	private Display display;
	private int type;
	
	public FakeImagePlus(int width, int height, Display display) {
		w = width;
		h = height;
		this.display = display;
		setProcessor("", new FakeProcessor(width, height));
		type = ImagePlus.GRAY8;
	}
	protected Display getDisplay() {
		return display;
	}
	public int getType() { return type; }
	public int getWidth() {
		// trick the canvas, but not the ROIs
		//Class dc = null; try { dc = Class.forName("ini.trakem2.DisplayCanvas"); } catch (Exception e) {}
		if ((Utils.caller(this)).endsWith("ImageCanvas")) return 4;
		return w;
	}
	public int getHeight() {
		// trick the canvas, but not the ROIs
		//Class dc = null; try { dc = Class.forName("ini.trakem2.DisplayCanvas"); } catch (Exception e) {}
		if ((Utils.caller(this)).endsWith("ImageCanvas")) return 4;
		return h;
	}

	/** Used to resize the canvas. */
	public void setDimensions(int width, int height) {
		this.w = width;
		this.h = height;
	}

	public int[] getPixel(int x, int y) {
		try {
			//return display.getLayer().getPixel(x, y, display.getCanvas().getMagnification());
			return ((FakeProcessor)getProcessor()).getPixel(display.getCanvas().getMagnification(), x, y, null);
		} catch (Exception e) {
			IJError.print(e);
		}
		return new int[3];
	}

	private class FakeProcessor extends ByteProcessor {
		FakeProcessor(int width, int height) {
			// create a 4x4 processor (just because, perhaps to skip nulls)
			super(4,4);
		}
		/** Override to return the pixel of the Patch under x,y, if any. */
		public int getPixel(final int x, final int y) {
			return getPixel(1.0, x, y);
		}
		public int getPixel(double mag, final int x, final int y) {
			if (mag > 1) mag = 1;
			final ArrayList al = display.getLayer().getDisplayables();
			final Displayable[] d = new Displayable[al.size()];
			al.toArray(d);
			int pixel = 0; // will return black if nothing found
			// reverse lookup, for the top ones are at the bottom of the array
			for (int i=d.length -1; i>-1; i--) {
				if (d[i].getClass() == Patch.class && d[i].contains(x, y)) {
					Patch p = (Patch)d[i];
					FakeImagePlus.this.type = p.getType(); // for proper value string display
					return p.getPixel(mag, x, y);
				}
			}
			return pixel;
		}
		public int[] getPixel(int x, int y, int[] iArray) {
			return getPixel(1.0, x, y, iArray);
		}
		public int[] getPixel(double mag, int x, int y, int[] iArray) {
			if (mag > 1) mag = 1;
			ArrayList al = display.getLayer().getDisplayables();
			final Displayable[] d = new Displayable[al.size()];
			al.toArray(d);
			// reverse lookup, for the top ones are at the bottom of the array
			for (int i=d.length -1; i>-1; i--) {
				if (d[i].getClass() == Patch.class && d[i].contains(x, y)) {
					Patch p = (Patch)d[i];
					FakeImagePlus.this.type = p.getType(); // for proper value string display
					if (!p.isStack() && Math.max(p.getWidth(), p.getHeight()) * mag >= 1024) {
						// Gather the ImagePlus: will be faster than using a PixelGrabber on an awt image
						Point2D.Double po = p.inverseTransformPoint(x, y);
						return p.getImagePlus().getProcessor().getPixel((int)po.x, (int)po.y, iArray);
					}
					return p.getPixel(mag, x, y, iArray);
				}
			}
			return null == iArray ? new int[4] : iArray;
		}
		public int getWidth() { return w; }
		public int getHeight() { return h; }

		public void setColorModel(ColorModel cm) {
			display.getSelection().setLut(cm);
		}
	}

	// TODO: use layerset virtualization
	public ImageStatistics getStatistics(int mOptions, int nBins, double histMin, double histMax) {
		Displayable active = display.getActive();
		if (null == active || !(active instanceof Patch)) {
			Utils.log("No patch selected.");
			return super.getStatistics(mOptions, nBins, histMin, histMax); // TODO can't return null, but something should be done about it.
		}
		ImagePlus imp = active.getProject().getLoader().fetchImagePlus((Patch)active);
		ImageProcessor ip = imp.getProcessor(); // don't create a new onw every time // ((Patch)active).getProcessor();
		Roi roi = super.getRoi();
		if (null != roi) {
			// translate ROI to be meaningful for the Patch
			int patch_x = (int)active.getX();
			int patch_y = (int)active.getY();
			Rectangle r = roi.getBounds();
			roi.setLocation(patch_x - r.x, patch_y - r.y);
		}
		ip.setRoi(roi); // even if null, to reset
		ip.setHistogramSize(nBins);
		Calibration cal = getCalibration();
		if (getType()==GRAY16&& !(histMin==0.0&&histMax==0.0))
			{histMin=cal.getRawValue(histMin); histMax=cal.getRawValue(histMax);}
		ip.setHistogramRange(histMin, histMax);
		ImageStatistics stats = ImageStatistics.getStatistics(ip, mOptions, cal);
		ip.setHistogramSize(256);
		ip.setHistogramRange(0.0, 0.0);
		return stats;
	}

	/** Returns a virtual stack made of boxes with the dimension of the ROI or the whole layer, so that pixels are retrieved on the fly. */
	public ImageStack getStack() {
		return null;
	}

	/** Returns 1. */
	public int getStackCount() {
		return 1;
		//return display.getLayer().getParent().size();
	}

	public boolean isVirtual() { return true; }

	/** Returns the super, which is a dummy 4x4 */ //Returns a virtual stack made of boxes with the dimension of the ROI or the whole layer, so that pixels are retrieved on the fly.
	public ImageProcessor getProcessor() {
		return super.getProcessor();
	}

	/** Forward to LayerSet. */
	public void setCalibration(Calibration cal) {
		try { super.setCalibration(cal); } catch (Throwable e) { IJError.print(e); }
		display.getLayer().getParent().setCalibration(cal);
	}

	public void setCalibrationSuper(Calibration cal) {
		super.setCalibration(cal);
	}

	public Calibration getCalibration() {
		// initialization problems with ij1.40a
		if (null == display || null == display.getLayer()) return new Calibration();
		return display.getLayer().getParent().getCalibrationCopy();
	}

	/** Forward kill roi to the last_temp of the associated Display. */
	public void killRoi() {
		if (null!=roi) {
			saveRoi();
			roi = null;
			ImageProcessor ip = getProcessor();
			if (null != ip) ip.resetRoi();
		}
	}

	public synchronized void setSlice(int slice) {}

	public void updateAndRepaintWindow() {
		// TODO: if a selected image is a stack, the LUT applies to it as well...
		Display.repaint(display.getLayer(), display.getSelection().getBox(), 0);
	}
}
