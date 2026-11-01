package com.drivesphere.integration.maps;

import com.drivesphere.dto.TripPlannerResponse.PoiDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Client for OpenRouteService, OpenStreetMap Nominatim Geocoding, and OSRM Routing.
 * Implements authoritative location resolution, actual highway routing, and distance matrix.
 */
@Slf4j
@Component
public class MapsClient {

    @Value("${drivesphere.openrouteservice.api-key:}")
    private String orsApiKey;

    private HttpClient httpClient;

    private synchronized HttpClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return this.httpClient;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MapsClient() {}

    public MapsClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public MapsClient(String orsApiKey, HttpClient httpClient) {
        this.orsApiKey = orsApiKey;
        this.httpClient = httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setOrsApiKey(String orsApiKey) {
        this.orsApiKey = orsApiKey;
    }

    @Data
    @Builder
    public static class GeocodeResult {
        private double lat;
        private double lng;
        private String displayName;
    }

    @Data
    @Builder
    public static class RouteCalculationResult {
        private BigDecimal distanceKm;
        private int durationMinutes;
        private List<List<Double>> coordinates; // [[lat, lng], ...]
        private boolean isFallback;
        private String provider;
    }

    // Static verified directory of major regional hubs for instant, zero-latency resolution
    private static final Map<String, GeocodeResult> VERIFIED_CITIES = new HashMap<>();

    static {
        VERIFIED_CITIES.put("bangalore", GeocodeResult.builder().lat(12.9716).lng(77.5946).displayName("Bengaluru, Karnataka, India").build());
        VERIFIED_CITIES.put("bengaluru", GeocodeResult.builder().lat(12.9716).lng(77.5946).displayName("Bengaluru, Karnataka, India").build());
        VERIFIED_CITIES.put("mysore", GeocodeResult.builder().lat(12.2958).lng(76.6394).displayName("Mysuru, Karnataka, India").build());
        VERIFIED_CITIES.put("mysuru", GeocodeResult.builder().lat(12.2958).lng(76.6394).displayName("Mysuru, Karnataka, India").build());
        VERIFIED_CITIES.put("chennai", GeocodeResult.builder().lat(13.0827).lng(80.2707).displayName("Chennai, Tamil Nadu, India").build());
        VERIFIED_CITIES.put("hyderabad", GeocodeResult.builder().lat(17.3850).lng(78.4867).displayName("Hyderabad, Telangana, India").build());
        VERIFIED_CITIES.put("ooty", GeocodeResult.builder().lat(11.4102).lng(76.6950).displayName("Udhagamandalam (Ooty), Tamil Nadu, India").build());
        VERIFIED_CITIES.put("coorg", GeocodeResult.builder().lat(12.4244).lng(75.7382).displayName("Madikeri, Coorg, Karnataka, India").build());
        VERIFIED_CITIES.put("madikeri", GeocodeResult.builder().lat(12.4244).lng(75.7382).displayName("Madikeri, Coorg, Karnataka, India").build());
        VERIFIED_CITIES.put("mumbai", GeocodeResult.builder().lat(19.0760).lng(72.8777).displayName("Mumbai, Maharashtra, India").build());
        VERIFIED_CITIES.put("pune", GeocodeResult.builder().lat(18.5204).lng(73.8567).displayName("Pune, Maharashtra, India").build());
        VERIFIED_CITIES.put("kolhapur", GeocodeResult.builder().lat(16.7050).lng(74.2433).displayName("Kolhapur, Maharashtra, India").build());
        VERIFIED_CITIES.put("satara", GeocodeResult.builder().lat(17.6805).lng(73.9997).displayName("Satara, Maharashtra, India").build());
        VERIFIED_CITIES.put("solapur", GeocodeResult.builder().lat(17.6599).lng(75.9064).displayName("Solapur, Maharashtra, India").build());
        VERIFIED_CITIES.put("nashik", GeocodeResult.builder().lat(19.9975).lng(73.7898).displayName("Nashik, Maharashtra, India").build());
        VERIFIED_CITIES.put("nagpur", GeocodeResult.builder().lat(21.1458).lng(79.0882).displayName("Nagpur, Maharashtra, India").build());
        VERIFIED_CITIES.put("aurangabad", GeocodeResult.builder().lat(19.8762).lng(75.3433).displayName("Chhatrapati Sambhajinagar (Aurangabad), Maharashtra, India").build());
        VERIFIED_CITIES.put("sambhajinagar", GeocodeResult.builder().lat(19.8762).lng(75.3433).displayName("Chhatrapati Sambhajinagar (Aurangabad), Maharashtra, India").build());
        VERIFIED_CITIES.put("goa", GeocodeResult.builder().lat(15.4909).lng(73.8278).displayName("Panaji, Goa, India").build());
        VERIFIED_CITIES.put("panaji", GeocodeResult.builder().lat(15.4909).lng(73.8278).displayName("Panaji, Goa, India").build());
        VERIFIED_CITIES.put("delhi", GeocodeResult.builder().lat(28.6139).lng(77.2090).displayName("New Delhi, Delhi, India").build());
        VERIFIED_CITIES.put("mangalore", GeocodeResult.builder().lat(12.9141).lng(74.8560).displayName("Mangaluru, Karnataka, India").build());
        VERIFIED_CITIES.put("mangaluru", GeocodeResult.builder().lat(12.9141).lng(74.8560).displayName("Mangaluru, Karnataka, India").build());
        VERIFIED_CITIES.put("kochi", GeocodeResult.builder().lat(9.9312).lng(76.2673).displayName("Kochi, Kerala, India").build());
        VERIFIED_CITIES.put("coimbatore", GeocodeResult.builder().lat(11.0168).lng(76.9558).displayName("Coimbatore, Tamil Nadu, India").build());
        VERIFIED_CITIES.put("hubli", GeocodeResult.builder().lat(15.3647).lng(75.1240).displayName("Hubballi, Karnataka, India").build());
        VERIFIED_CITIES.put("hubballi", GeocodeResult.builder().lat(15.3647).lng(75.1240).displayName("Hubballi, Karnataka, India").build());
        VERIFIED_CITIES.put("belgaum", GeocodeResult.builder().lat(15.8497).lng(74.4977).displayName("Belagavi (Belgaum), Karnataka, India").build());
        VERIFIED_CITIES.put("belagavi", GeocodeResult.builder().lat(15.8497).lng(74.4977).displayName("Belagavi (Belgaum), Karnataka, India").build());
        VERIFIED_CITIES.put("hassan", GeocodeResult.builder().lat(13.0033).lng(76.1004).displayName("Hassan, Karnataka, India").build());
        VERIFIED_CITIES.put("chikmagalur", GeocodeResult.builder().lat(13.3161).lng(75.7720).displayName("Chikkamagaluru, Karnataka, India").build());
        VERIFIED_CITIES.put("shimoga", GeocodeResult.builder().lat(13.9299).lng(75.5681).displayName("Shivamogga, Karnataka, India").build());
        VERIFIED_CITIES.put("ahmedabad", GeocodeResult.builder().lat(23.0225).lng(72.5714).displayName("Ahmedabad, Gujarat, India").build());
        // Platform Mobility Hubs & Airport Locations
        VERIFIED_CITIES.put("downtown hub", GeocodeResult.builder().lat(12.9716).lng(77.5946).displayName("Downtown Hub, Bangalore").build());
        VERIFIED_CITIES.put("downtown", GeocodeResult.builder().lat(12.9716).lng(77.5946).displayName("Downtown Hub, Bangalore").build());
        VERIFIED_CITIES.put("airport terminal", GeocodeResult.builder().lat(13.1986).lng(77.7066).displayName("Kempegowda International Airport Terminal, Bangalore").build());
        VERIFIED_CITIES.put("airport expressway hub", GeocodeResult.builder().lat(13.1986).lng(77.7066).displayName("Airport Expressway Hub, Bangalore").build());
        VERIFIED_CITIES.put("airport", GeocodeResult.builder().lat(13.1986).lng(77.7066).displayName("Kempegowda International Airport Terminal, Bangalore").build());
        VERIFIED_CITIES.put("mg road terminal", GeocodeResult.builder().lat(12.9756).lng(77.6066).displayName("MG Road Terminal, Bangalore").build());
        VERIFIED_CITIES.put("mg road", GeocodeResult.builder().lat(12.9756).lng(77.6066).displayName("MG Road Terminal, Bangalore").build());
        VERIFIED_CITIES.put("koramangala hub", GeocodeResult.builder().lat(12.9352).lng(77.6245).displayName("Koramangala Hub, Bangalore").build());
        VERIFIED_CITIES.put("koramangala", GeocodeResult.builder().lat(12.9352).lng(77.6245).displayName("Koramangala Hub, Bangalore").build());
        VERIFIED_CITIES.put("indiranagar hub", GeocodeResult.builder().lat(12.9784).lng(77.6408).displayName("Indiranagar Hub, Bangalore").build());
        VERIFIED_CITIES.put("indiranagar", GeocodeResult.builder().lat(12.9784).lng(77.6408).displayName("Indiranagar Hub, Bangalore").build());
        VERIFIED_CITIES.put("whitefield station", GeocodeResult.builder().lat(12.9698).lng(77.7499).displayName("Whitefield Station, Bangalore").build());
        VERIFIED_CITIES.put("whitefield", GeocodeResult.builder().lat(12.9698).lng(77.7499).displayName("Whitefield Station, Bangalore").build());
        VERIFIED_CITIES.put("jayanagar hub", GeocodeResult.builder().lat(12.9308).lng(77.5838).displayName("Jayanagar Hub, Bangalore").build());
        VERIFIED_CITIES.put("jayanagar", GeocodeResult.builder().lat(12.9308).lng(77.5838).displayName("Jayanagar Hub, Bangalore").build());
        VERIFIED_CITIES.put("malleshwaram hub", GeocodeResult.builder().lat(13.0031).lng(77.5643).displayName("Malleshwaram Hub, Bangalore").build());
        VERIFIED_CITIES.put("malleshwaram", GeocodeResult.builder().lat(13.0031).lng(77.5643).displayName("Malleshwaram Hub, Bangalore").build());
        VERIFIED_CITIES.put("ub city luxury concierge", GeocodeResult.builder().lat(12.9716).lng(77.5957).displayName("UB City Luxury Concierge, Bangalore").build());
        VERIFIED_CITIES.put("ub city", GeocodeResult.builder().lat(12.9716).lng(77.5957).displayName("UB City Luxury Concierge, Bangalore").build());
        VERIFIED_CITIES.put("hsr layout hub", GeocodeResult.builder().lat(12.9121).lng(77.6446).displayName("HSR Layout Hub, Bangalore").build());
        VERIFIED_CITIES.put("hsr layout", GeocodeResult.builder().lat(12.9121).lng(77.6446).displayName("HSR Layout Hub, Bangalore").build());
        VERIFIED_CITIES.put("electronic city terminal", GeocodeResult.builder().lat(12.8399).lng(77.6770).displayName("Electronic City Terminal, Bangalore").build());
        VERIFIED_CITIES.put("electronic city", GeocodeResult.builder().lat(12.8399).lng(77.6770).displayName("Electronic City Terminal, Bangalore").build());
        VERIFIED_CITIES.put("mysore palace road", GeocodeResult.builder().lat(12.3051).lng(76.6551).displayName("Mysore Palace Road, Mysuru").build());
        VERIFIED_CITIES.put("mysore palace", GeocodeResult.builder().lat(12.3051).lng(76.6551).displayName("Mysore Palace Road, Mysuru").build());
    }

    public boolean isConfigured() {
        return orsApiKey != null && !orsApiKey.isBlank() && !orsApiKey.equalsIgnoreCase("YOUR_ORS_API_KEY");
    }

    /**
     * Authoritative Geocoding: resolves location string into verified coordinates.
     * Uses local verified hub directory first, then queries live OpenStreetMap Nominatim.
     * Throws IllegalArgumentException if location is invalid/unresolvable.
     */
    public GeocodeResult geocode(String query) {
        if (query == null || query.trim().isBlank()) {
            throw new IllegalArgumentException("Location query cannot be blank.");
        }

        String normalized = query.trim().toLowerCase();
        
        // 1. Exact match check
        if (VERIFIED_CITIES.containsKey(normalized)) {
            return VERIFIED_CITIES.get(normalized);
        }

        // 2. Substring match against verified hubs (sorted by length descending to match specific hubs first)
        List<String> sortedKeys = VERIFIED_CITIES.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        for (String key : sortedKeys) {
            if (normalized.contains(key)) {
                return VERIFIED_CITIES.get(key);
            }
        }

        // 2. Query OpenStreetMap Nominatim API with User-Agent
        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "DriveSphere-Mobility-Platform/1.0 (support@drivesphere.com)")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    double lat = Double.parseDouble(first.path("lat").asText());
                    double lon = Double.parseDouble(first.path("lon").asText());
                    String name = first.path("display_name").asText(query);
                    return GeocodeResult.builder().lat(lat).lng(lon).displayName(name).build();
                }
            }
        } catch (Exception e) {
            log.warn("Nominatim geocoding failed for '{}': {}", query, e.getMessage());
        }

        // 3. If geocoding failed, reject the location cleanly with requirement-specified wording
        throw new IllegalArgumentException("I couldn't locate '" + query + "'. Please provide a more specific location.");
    }

    /**
     * Calculate route distance, duration, and coordinate waypoints between start and destination.
     * Queries ORS, then OSRM, then realistic geodesic highway calculation if offline.
     */
    public RouteCalculationResult calculateRoute(double startLat, double startLng, double destLat, double destLng) {
        // Option 1: OpenRouteService (if configured)
        if (isConfigured()) {
            try {
                String url = String.format(Locale.US,
                        "https://api.openrouteservice.org/v2/directions/driving-car?api_key=%s&start=%f,%f&end=%f,%f",
                        orsApiKey, startLng, startLat, destLng, destLat
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode features = root.path("features");
                    if (features.isArray() && !features.isEmpty()) {
                        JsonNode feature = features.get(0);
                        double distanceMeters = feature.path("properties").path("summary").path("distance").asDouble(0.0);
                        double durationSeconds = feature.path("properties").path("summary").path("duration").asDouble(0.0);

                        BigDecimal distKm = BigDecimal.valueOf(distanceMeters / 1000.0).setScale(2, RoundingMode.HALF_UP);
                        int durMin = (int) Math.round(durationSeconds / 60.0);

                        List<List<Double>> coords = new ArrayList<>();
                        JsonNode coordsNode = feature.path("geometry").path("coordinates");
                        if (coordsNode.isArray()) {
                            for (JsonNode pt : coordsNode) {
                                coords.add(List.of(pt.get(1).asDouble(), pt.get(0).asDouble()));
                            }
                        }

                        if (!coords.isEmpty()) {
                            return RouteCalculationResult.builder()
                                    .distanceKm(distKm)
                                    .durationMinutes(durMin)
                                    .coordinates(coords)
                                    .isFallback(false)
                                    .provider("OPENROUTESERVICE")
                                    .build();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("ORS API call failed: {}. Trying OSRM public router...", e.getMessage());
            }
        }

        // Option 2: OSRM Public Routing Service (Zero-Config, Real Highway Geometry)
        try {
            String osrmUrl = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                    startLng, startLat, destLng, destLat
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(osrmUrl))
                    .header("User-Agent", "DriveSphere-Mobility-Platform/1.0")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode routes = root.path("routes");
                if (routes.isArray() && !routes.isEmpty()) {
                    JsonNode primaryRoute = routes.get(0);
                    double distanceMeters = primaryRoute.path("distance").asDouble(0.0);
                    double durationSeconds = primaryRoute.path("duration").asDouble(0.0);

                    BigDecimal distKm = BigDecimal.valueOf(distanceMeters / 1000.0).setScale(2, RoundingMode.HALF_UP);
                    int durMin = (int) Math.round(durationSeconds / 60.0);

                    List<List<Double>> coords = new ArrayList<>();
                    JsonNode coordsNode = primaryRoute.path("geometry").path("coordinates");
                    if (coordsNode.isArray()) {
                        for (JsonNode pt : coordsNode) {
                            coords.add(List.of(pt.get(1).asDouble(), pt.get(0).asDouble()));
                        }
                    }

                    if (!coords.isEmpty()) {
                        return RouteCalculationResult.builder()
                                .distanceKm(distKm)
                                .durationMinutes(durMin)
                                .coordinates(coords)
                                .isFallback(false)
                                .provider("OSRM")
                                .build();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("OSRM routing call failed: {}. Generating curved road approximation.", e.getMessage());
        }

        // Fallback Route Calculation: Haversine distance with curved road interpolation
        return generateFallbackRoute(startLat, startLng, destLat, destLng);
    }

    private RouteCalculationResult generateFallbackRoute(double startLat, double startLng, double destLat, double destLng) {
        double dLat = Math.toRadians(destLat - startLat);
        double dLng = Math.toRadians(destLng - startLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(destLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightLineKm = 6371 * c;

        // Road distance is typically 1.25x - 1.35x straight line
        double roadDistanceKm = straightLineKm * 1.30;
        if (roadDistanceKm < 5.0) roadDistanceKm = 5.0;

        BigDecimal distKm = BigDecimal.valueOf(roadDistanceKm).setScale(2, RoundingMode.HALF_UP);
        int durationMinutes = (int) Math.round((roadDistanceKm / 55.0) * 60.0);

        List<List<Double>> coordinates = new ArrayList<>();
        int steps = 25;
        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double lat = startLat + fraction * (destLat - startLat);
            double lng = startLng + fraction * (destLng - startLng);

            double deviation = Math.sin(fraction * Math.PI) * 0.015;
            lat += deviation;
            lng += deviation * 0.7;

            coordinates.add(List.of(
                    BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP).doubleValue(),
                    BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP).doubleValue()
            ));
        }

        return RouteCalculationResult.builder()
                .distanceKm(distKm)
                .durationMinutes(Math.max(10, durationMinutes))
                .coordinates(coordinates)
                .isFallback(true)
                .provider("HAVERSINE_HIGHWAY_MODEL")
                .build();
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }

    /**
     * Queries real live OpenStreetMap Overpass API for verified nearby POIs
     * (Fuel, EV Chargers, Parking, Restaurants) within a 5 km radius.
     * Returns a truthful empty list if no verified places are found.
     */
    public List<PoiDto> getNearbyPlaces(double lat, double lng) {
        List<PoiDto> pois = new ArrayList<>();

        try {
            String query = String.format(Locale.US,
                    "[out:json][timeout:5];(node[\"amenity\"~\"fuel|charging_station|parking|restaurant\"](around:5000,%.6f,%.6f););out 8;",
                    lat, lng
            );
            String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "DriveSphere-Mobility-Platform/1.0 (support@drivesphere.com)")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode elements = root.path("elements");
                if (elements.isArray()) {
                    for (JsonNode elem : elements) {
                        double nodeLat = elem.path("lat").asDouble(0.0);
                        double nodeLon = elem.path("lon").asDouble(0.0);
                        JsonNode tags = elem.path("tags");
                        String name = tags.path("name").asText(null);
                        String amenity = tags.path("amenity").asText("");

                        if (name == null || name.isBlank() || nodeLat == 0.0 || nodeLon == 0.0) {
                            continue;
                        }

                        String category = switch (amenity) {
                            case "fuel" -> "PETROL_PUMP";
                            case "charging_station" -> "EV_CHARGER";
                            case "parking" -> "PARKING";
                            default -> "RESTAURANT";
                        };

                        double dist = calculateHaversineKm(lat, lng, nodeLat, nodeLon);
                        String address = tags.has("addr:street") ? tags.path("addr:street").asText() : (tags.has("brand") ? tags.path("brand").asText() : "Verified POI");

                        pois.add(PoiDto.builder()
                                .name(name)
                                .category(category)
                                .latitude(nodeLat)
                                .longitude(nodeLon)
                                .address(address)
                                .distanceKm(BigDecimal.valueOf(dist).setScale(1, RoundingMode.HALF_UP).doubleValue())
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.info("Live Overpass POI lookup returned: {}. Providing truthful empty verified place dataset.", e.getMessage());
        }

        return pois;
    }
}
