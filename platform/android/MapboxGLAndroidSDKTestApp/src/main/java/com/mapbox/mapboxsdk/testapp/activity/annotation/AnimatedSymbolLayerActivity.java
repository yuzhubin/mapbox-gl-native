package com.mapbox.mapboxsdk.testapp.activity.annotation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.google.gson.JsonObject;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.style.functions.stops.Stops;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.testapp.R;
import com.mapbox.services.api.utils.turf.TurfMeasurement;
import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.FeatureCollection;
import com.mapbox.services.commons.geojson.Point;
import com.mapbox.services.commons.models.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.mapbox.mapboxsdk.style.functions.Function.property;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconRotate;

/**
 * Test activity showcasing animating MarkerViews.
 */
public class AnimatedSymbolLayerActivity extends AppCompatActivity {

  private static final String RANDOM_CAR_SOURCE = "random-car-source";
  private static final String PASSENGER_SOURCE = "passenger-source";
  private static final String TAXI_SOURCE = "taxi-source";

  private static final String RANDOM_CAR_LAYER = "random-car-layer";

  private static final String PASSENGER = "passenger";
  private static final String TAXI = "taxi";

  private MapView mapView;
  private MapboxMap mapboxMap;

  private List<Car> randomCars = new ArrayList<>();
  private GeoJsonSource randomCarSource;

  private Car taxi;
  private GeoJsonSource taxiSource;

  private LatLng passenger;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_animated_marker);

    mapView = (MapView) findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(new OnMapReadyCallback() {

      @Override
      public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        AnimatedSymbolLayerActivity.this.mapboxMap = mapboxMap;
        setupCars();
        animateRandomRoutes();
        animateTaxi();
      }
    });
  }

  private void setupCars() {
    addRandomCars();
    addPassenger();
    addMainCar();
  }

  private void animateRandomRoutes() {
    final Car longestDrive = getLongestDrive();
    final Random random = new Random();
    for (final Car car : randomCars) {
      final boolean isLongestDrive = longestDrive.equals(car);
      ValueAnimator valueAnimator = ValueAnimator.ofObject(new LatLngEvaluator(), car.current, car.next);
      valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

        private LatLng latLng;

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
          latLng = (LatLng) animation.getAnimatedValue();
          car.current = latLng;
          if (isLongestDrive) {
            updateRandomCarSource();
          }
        }
      });

      if (isLongestDrive) {
        valueAnimator.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            updateRandomDestinations();
            animateRandomRoutes();
          }
        });
      }

      valueAnimator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
          super.onAnimationStart(animation);
          car.feature.getProperties().addProperty("bearing", getBearing(car.current, car.next));
        }
      });

      int offset = random.nextInt(2) == 0 ? 0 : random.nextInt(1000) + 250;
      valueAnimator.setStartDelay(offset);
      valueAnimator.setDuration(car.duration - offset);
      valueAnimator.setInterpolator(new LinearInterpolator());
      valueAnimator.start();
    }
  }

  private void animateTaxi() {
    ValueAnimator valueAnimator = ValueAnimator.ofObject(new LatLngEvaluator(), taxi.current, taxi.next);
    valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

      private LatLng latLng;

      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        latLng = (LatLng) animation.getAnimatedValue();
        taxi.current = latLng;
        updateTaxiSource();
      }
    });

    valueAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        super.onAnimationEnd(animation);
        updatePassenger();
        animateTaxi();
      }
    });

    valueAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        super.onAnimationStart(animation);
        taxi.feature.getProperties().addProperty("bearing", getBearing(taxi.current, taxi.next));
      }
    });

    valueAnimator.setDuration((long) (7 * taxi.current.distanceTo(taxi.next)));
    valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
    valueAnimator.start();
  }

  private void updatePassenger() {
    passenger = getLatLngInBounds();
    updatePassengerSource();
    taxi.setNext(passenger);
  }

  private void updatePassengerSource() {
    GeoJsonSource source = mapboxMap.getSourceAs(PASSENGER_SOURCE);
    FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[] {
      Feature.fromGeometry(
        Point.fromCoordinates(
          Position.fromCoordinates(
            passenger.getLongitude(),
            passenger.getLatitude()
          )
        )
      )
    });
    source.setGeoJson(featureCollection);
  }

  private void updateTaxiSource() {
    taxi.feature.setGeometry(Point.fromCoordinates(Position.fromCoordinates(
      taxi.current.getLongitude(),
      taxi.current.getLatitude())
    ));
    taxiSource.setGeoJson(taxi.feature);
  }

  private void updateRandomDestinations() {
    for (Car randomCar : randomCars) {
      randomCar.setNext(getLatLngInBounds());
    }
  }

  private Car getLongestDrive() {
    Car longestDrive = null;
    for (Car randomCar : randomCars) {
      if (longestDrive == null) {
        longestDrive = randomCar;
      } else if (longestDrive.duration < randomCar.duration) {
        longestDrive = randomCar;
      }
    }
    return longestDrive;
  }

  private void updateRandomCarSource() {
    for (Car randomCarsRoute : randomCars) {
      randomCarsRoute.feature.setGeometry(Point.fromCoordinates(Position.fromCoordinates(
        randomCarsRoute.current.getLongitude(),
        randomCarsRoute.current.getLatitude())
      ));
    }
    randomCarSource.setGeoJson(featuresFromRoutes());
  }

  private FeatureCollection featuresFromRoutes() {
    List<Feature> features = new ArrayList<>();
    for (Car randomCarsRoute : randomCars) {
      features.add(randomCarsRoute.feature);
    }
    return FeatureCollection.fromFeatures(features);
  }

  private void addRandomCars() {
    LatLng latLng;
    LatLng next;
    for (int i = 0; i < 10; i++) {
      latLng = getLatLngInBounds();
      next = getLatLngInBounds();

      JsonObject properties = new JsonObject();
      properties.addProperty("bearing", getBearing(latLng, next));

      Feature feature = Feature.fromGeometry(
        Point.fromCoordinates(
          Position.fromCoordinates(
            latLng.getLongitude(),
            latLng.getLatitude()
          )
        ), properties);

      randomCars.add(
        new Car(feature, next)
      );
    }

    randomCarSource = new GeoJsonSource(RANDOM_CAR_SOURCE, featuresFromRoutes());
    mapboxMap.addSource(randomCarSource);
    mapboxMap.addImage("random-car",
      ((BitmapDrawable) getResources().getDrawable(R.drawable.ic_car_top)).getBitmap());

    SymbolLayer symbolLayer = new SymbolLayer(RANDOM_CAR_LAYER, RANDOM_CAR_SOURCE);
    symbolLayer.withProperties(
      iconImage("random-car"),
      iconAllowOverlap(false),
      iconRotate(
        property(
          "bearing",
          Stops.<Float>identity()
        )
      ),
      iconIgnorePlacement(true)
    );

    mapboxMap.addLayerBelow(symbolLayer, "waterway-label");
  }

  private void addPassenger() {
    passenger = getLatLngInBounds();
    FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[] {
      Feature.fromGeometry(
        Point.fromCoordinates(
          Position.fromCoordinates(
            passenger.getLongitude(),
            passenger.getLatitude()
          )
        )
      )
    });

    mapboxMap.addImage(PASSENGER,
      ((BitmapDrawable) getResources().getDrawable(R.drawable.icon_burned)).getBitmap());

    GeoJsonSource geoJsonSource = new GeoJsonSource(PASSENGER_SOURCE, featureCollection);
    mapboxMap.addSource(geoJsonSource);

    SymbolLayer symbolLayer = new SymbolLayer("passenger-layer", PASSENGER_SOURCE);
    symbolLayer.withProperties(
      iconImage(PASSENGER)
    );
    mapboxMap.addLayerBelow(symbolLayer, RANDOM_CAR_LAYER);
  }

  private void addMainCar() {
    LatLng latLng = getLatLngInBounds();
    JsonObject properties = new JsonObject();
    properties.addProperty("bearing", getBearing(latLng, passenger));
    Feature feature = Feature.fromGeometry(
      Point.fromCoordinates(
        Position.fromCoordinates(
          latLng.getLongitude(),
          latLng.getLatitude()))
      , properties);
    FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[] {feature});

    taxi = new Car(feature, passenger);
    mapboxMap.addImage(TAXI,
      ((BitmapDrawable) getResources().getDrawable(R.drawable.ic_taxi_top)).getBitmap());
    taxiSource = new GeoJsonSource(TAXI_SOURCE, featureCollection);
    mapboxMap.addSource(taxiSource);

    SymbolLayer symbolLayer = new SymbolLayer("taxi-layer", TAXI_SOURCE);
    symbolLayer.withProperties(
      iconImage(TAXI),
      iconRotate(
        property(
          "bearing",
          Stops.<Float>identity()
        )
      )
    );
    mapboxMap.addLayer(symbolLayer);
  }

  private LatLng getLatLngInBounds() {
    LatLngBounds bounds = mapboxMap.getProjection().getVisibleRegion().latLngBounds;
    Random generator = new Random();
    double randomLat = bounds.getLatSouth() + generator.nextDouble()
      * (bounds.getLatNorth() - bounds.getLatSouth());
    double randomLon = bounds.getLonWest() + generator.nextDouble()
      * (bounds.getLonEast() - bounds.getLonWest());
    return new LatLng(randomLat, randomLon);
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  /**
   * Evaluator for LatLng pairs
   */
  private static class LatLngEvaluator implements TypeEvaluator<LatLng> {

    private LatLng latLng = new LatLng();

    @Override
    public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
      latLng.setLatitude(startValue.getLatitude()
        + ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
      latLng.setLongitude(startValue.getLongitude()
        + ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
      return latLng;
    }

  }

  private float getBearing(LatLng from, LatLng to) {
    return (float) TurfMeasurement.bearing(
      Position.fromCoordinates(from.getLongitude(), from.getLatitude()),
      Position.fromCoordinates(to.getLongitude(), to.getLatitude())
    );
  }

  private class Car {
    private Feature feature;
    private LatLng next;
    private LatLng current;
    private long duration;

    Car(Feature feature, LatLng next) {
      this.feature = feature;
      Position point = ((Point) feature.getGeometry()).getCoordinates();
      this.current = new LatLng(point.getLatitude(), point.getLongitude());
      this.duration = 4500;
      this.next = next;
    }

    void setNext(LatLng next) {
      this.next = next;
      this.duration = 4500;
    }
  }
}
