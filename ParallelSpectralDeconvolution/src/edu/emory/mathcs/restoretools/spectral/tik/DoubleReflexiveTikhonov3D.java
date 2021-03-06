/*
 *  Copyright (C) 2008-2009 Piotr Wendykier
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.emory.mathcs.restoretools.spectral.tik;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import optimization.DoubleFmin;
import optimization.DoubleFmin_methods;
import cern.colt.matrix.tdouble.DoubleMatrix3D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix3D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.emory.mathcs.restoretools.Enums.OutputType;
import edu.emory.mathcs.restoretools.spectral.AbstractDoubleSpectralDeconvolver3D;
import edu.emory.mathcs.restoretools.spectral.DoubleCommon2D;
import edu.emory.mathcs.restoretools.spectral.DoubleCommon3D;
import edu.emory.mathcs.restoretools.spectral.SpectralEnums.PaddingType;
import edu.emory.mathcs.restoretools.spectral.SpectralEnums.ResizingType;

/**
 * 3D Tikhonov with reflexive boundary conditions.
 * 
 * @author Piotr Wendykier (piotr.wendykier@gmail.com)
 * 
 */
public class DoubleReflexiveTikhonov3D extends AbstractDoubleSpectralDeconvolver3D {

    private DoubleMatrix3D E1;

    private DoubleMatrix3D S;

    /**
     * Creates new instance of DoubleTikDCT3D
     * 
     * @param imB
     *            blurred image
     * @param imPSF
     *            Point Spread Function image
     * @param resizing
     *            type of resizing
     * @param output
     *            type of output
     * @param showPadded
     *            if true, then a padded image is displayed
     * @param regParam
     *            regularization parameter. If regParam == -1 then the
     *            regularization parameter is computed by Generalized Cross
     *            Validation.
     * @param threshold
     *            the smallest positive value assigned to the restored image,
     *            all the values less than the threshold are set to zero. To
     *            disable thresholding use threshold = -1.
     */
    public DoubleReflexiveTikhonov3D(ImagePlus imB, ImagePlus imPSF, ResizingType resizing, OutputType output, boolean showPadded, double regParam, double threshold) {
        super("Tikhonov", imB, imPSF, resizing, output, PaddingType.REFLEXIVE, showPadded, regParam, threshold);
    }

    public ImagePlus deconvolve() {
        IJ.showStatus(name + ": deconvolving");
        E1 = new DenseDoubleMatrix3D(bSlicesPad, bRowsPad, bColumnsPad);
        E1.setQuick(0, 0, 0, 1);
        ((DenseDoubleMatrix3D) E1).dct3(true);
        ((DenseDoubleMatrix3D) B).dct3(true);
        S = DoubleCommon3D.dctShift((DoubleMatrix3D) PSF, psfCenter);
        ((DenseDoubleMatrix3D) S).dct3(true);
        S.assign(E1, DoubleFunctions.div);
        if (ragParam == -1) {
            IJ.showStatus(name + ": computing regularization parameter");
            ragParam = gcvTikDCT3D(S, (DoubleMatrix3D) B);
        }
        IJ.showStatus(name + ": deconvolving");
        PSF = S.copy();
        ((DoubleMatrix3D) PSF).assign(DoubleFunctions.square);
        E1 = ((DoubleMatrix3D) PSF).copy();
        ((DoubleMatrix3D) PSF).assign(DoubleFunctions.plus(ragParam * ragParam));
        ((DoubleMatrix3D) B).assign(S, DoubleFunctions.mult);
        S = ((DoubleMatrix3D) B).copy();
        S.assign((DoubleMatrix3D) PSF, DoubleFunctions.div);
        ((DenseDoubleMatrix3D) S).idct3(true);
        IJ.showStatus(name + ": finalizing");
        ImageStack stackOut = new ImageStack(bColumns, bRows);
        if (threshold == -1) {
            if (isPadded) {
                DoubleCommon3D.assignPixelsToStackPadded(stackOut, S, bSlices, bRows, bColumns, bSlicesOff, bRowsOff, bColumnsOff, cmY);
            } else {
                DoubleCommon3D.assignPixelsToStack(stackOut, S, cmY);
            }
        } else {
            if (isPadded) {
                DoubleCommon3D.assignPixelsToStackPadded(stackOut, S, bSlices, bRows, bColumns, bSlicesOff, bRowsOff, bColumnsOff, cmY, threshold);
            } else {
                DoubleCommon3D.assignPixelsToStack(stackOut, S, cmY, threshold);
            }
        }
        ImagePlus imX = new ImagePlus("Deblurred", stackOut);
        DoubleCommon3D.convertImage(imX, output);
        imX.setProperty("regParam", ragParam);
        return imX;
    }

    public void update(double regParam, double threshold, ImagePlus imX) {
        IJ.showStatus("Tikhonov: updating");
        PSF = E1.copy();
        ((DoubleMatrix3D) PSF).assign(DoubleFunctions.plus(regParam * regParam));
        S = ((DoubleMatrix3D) B).copy();
        S.assign((DoubleMatrix3D) PSF, DoubleFunctions.div);
        ((DenseDoubleMatrix3D) S).idct3(true);
        IJ.showStatus("Tikhonov: finalizing");
        ImageStack stackOut = new ImageStack(bColumns, bRows);
        if (threshold == -1) {
            if (isPadded) {
                DoubleCommon3D.assignPixelsToStackPadded(stackOut, S, bSlices, bRows, bColumns, bSlicesOff, bRowsOff, bColumnsOff, cmY);
            } else {
                DoubleCommon3D.assignPixelsToStack(stackOut, S, cmY);
            }
        } else {
            if (isPadded) {
                DoubleCommon3D.assignPixelsToStackPadded(stackOut, S, bSlices, bRows, bColumns, bSlicesOff, bRowsOff, bColumnsOff, cmY, threshold);
            } else {
                DoubleCommon3D.assignPixelsToStack(stackOut, S, cmY, threshold);
            }
        }
        imX.setStack(imX.getTitle(), stackOut);
        DoubleCommon3D.convertImage(imX, output);
    }

    private static double gcvTikDCT3D(DoubleMatrix3D S, DoubleMatrix3D Bhat) {
        DoubleMatrix3D s = S.copy();
        DoubleMatrix3D bhat = Bhat.copy();
        s = s.assign(DoubleFunctions.abs);
        bhat = bhat.assign(DoubleFunctions.abs);
        double[] tmp = s.getMinLocation();
        double smin = tmp[0];
        tmp = s.getMaxLocation();
        double smax = tmp[0];
        s = s.assign(DoubleFunctions.square);
        TikFmin3D fmin = new TikFmin3D(s, bhat);
        return (double) DoubleFmin.fmin(smin, smax, fmin, DoubleCommon2D.FMIN_TOL);
    }

    private static class TikFmin3D implements DoubleFmin_methods {
        DoubleMatrix3D ssquare;

        DoubleMatrix3D bhat;

        public TikFmin3D(DoubleMatrix3D ssquare, DoubleMatrix3D bhat) {
            this.ssquare = ssquare;
            this.bhat = bhat;
        }

        public double f_to_minimize(double regParam) {
            DoubleMatrix3D sloc = ssquare.copy();
            DoubleMatrix3D bhatloc = bhat.copy();
            sloc.assign(DoubleFunctions.plus(regParam * regParam));
            sloc.assign(DoubleFunctions.inv);
            bhatloc.assign(sloc, DoubleFunctions.mult);
            bhatloc.assign(DoubleFunctions.square);
            double ss = sloc.zSum();
            return bhatloc.zSum() / (ss * ss);
        }

    }
}
