/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.interestrate.annuity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.Validate;

import com.opengamma.analytics.financial.interestrate.PresentValueCalculator;
import com.opengamma.analytics.financial.interestrate.PresentValueCurveSensitivityCalculator;
import com.opengamma.analytics.financial.interestrate.YieldCurveBundle;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.Annuity;
import com.opengamma.analytics.financial.interestrate.payments.derivative.Payment;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.rootfinding.BracketRoot;
import com.opengamma.analytics.math.rootfinding.BrentSingleRootFinder;
import com.opengamma.analytics.math.rootfinding.RealSingleRootFinder;
import com.opengamma.util.tuple.DoublesPair;

/**
 * 
 */
public final class ZSpreadCalculator {
  private static final PresentValueCalculator PRESENT_VALUE_CALCULATOR = PresentValueCalculator.getInstance();
  private static final PresentValueCurveSensitivityCalculator PV_SENSITIVITY_CALCULATOR = PresentValueCurveSensitivityCalculator.getInstance();
  private static final BracketRoot ROOT_BRACKETER = new BracketRoot();
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();
  private static final ZSpreadCalculator CALCULATOR = new ZSpreadCalculator();

  private ZSpreadCalculator() {
  }

  public static ZSpreadCalculator getInstance() {
    return CALCULATOR;
  }

  public double calculateZSpread(final Annuity<? extends Payment> annuity, final YieldCurveBundle curves, final double price) {
    Validate.notNull(annuity, "annuity");
    Validate.notNull(curves, "curves");

    final Function1D<Double, Double> f = new Function1D<Double, Double>() {
      @Override
      public Double evaluate(final Double y) {
        return calculatePriceForZSpread(annuity, curves, y) - price;
      }
    };

    final double[] range = ROOT_BRACKETER.getBracketedPoints(f, 0.0, 1.2);
    return ROOT_FINDER.getRoot(f, range[0], range[1]);
  }

  public double calculatePriceForZSpread(final Annuity<? extends Payment> annuity, final YieldCurveBundle curves, final double zSpread) {
    Validate.notNull(annuity, "annuity");
    Validate.notNull(curves, "curves");

    double sum = 0;

    final int n = annuity.getNumberOfPayments();
    Payment payment;
    for (int i = 0; i < n; i++) {
      payment = annuity.getNthPayment(i);
      final double temp = PRESENT_VALUE_CALCULATOR.visit(payment, curves);
      sum += temp * Math.exp(-zSpread * payment.getPaymentTime());
    }
    return sum;
  }

  public double calculatePriceSensitivityToZSpread(final Annuity<? extends Payment> annuity, final YieldCurveBundle curves, final double zSpread) {
    Validate.notNull(annuity, "annuity");
    Validate.notNull(curves, "curves");

    double sum = 0;

    final int n = annuity.getNumberOfPayments();
    Payment payment;
    for (int i = 0; i < n; i++) {
      payment = annuity.getNthPayment(i);
      final double temp = PRESENT_VALUE_CALCULATOR.visit(payment, curves);
      final double time = payment.getPaymentTime();
      sum -= time * temp * Math.exp(-zSpread * time);
    }
    return sum;
  }

  public Map<String, List<DoublesPair>> calculatePriceSensitivityToCurve(final Annuity<? extends Payment> annuity, final YieldCurveBundle curves, final double zSpread) {
    Validate.notNull(annuity, "annuity");
    Validate.notNull(curves, "curves");

    final Map<String, List<DoublesPair>> temp = PV_SENSITIVITY_CALCULATOR.visit(annuity, curves);
    if (Double.doubleToLongBits(zSpread) == 0) {
      return temp;
    }
    final Map<String, List<DoublesPair>> result = new HashMap<String, List<DoublesPair>>();
    for (final Map.Entry<String, List<DoublesPair>> entry : result.entrySet()) {
      final List<DoublesPair> unadjusted = entry.getValue();
      final ArrayList<DoublesPair> adjusted = new ArrayList<DoublesPair>(unadjusted.size());
      for (final DoublesPair pair : unadjusted) {
        final DoublesPair newPair = new DoublesPair(pair.first, pair.second * Math.exp(-zSpread * pair.first));
        adjusted.add(newPair);
      }
      result.put(entry.getKey(), adjusted);
    }
    return result;
  }

  public Map<String, List<DoublesPair>> calculateZSpreadSensitivityToCurve(final Annuity<? extends Payment> annuity, final YieldCurveBundle curves, final double zSpread) {
    Validate.notNull(annuity, "annuity");
    Validate.notNull(curves, "curves");

    final double dPricedZ = calculatePriceSensitivityToZSpread(annuity, curves, zSpread);
    Validate.isTrue(Double.doubleToLongBits(dPricedZ) != 0, "Price Sensitivity To ZSpread is zero");

    final Map<String, List<DoublesPair>> result = new HashMap<String, List<DoublesPair>>();
    for (final Map.Entry<String, List<DoublesPair>> entry : result.entrySet()) {
      final List<DoublesPair> unadjusted = entry.getValue();
      final ArrayList<DoublesPair> adjusted = new ArrayList<DoublesPair>(unadjusted.size());
      for (final DoublesPair pair : unadjusted) {
        final DoublesPair newPair = new DoublesPair(pair.first, -pair.second * Math.exp(-zSpread * pair.first) / dPricedZ);
        adjusted.add(newPair);
      }
      result.put(entry.getKey(), adjusted);
    }
    return result;
  }
}
