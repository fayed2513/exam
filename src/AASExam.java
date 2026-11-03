import java.util.*;

public class AASExam {

    static final class Field {
        final String id, cropType;
        Field(String id, String cropType) {
            this.id = id;
            this.cropType = cropType;
        }
    }

    static final class FarmDataManager {
        private static final FarmDataManager INSTANCE = new FarmDataManager();
        private final Map<String, Field> fields = new LinkedHashMap<>();
        private FarmDataManager() { }
        static FarmDataManager getInstance() { return INSTANCE; }
        void save(Field field) { fields.put(field.id, field); }
        Field find(String id) { return fields.get(id); }
    }

    interface Sensor {
        String type();
        double read();
    }

    static final class SoilMoistureSensor implements Sensor {
        private final double value;
        SoilMoistureSensor(double value) { this.value = value; }
        public String type() { return "moisture"; }
        public double read() { return value; }
    }

    static final class TemperatureSensor implements Sensor {
        private final double value;
        TemperatureSensor(double value) { this.value = value; }
        public String type() { return "temperature"; }
        public double read() { return value; }
    }

    static final class SensorFactory {
        static Sensor create(String type, double value) {
            if ("moisture".equalsIgnoreCase(type)) return new SoilMoistureSensor(value);
            if ("temperature".equalsIgnoreCase(type)) return new TemperatureSensor(value);
            throw new IllegalArgumentException("Unsupported sensor: " + type);
        }
    }

    static final class WeatherData {
        final boolean rainExpected;
        WeatherData(boolean rainExpected) { this.rainExpected = rainExpected; }
    }

    interface WeatherService {
        WeatherData current();
    }

    static final class ExternalWeatherAPI {
        private final String forecast;
        ExternalWeatherAPI(String forecast) { this.forecast = forecast; }
        String fetchForecastCode() { return forecast; }
    }

    static final class WeatherAPIAdapter implements WeatherService {
        private final ExternalWeatherAPI api;
        WeatherAPIAdapter(ExternalWeatherAPI api) { this.api = api; }
        public WeatherData current() {
            String code = api.fetchForecastCode();
            if ("RAIN".equals(code)) return new WeatherData(true);
            if ("CLEAR".equals(code)) return new WeatherData(false);
            throw new IllegalArgumentException("Unknown weather code: " + code);
        }
    }

    interface IrrigationStrategy {
        boolean shouldIrrigate(double moisture, WeatherData weather);
    }

    static final class NormalIrrigationStrategy implements IrrigationStrategy {
        public boolean shouldIrrigate(double m, WeatherData w) {
            return m < 40 && !w.rainExpected;
        }
    }

    static final class WaterSavingStrategy implements IrrigationStrategy {
        public boolean shouldIrrigate(double m, WeatherData w) {
            return m < 25 && !w.rainExpected;
        }
    }

    interface AlertObserver {
        void update(String message);
    }

    static final class FarmerNotifier implements AlertObserver {
        int received;
        public void update(String message) {
            received++;
            System.out.println("Farmer: " + message);
        }
    }

    static final class FarmDashboard implements AlertObserver {
        int received;
        public void update(String message) {
            received++;
            System.out.println("Dashboard: " + message);
        }
    }

    static final class AlertPublisher {
        private final List<AlertObserver> observers = new ArrayList<>();
        void subscribe(AlertObserver o) {
            if (!observers.contains(o)) observers.add(o);
        }
        void unsubscribe(AlertObserver o) { observers.remove(o); }
        void publish(String message) {
            for (AlertObserver o : new ArrayList<>(observers)) o.update(message);
        }
    }

    static final class IrrigationPump {
        private boolean on;
        void setOn(boolean on) { this.on = on; }
        boolean isOn() { return on; }
    }

    static final class IrrigationController {
        private IrrigationStrategy strategy;
        private final WeatherService weather;
        private final IrrigationPump pump;
        private final AlertPublisher alerts;

        IrrigationController(IrrigationStrategy s, WeatherService w, IrrigationPump p, AlertPublisher a) {
            strategy = s; weather = w; pump = p; alerts = a;
        }

        void setStrategy(IrrigationStrategy s) { strategy = s; }

        boolean check(Sensor sensor) {
            pump.setOn(false);
            if (!"moisture".equals(sensor.type()))
                throw new IllegalArgumentException("Moisture sensor required");
            double m = sensor.read();
            if (!Double.isFinite(m) || m < 0 || m > 100) {
                alerts.publish("Invalid moisture; pump OFF");
                throw new IllegalArgumentException("Moisture must be 0 to 100");
            }
            WeatherData w = weather.current();
            boolean run = strategy.shouldIrrigate(m, w);
            pump.setOn(run);
            alerts.publish("Moisture=" + m + "; pump " + (run ? "ON" : "OFF"));
            return run;
        }
    }

    static void expect(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS: " + name);
    }

    static void checkCase(IrrigationController c, IrrigationPump p, double m, boolean expected, String label) {
        boolean result = c.check(SensorFactory.create("moisture", m));
        expect(result == expected && p.isOn() == expected, label);
    }

    public static void main(String[] args) {
        FarmDataManager data = FarmDataManager.getInstance();
        data.save(new Field("F1", "Rice"));
        expect(data == FarmDataManager.getInstance(), "singleton identity");
        expect("Rice".equals(data.find("F1").cropType), "stored field");

        expect(SensorFactory.create("moisture", 30) instanceof SoilMoistureSensor, "factory moisture");
        expect(SensorFactory.create("temperature", 28) instanceof TemperatureSensor, "factory temperature");

        try {
            SensorFactory.create("unknown", 0);
            throw new AssertionError("Unsupported type was accepted");
        } catch (IllegalArgumentException e) {
            System.out.println("PASS: unsupported sensor rejected");
        }

        AlertPublisher alerts = new AlertPublisher();
        FarmerNotifier farmer = new FarmerNotifier();
        FarmDashboard dashboard = new FarmDashboard();
        alerts.subscribe(farmer);
        alerts.subscribe(dashboard);
        alerts.publish("Weather alert");

        expect(farmer.received == 1 && dashboard.received == 1, "two observers notified");

        alerts.unsubscribe(dashboard);
        alerts.publish("Second alert");
        expect(farmer.received == 2 && dashboard.received == 1, "unsubscription");

        alerts.subscribe(dashboard);
        IrrigationPump pump = new IrrigationPump();
        WeatherService clear = new WeatherAPIAdapter(new ExternalWeatherAPI("CLEAR"));
        IrrigationController c = new IrrigationController(new NormalIrrigationStrategy(), clear, pump, alerts);

        checkCase(c, pump, 30, true, "normal 30 clear");
        c.setStrategy(new WaterSavingStrategy());
        checkCase(c, pump, 30, false, "water saving 30 clear");
        checkCase(c, pump, 25, false, "water saving boundary 25");
        checkCase(c, pump, 20, true, "water saving 20 clear");

        c.setStrategy(new NormalIrrigationStrategy());
        checkCase(c, pump, 40, false, "normal boundary 40");

        WeatherService rain = new WeatherAPIAdapter(new ExternalWeatherAPI("RAIN"));
        IrrigationController wet = new IrrigationController(new NormalIrrigationStrategy(), rain, pump, alerts);
        checkCase(wet, pump, 20, false, "normal 20 rain");

        wet.setStrategy(new WaterSavingStrategy());
        checkCase(wet, pump, 20, false, "water saving 20 rain");

        for (double invalid : new double[] {-5, 105, Double.NaN}) {
            pump.setOn(true);
            try {
                c.check(SensorFactory.create("moisture", invalid));
                throw new AssertionError("Invalid reading was accepted");
            } catch (IllegalArgumentException e) {
                expect(!pump.isOn(), "invalid reading rejected with pump OFF");
            }
        }

        System.out.println("All checks completed.");
    }
}