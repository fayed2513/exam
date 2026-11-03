import java.util.*;

public class DUMSExam {
    enum Status { OPEN, IN_PROGRESS, RESOLVED }

    static final class Complaint {
        final String id, citizenId, serviceType, location;
        private Status status = Status.OPEN;

        Complaint(String id, String citizenId, String type, String location) {
            this.id = id;
            this.citizenId = citizenId;
            this.serviceType = type;
            this.location = location;
        }

        public Status getStatus() { return status; }
        void changeStatus(Status next) { status = next; }
    }

    static final class UrbanDatabase {
        private static final UrbanDatabase INSTANCE = new UrbanDatabase();
        private final Map<String, Complaint> complaints = new LinkedHashMap<>();

        private UrbanDatabase() { }

        public static UrbanDatabase getInstance() { return INSTANCE; }

        public void add(Complaint c) {
            if (complaints.containsKey(c.id))
                throw new IllegalArgumentException("Duplicate complaint ID");
            complaints.put(c.id, c);
        }

        public Complaint find(String id) {
            Complaint c = complaints.get(id);
            if (c == null) throw new IllegalArgumentException("Unknown complaint");
            return c;
        }
    }

    interface UrbanService {
        String assign(Complaint c);
    }

    static final class WasteService implements UrbanService {
        public String assign(Complaint c) { return "Waste team -> " + c.location; }
    }

    static final class TrafficService implements UrbanService {
        public String assign(Complaint c) { return "Traffic team -> " + c.location; }
    }

    static final class EmergencyService implements UrbanService {
        public String assign(Complaint c) { return "Emergency team -> " + c.location; }
    }

    static final class UrbanServiceFactory {
        public static UrbanService create(String type) {
            if ("WASTE".equals(type)) return new WasteService();
            if ("TRAFFIC".equals(type)) return new TrafficService();
            if ("EMERGENCY".equals(type)) return new EmergencyService();
            throw new IllegalArgumentException("Unsupported service type");
        }
    }

    interface ComplaintObserver {
        void update(Complaint c);
    }

    static final class CitizenNotifier implements ComplaintObserver {
        private final String citizenId;
        int received;

        CitizenNotifier(String id) { citizenId = id; }

        public void update(Complaint c) {
            if (!citizenId.equals(c.citizenId)) return;
            received++;
            System.out.println("Citizen " + citizenId + ": " + c.id + " " + c.getStatus());
        }
    }

    static final class GovernmentDashboard implements ComplaintObserver {
        int received;
        public void update(Complaint c) {
            received++;
            System.out.println("Dashboard: " + c.id + " " + c.getStatus());
        }
    }

    static final class StatusPublisher {
        private final List<ComplaintObserver> observers = new ArrayList<>();

        public void subscribe(ComplaintObserver o) {
            if (!observers.contains(o)) observers.add(o);
        }

        public void unsubscribe(ComplaintObserver o) { observers.remove(o); }

        public void publish(Complaint c) {
            for (ComplaintObserver o : new ArrayList<>(observers)) o.update(c);
        }
    }

    static final class Route {
        final String id;
        final double km, minutes;

        Route(String id, double km, double minutes) {
            if (!Double.isFinite(km) || !Double.isFinite(minutes) || km <= 0 || minutes <= 0)
                throw new IllegalArgumentException("Invalid route measurement");
            this.id = id;
            this.km = km;
            this.minutes = minutes;
        }
    }

    interface TrafficServiceAPI {
        List<Route> routes(String destination);
    }

    static final class ExternalTrafficAPI {
        public String[][] fetchCandidates(String destination) {
            if (destination == null || destination.trim().isEmpty())
                throw new IllegalArgumentException("Destination required");
            return new String[][] {
                    {"A", "5000", "1800"},
                    {"B", "8000", "1200"},
                    {"C", "6500", "1500"}
            };
        }
    }

    static final class TrafficAPIAdapter implements TrafficServiceAPI {
        private final ExternalTrafficAPI api;

        TrafficAPIAdapter(ExternalTrafficAPI api) { this.api = api; }

        public List<Route> routes(String destination) {
            List<Route> result = new ArrayList<>();
            for (String[] row : api.fetchCandidates(destination)) {
                result.add(new Route(row[0], Double.parseDouble(row[1]) / 1000.0, Double.parseDouble(row[2]) / 60.0));
            }
            return result;
        }
    }

    interface RouteStrategy {
        Route choose(List<Route> candidates);
    }

    static final class ShortestRouteStrategy implements RouteStrategy {
        public Route choose(List<Route> candidates) {
            return candidates.stream().min(Comparator.comparingDouble((Route r) -> r.km).thenComparing(r -> r.id))
                    .orElseThrow(() -> new IllegalArgumentException("No routes"));
        }
    }

    static final class FastestRouteStrategy implements RouteStrategy {
        public Route choose(List<Route> candidates) {
            return candidates.stream().min(Comparator.comparingDouble((Route r) -> r.minutes).thenComparing(r -> r.id))
                    .orElseThrow(() -> new IllegalArgumentException("No routes"));
        }
    }

    static final class RoutePlanner {
        private RouteStrategy strategy;
        private final TrafficServiceAPI traffic;

        RoutePlanner(RouteStrategy strategy, TrafficServiceAPI traffic) {
            this.strategy = strategy;
            this.traffic = traffic;
        }

        public void setStrategy(RouteStrategy strategy) { this.strategy = strategy; }

        public Route plan(String location) {
            return strategy.choose(traffic.routes(location));
        }
    }

    static final class ComplaintManager {
        private final UrbanDatabase db;
        private final StatusPublisher publisher;
        private final RoutePlanner planner;

        ComplaintManager(UrbanDatabase db, StatusPublisher publisher, RoutePlanner planner) {
            this.db = db;
            this.publisher = publisher;
            this.planner = planner;
        }

        public Complaint submit(String id, String citizen, String type, String location) {
            if (id == null || id.trim().isEmpty() || citizen == null || citizen.trim().isEmpty() || location == null || location.trim().isEmpty())
                throw new IllegalArgumentException("ID, citizen, and location required");

            UrbanServiceFactory.create(type);
            Complaint c = new Complaint(id, citizen, type, location);
            db.add(c);
            publisher.publish(c);
            return c;
        }

        public Route dispatch(String id) {
            Complaint c = db.find(id);
            if (c.getStatus() != Status.OPEN)
                throw new IllegalStateException("Only OPEN complaints can be dispatched");

            UrbanService service = UrbanServiceFactory.create(c.serviceType);
            Route route = planner.plan(c.location);
            String assignment = service.assign(c);
            c.changeStatus(Status.IN_PROGRESS);
            publisher.publish(c);
            System.out.println(assignment + "; route=" + route.id);
            return route;
        }

        public void resolve(String id) {
            Complaint c = db.find(id);
            if (c.getStatus() != Status.IN_PROGRESS)
                throw new IllegalStateException("Only IN_PROGRESS complaints can be resolved");
            c.changeStatus(Status.RESOLVED);
            publisher.publish(c);
        }
    }

    static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        System.out.println("PASS: " + label);
    }

    static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("PASS: " + label);
            return;
        }
        throw new AssertionError(label);
    }

    public static void main(String[] args) {
        UrbanDatabase db = UrbanDatabase.getInstance();
        expect(db == UrbanDatabase.getInstance(), "singleton identity");
        expect(UrbanServiceFactory.create("WASTE") instanceof WasteService, "waste factory");
        expect(UrbanServiceFactory.create("TRAFFIC") instanceof TrafficService, "traffic factory");
        expect(UrbanServiceFactory.create("EMERGENCY") instanceof EmergencyService, "emergency factory");
        rejects(() -> UrbanServiceFactory.create("UNKNOWN"), "unsupported service");

        TrafficServiceAPI traffic = new TrafficAPIAdapter(new ExternalTrafficAPI());
        List<Route> routes = traffic.routes("Mirpur");
        expect(routes.get(0).km == 5.0 && routes.get(0).minutes == 30.0, "adapter units");

        RoutePlanner planner = new RoutePlanner(new ShortestRouteStrategy(), traffic);
        expect("A".equals(planner.plan("Mirpur").id), "shortest chooses A");

        planner.setStrategy(new FastestRouteStrategy());
        expect("B".equals(planner.plan("Mirpur").id), "fastest chooses B");
        rejects(() -> new ShortestRouteStrategy().choose(Collections.emptyList()), "empty route list");

        StatusPublisher publisher = new StatusPublisher();
        CitizenNotifier citizen = new CitizenNotifier("U1");
        CitizenNotifier other = new CitizenNotifier("U2");
        GovernmentDashboard dashboard = new GovernmentDashboard();

        publisher.subscribe(citizen);
        publisher.subscribe(other);
        publisher.subscribe(dashboard);

        ComplaintManager manager = new ComplaintManager(db, publisher, planner);
        Complaint c = manager.submit("C1", "U1", "WASTE", "Mirpur");
        expect(c.getStatus() == Status.OPEN, "submission OPEN");
        expect(citizen.received == 1 && dashboard.received == 1 && other.received == 0, "subscribers and citizen filtering");

        rejects(() -> manager.submit("C1", "U1", "WASTE", "Mirpur"), "duplicate ID");
        rejects(() -> manager.submit("", "U1", "WASTE", "Mirpur"), "blank ID");
        rejects(() -> manager.resolve("C1"), "cannot resolve OPEN complaint");

        Route chosen = manager.dispatch("C1");
        expect("B".equals(chosen.id) && c.getStatus() == Status.IN_PROGRESS, "dispatch fastest and change status");
        expect(citizen.received == 2 && dashboard.received == 2, "dispatch notifications");

        publisher.unsubscribe(dashboard);
        manager.resolve("C1");
        expect(c.getStatus() == Status.RESOLVED && citizen.received == 3 && dashboard.received == 2, "resolve and unsubscription");

        rejects(() -> manager.dispatch("C1"), "cannot redispatch RESOLVED complaint");
        rejects(() -> manager.dispatch("missing"), "unknown complaint ID");

        RoutePlanner emptyPlanner = new RoutePlanner(new FastestRouteStrategy(), destination -> Collections.emptyList());
        ComplaintManager noRoute = new ComplaintManager(db, publisher, emptyPlanner);
        Complaint c2 = noRoute.submit("C2", "U1", "TRAFFIC", "Dhanmondi");
        int before = citizen.received;

        rejects(() -> noRoute.dispatch("C2"), "no-route dispatch");
        expect(c2.getStatus() == Status.OPEN && citizen.received == before, "no-route preserves OPEN");

        System.out.println("All checks completed.");
    }
}