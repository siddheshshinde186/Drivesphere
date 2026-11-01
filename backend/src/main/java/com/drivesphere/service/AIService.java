package com.drivesphere.service;

import com.drivesphere.dto.*;
import com.drivesphere.dto.TripPlannerResponse.PoiDto;
import com.drivesphere.dto.TripPlannerResponse.WeatherSummaryDto;
import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Vehicle;
import com.drivesphere.integration.gemini.GeminiClient;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.integration.maps.MapsClient.GeocodeResult;
import com.drivesphere.integration.maps.MapsClient.RouteCalculationResult;
import com.drivesphere.integration.weather.WeatherClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final GeminiClient geminiClient;
    private final MapsClient mapsClient;
    private final WeatherClient weatherClient;
    private final VehicleRepository vehicleRepository;
    private final EntityDtoMapper mapper;

    @Transactional(readOnly = true)
    public AiRecommendationResponse recommendVehicles(AiRecommendationRequest request) {
        int requestedPassengers = request.getPassengers() != null && request.getPassengers() > 0
                ? request.getPassengers()
                : 1;

        // 1. Retrieve operational available vehicles from database (source of truth)
        // If rental period is supplied, strictly check period availability
        List<Vehicle> operational;
        if (request.getStartDateTime() != null && request.getEndDateTime() != null) {
            operational = vehicleRepository.findAvailableVehiclesForPeriod(request.getStartDateTime(), request.getEndDateTime());
        } else {
            operational = vehicleRepository.findOperationalAvailableVehicles();
        }

        // Hard constraint: Never fall back to findAll(). If operational fleet is empty, return NO_FLEET_AVAILABLE
        if (operational == null || operational.isEmpty()) {
            String explanation = (request.getStartDateTime() != null && request.getEndDateTime() != null)
                    ? "No operational vehicles are available for the selected dates. Please choose alternative dates or contact support."
                    : "No operational vehicles are currently available in the active fleet.";
            return AiRecommendationResponse.builder()
                    .requestedPassengers(requestedPassengers)
                    .multiVehicleRequired(false)
                    .totalAvailableFleetCapacity(0)
                    .status("NO_FLEET_AVAILABLE")
                    .recommendations(Collections.emptyList())
                    .summaryExplanation(explanation)
                    .generatedByGemini(false)
                    .build();
        }

        int totalFleetCapacity = operational.stream().mapToInt(Vehicle::getSeatingCapacity).sum();

        // HARD CONSTRAINT: Check if requested passengers exceed total operational fleet capacity
        if (requestedPassengers > totalFleetCapacity) {
            String explanation = String.format(
                    "No single vehicle or multi-vehicle combination can accommodate %d passengers. "
                    + "The maximum available fleet capacity across all %d operational vehicles is %d seats. "
                    + "We couldn't find a suitable multi-vehicle combination in the current fleet. Please contact our enterprise support team.",
                    requestedPassengers, operational.size(), totalFleetCapacity
            );

            return AiRecommendationResponse.builder()
                    .requestedPassengers(requestedPassengers)
                    .multiVehicleRequired(true)
                    .totalAvailableFleetCapacity(totalFleetCapacity)
                    .status("NO_FLEET_AVAILABLE")
                    .recommendations(Collections.emptyList())
                    .summaryExplanation(explanation)
                    .generatedByGemini(false)
                    .build();
        }

        // 2. Identify single-vehicle candidates that strictly satisfy seating capacity and budget
        List<Vehicle> singleCandidates = operational.stream()
                .filter(v -> v.getSeatingCapacity() >= requestedPassengers)
                .filter(v -> request.getMaxBudgetPerDay() == null || v.getRentalPricePerDay().compareTo(request.getMaxBudgetPerDay()) <= 0)
                .collect(Collectors.toList());

        List<VehicleRecommendationDto> recommendations = new ArrayList<>();
        boolean multiVehicleRequired = singleCandidates.isEmpty() && requestedPassengers > 7;

        // If single vehicles can accommodate all passengers
        if (!singleCandidates.isEmpty()) {
            for (Vehicle v : singleCandidates) {
                int score = 75;
                String reason;
                String suitableFor;

                // Seating proximity criteria
                if (v.getSeatingCapacity() == requestedPassengers) {
                    score += 15;
                } else if (v.getSeatingCapacity() - requestedPassengers <= 2) {
                    score += 8;
                } else {
                    score += 3;
                }

                // Vehicle Type & Performance Criteria
                if (v.getVehicleType().name().contains("ELECTRIC")) {
                    score += 8;
                    reason = "Zero-emissions EV with 310+ km range, ideal for eco-friendly modern urban & regional highway touring.";
                    suitableFor = "Eco-Conscious Commuters & Tech Enthusiasts";
                } else if (v.getVehicleType().name().contains("SUV")) {
                    score += 8;
                    reason = "Commanding high ground clearance, 7-passenger capability, and expansive cargo bay for family roadtrips.";
                    suitableFor = "Family Roadtrips & Group Expeditions";
                } else if (v.getVehicleType().name().contains("LUXURY")) {
                    score += 6;
                    reason = "Executive leather cabin appointments, active sound damping, and prestige ride dynamics.";
                    suitableFor = "Executive Business Travel & VIP Events";
                } else {
                    score += 5;
                    reason = "Exceptional hybrid/petrol fuel economy and effortless highway cruise value.";
                    suitableFor = "City Commutes & Highway Value";
                }

                // Fuel & Transmission preference matches
                if (request.getFuelPreference() != null && request.getFuelPreference() == v.getFuelType()) {
                    score += 8;
                }
                if (request.getTransmissionPreference() != null && request.getTransmissionPreference() == v.getTransmission()) {
                    score += 4;
                }

                // Budget value criteria
                if (request.getMaxBudgetPerDay() != null) {
                    BigDecimal budgetThreshold = request.getMaxBudgetPerDay().multiply(BigDecimal.valueOf(0.85));
                    if (v.getRentalPricePerDay().compareTo(budgetThreshold) <= 0) {
                        score += 5;
                    }
                }

                int matchPercent = Math.min(99, Math.max(70, score));

                recommendations.add(VehicleRecommendationDto.builder()
                        .vehicle(mapper.toVehicleDto(v))
                        .vehicleCount(1)
                        .totalSeatsCapacity(v.getSeatingCapacity())
                        .perVehicleDailyRate(v.getRentalPricePerDay())
                        .totalDailyRental(v.getRentalPricePerDay())
                        .seatingCalculation(String.format("1 Vehicle x %d seats = %d total seats", v.getSeatingCapacity(), v.getSeatingCapacity()))
                        .multiVehicle(false)
                        .available(true)
                        .availabilityNote("1 Unit Available Now")
                        .matchPercentage(matchPercent)
                        .matchReason(reason)
                        .bestUsedFor(suitableFor)
                        .estimatedCost(v.getRentalPricePerDay())
                        .fleetItems(List.of(
                                FleetItemDto.builder()
                                        .vehicle(mapper.toVehicleDto(v))
                                        .quantity(1)
                                        .seatsPerVehicle(v.getSeatingCapacity())
                                        .totalSeats(v.getSeatingCapacity())
                                        .dailyRatePerVehicle(v.getRentalPricePerDay())
                                        .subtotalDailyRate(v.getRentalPricePerDay())
                                        .build()
                        ))
                        .build());
            }

            // Sort single candidates by match percentage
            recommendations.sort(Comparator.comparingInt((VehicleRecommendationDto r) -> r.getMatchPercentage() != null ? r.getMatchPercentage() : 0).reversed());
        }

        // 3. If multi-vehicle is required or for large groups (>= 6)
        if (singleCandidates.isEmpty() || requestedPassengers >= 6) {
            List<VehicleRecommendationDto> multiCombos = buildMultiVehicleCombinations(
                    operational, requestedPassengers, request.getMaxBudgetPerDay(), request.getTripType(), request.getFuelPreference()
            );
            if (singleCandidates.isEmpty()) {
                multiVehicleRequired = true;
                recommendations.clear();
                recommendations.addAll(multiCombos);
            } else if (recommendations.size() < 4) {
                recommendations.addAll(multiCombos.stream().limit(1).collect(Collectors.toList()));
            }
        }

        // If after filtering by capacity, budget, and operational status, NO recommendations remain:
        if (recommendations.isEmpty()) {
            String explanation;
            if (request.getMaxBudgetPerDay() != null) {
                explanation = String.format(
                        "No operational vehicles or combinations satisfy your requirement for %d passengers within the daily budget limit of ₹%s. "
                        + "Please consider adjusting your budget or passenger count.",
                        requestedPassengers, request.getMaxBudgetPerDay()
                );
            } else {
                explanation = String.format(
                        "No suitable fleet combination in our active inventory could accommodate %d passengers. Please contact support.",
                        requestedPassengers
                );
            }
            return AiRecommendationResponse.builder()
                    .requestedPassengers(requestedPassengers)
                    .multiVehicleRequired(multiVehicleRequired)
                    .totalAvailableFleetCapacity(totalFleetCapacity)
                    .status("NO_FLEET_AVAILABLE")
                    .recommendations(Collections.emptyList())
                    .summaryExplanation(explanation)
                    .generatedByGemini(false)
                    .build();
        }

        // 4. Generate factually accurate AI summary explanation
        SummaryResult summary = generateFactualSummary(requestedPassengers, recommendations, multiVehicleRequired);

        return AiRecommendationResponse.builder()
                .requestedPassengers(requestedPassengers)
                .multiVehicleRequired(multiVehicleRequired)
                .totalAvailableFleetCapacity(totalFleetCapacity)
                .status("SUCCESS")
                .recommendations(recommendations.stream().limit(4).collect(Collectors.toList()))
                .summaryExplanation(summary.getExplanation())
                .generatedByGemini(summary.isGeneratedByGemini())
                .build();
    }

    @lombok.Value
    public static class SummaryResult {
        String explanation;
        boolean generatedByGemini;
    }

    private List<VehicleRecommendationDto> buildMultiVehicleCombinations(
            List<Vehicle> operational, int requestedPassengers, BigDecimal maxBudgetPerDay,
            String tripType, FuelType fuelPref) {

        List<VehicleRecommendationDto> combos = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        // Group operational vehicles by seating capacity: distinct physical units
        List<Vehicle> sevenSeaters = operational.stream().filter(v -> v.getSeatingCapacity() == 7).collect(Collectors.toList());
        List<Vehicle> fiveSeaters = operational.stream().filter(v -> v.getSeatingCapacity() == 5).collect(Collectors.toList());

        // Combination 1: High-Capacity Balanced Convoy (7-seaters first, then 5-seaters)
        List<Vehicle> combo1 = new ArrayList<>();
        Set<Long> usedIds1 = new HashSet<>();
        int cap1 = 0;
        BigDecimal cost1 = BigDecimal.ZERO;

        for (Vehicle v : sevenSeaters) {
            if (cap1 < requestedPassengers && !usedIds1.contains(v.getId())) {
                combo1.add(v);
                usedIds1.add(v.getId());
                cap1 += v.getSeatingCapacity();
                cost1 = cost1.add(v.getRentalPricePerDay());
            }
        }
        for (Vehicle v : fiveSeaters) {
            if (cap1 < requestedPassengers && !usedIds1.contains(v.getId())) {
                combo1.add(v);
                usedIds1.add(v.getId());
                cap1 += v.getSeatingCapacity();
                cost1 = cost1.add(v.getRentalPricePerDay());
            }
        }

        if (cap1 >= requestedPassengers && !combo1.isEmpty()) {
            boolean budgetOk = (maxBudgetPerDay == null || cost1.compareTo(maxBudgetPerDay) <= 0);
            if (budgetOk) {
                String sig = getComboSignature(combo1);
                seenSignatures.add(sig);
                combos.add(createMultiVehicleDto(
                        combo1, cap1, cost1, 94,
                        "Optimal Executive & SUV Convoy",
                        String.format("Recommended multi-vehicle configuration to comfortably transport %d passengers with extensive luggage space.", requestedPassengers)
                ));
            }
        }

        // Combination 2: Budget-Optimized Convoy (sorted by price ascending)
        List<Vehicle> sortedByPrice = operational.stream()
                .sorted(Comparator.comparing(Vehicle::getRentalPricePerDay))
                .collect(Collectors.toList());

        List<Vehicle> combo2 = new ArrayList<>();
        Set<Long> usedIds2 = new HashSet<>();
        int cap2 = 0;
        BigDecimal cost2 = BigDecimal.ZERO;

        for (Vehicle v : sortedByPrice) {
            if (cap2 < requestedPassengers && !usedIds2.contains(v.getId())) {
                combo2.add(v);
                usedIds2.add(v.getId());
                cap2 += v.getSeatingCapacity();
                cost2 = cost2.add(v.getRentalPricePerDay());
            }
        }

        if (cap2 >= requestedPassengers && !combo2.isEmpty()) {
            boolean budgetOk = (maxBudgetPerDay == null || cost2.compareTo(maxBudgetPerDay) <= 0);
            String sig = getComboSignature(combo2);
            if (budgetOk && !seenSignatures.contains(sig)) {
                seenSignatures.add(sig);
                combos.add(createMultiVehicleDto(
                        combo2, cap2, cost2, 90,
                        "High-Efficiency & Budget-Optimized Fleet",
                        String.format("Economical multi-vehicle convoy for %d passengers combining high fuel-efficiency and low operational cost.", requestedPassengers)
                ));
            }
        }

        // Combination 3: Eco-Friendly EV / Hybrid Convoy
        List<Vehicle> ecoVehicles = operational.stream()
                .filter(v -> v.getFuelType() == FuelType.ELECTRIC || v.getFuelType() == FuelType.HYBRID)
                .collect(Collectors.toList());

        List<Vehicle> combo3 = new ArrayList<>();
        Set<Long> usedIds3 = new HashSet<>();
        int cap3 = 0;
        BigDecimal cost3 = BigDecimal.ZERO;

        for (Vehicle v : ecoVehicles) {
            if (cap3 < requestedPassengers && !usedIds3.contains(v.getId())) {
                combo3.add(v);
                usedIds3.add(v.getId());
                cap3 += v.getSeatingCapacity();
                cost3 = cost3.add(v.getRentalPricePerDay());
            }
        }

        if (cap3 >= requestedPassengers && !combo3.isEmpty()) {
            boolean budgetOk = (maxBudgetPerDay == null || cost3.compareTo(maxBudgetPerDay) <= 0);
            String sig = getComboSignature(combo3);
            if (budgetOk && !seenSignatures.contains(sig)) {
                seenSignatures.add(sig);
                combos.add(createMultiVehicleDto(
                        combo3, cap3, cost3, 92,
                        "Zero-Emission & Hybrid Green Convoy",
                        String.format("Eco-conscious multi-vehicle convoy for %d passengers utilizing real operational EV and Hybrid fleet units.", requestedPassengers)
                ));
            }
        }

        return combos;
    }

    private String getComboSignature(List<Vehicle> list) {
        return list.stream().map(v -> String.valueOf(v.getId())).sorted().collect(Collectors.joining("-"));
    }

    private VehicleRecommendationDto createMultiVehicleDto(
            List<Vehicle> vehicles, int totalSeats, BigDecimal totalDailyRental,
            int matchPercent, String bestFor, String reason) {

        Vehicle primary = vehicles.get(0);
        BigDecimal avgRate = totalDailyRental.divide(BigDecimal.valueOf(vehicles.size()), 2, RoundingMode.HALF_UP);

        Map<String, List<Vehicle>> grouped = vehicles.stream()
                .collect(Collectors.groupingBy(v -> v.getSeatingCapacity() + "-seater " + v.getVehicleType().name()));

        String calculation = grouped.entrySet().stream()
                .map(e -> e.getValue().size() + " x " + e.getKey())
                .collect(Collectors.joining(" + ")) + " = " + totalSeats + " total seats";

        Map<String, List<Vehicle>> groupedByModel = vehicles.stream()
                .collect(Collectors.groupingBy(Vehicle::getModel, LinkedHashMap::new, Collectors.toList()));

        List<FleetItemDto> items = groupedByModel.values().stream()
                .map(vList -> {
                    Vehicle v = vList.get(0);
                    int qty = vList.size();
                    BigDecimal subtotal = v.getRentalPricePerDay().multiply(BigDecimal.valueOf(qty));
                    return FleetItemDto.builder()
                            .vehicle(mapper.toVehicleDto(v))
                            .quantity(qty)
                            .seatsPerVehicle(v.getSeatingCapacity())
                            .totalSeats(v.getSeatingCapacity() * qty)
                            .dailyRatePerVehicle(v.getRentalPricePerDay())
                            .subtotalDailyRate(subtotal)
                            .build();
                })
                .collect(Collectors.toList());

        return VehicleRecommendationDto.builder()
                .vehicle(mapper.toVehicleDto(primary))
                .vehicleCount(vehicles.size())
                .totalSeatsCapacity(totalSeats)
                .perVehicleDailyRate(avgRate)
                .totalDailyRental(totalDailyRental)
                .seatingCalculation(calculation)
                .multiVehicle(true)
                .available(true)
                .availabilityNote(String.format("All %d distinct vehicle units are operational & available in fleet", vehicles.size()))
                .matchPercentage(matchPercent)
                .matchReason(reason)
                .bestUsedFor(bestFor)
                .estimatedCost(totalDailyRental)
                .fleetItems(items)
                .build();
    }

    private SummaryResult generateFactualSummary(int passengers, List<VehicleRecommendationDto> recommendations, boolean isMultiVehicle) {
        if (recommendations.isEmpty()) {
            return new SummaryResult(String.format("No suitable fleet combination found for %d passengers.", passengers), false);
        }

        VehicleRecommendationDto topPick = recommendations.get(0);

        if (isMultiVehicle) {
            String fallback = String.format(
                    "%d passengers cannot fit in one single vehicle. We recommend %d vehicles (%s) for %d total seats, subject to fleet availability.",
                    passengers, topPick.getVehicleCount(), topPick.getSeatingCalculation(), topPick.getTotalSeatsCapacity()
            );

            if (geminiClient.isConfigured()) {
                String prompt = String.format(
                        "You are DriveSphere AI Mobility Concierge. A group of %d passengers requires vehicle rental. "
                        + "HARD CAPACITY RULE: No single vehicle in the fleet can fit %d passengers. "
                        + "Our deterministic algorithm validated this multi-vehicle recommendation: %d vehicles, %s, %d total seats at total ₹%s/day. "
                        + "In 2 concise, factually accurate sentences, explain why this multi-vehicle combination meets their capacity requirements without inventing vehicle specs.",
                        passengers, passengers, topPick.getVehicleCount(), topPick.getSeatingCalculation(),
                        topPick.getTotalSeatsCapacity(), topPick.getTotalDailyRental()
                );
                GeminiClient.GeminiResult result = geminiClient.executePrompt(prompt);
                if (result.isSuccess() && result.getText() != null) {
                    return new SummaryResult(result.getText(), true);
                }
            }
            return new SummaryResult(fallback, false);
        } else {
            // Single vehicle recommendation
            String fallback = String.format(
                    "For your travel party of %d, our top recommendation is the %s (%d seats) at ₹%s/day. It provides the optimal blend of seating comfort, luggage capacity, and efficiency.",
                    passengers, topPick.getVehicle().getDisplayName(), topPick.getVehicle().getSeatingCapacity(), topPick.getVehicle().getRentalPricePerDay()
            );

            if (geminiClient.isConfigured()) {
                String prompt = String.format(
                        "You are DriveSphere AI Mobility Concierge. Customer requested vehicle for %d passengers. "
                        + "Our validated top pick is %s (%d seats, ₹%s/day). "
                        + "In 2 concise, factually accurate sentences, explain why this vehicle fits their group.",
                        passengers, topPick.getVehicle().getDisplayName(), topPick.getVehicle().getSeatingCapacity(), topPick.getVehicle().getRentalPricePerDay()
                );
                GeminiClient.GeminiResult result = geminiClient.executePrompt(prompt);
                if (result.isSuccess() && result.getText() != null) {
                    return new SummaryResult(result.getText(), true);
                }
            }
            return new SummaryResult(fallback, false);
        }
    }

    @Transactional(readOnly = true)
    public AiChatResponse chat(AiChatRequest request) {
        String userMsg = request.getMessage().trim();
        boolean isAuthenticated = Boolean.TRUE.equals(request.getAuthenticated());

        // Pipeline Architecture:
        // 1. Transactional & Booking Intent (requires login if public)
        if (isTransactionalIntent(userMsg) && !isAuthenticated) {
            return AiChatResponse.builder()
                    .reply("To book a vehicle or access personal reservations, you must sign in to your DriveSphere customer account.\n\n👉 [Please sign in here](/login.html?returnTo=/customer/index.html&action=book) to continue with your booking.")
                    .suggestedQuestions(List.of(
                            "Which car is best for family?",
                            "How does Driver Trust Score work?",
                            "Estimate fuel cost for my journey",
                            "What documents are needed for rental?"
                    ))
                    .fallbackMode(false)
                    .build();
        }

        // 2. Real Distance & Driving Route Pipeline
        RoutePair routePair = extractRoutePair(userMsg);
        if (routePair != null) {
            return handleRouteQuery(userMsg, routePair);
        }

        // 3. Real Destination Weather Pipeline
        String weatherLocation = extractWeatherLocation(userMsg);
        if (weatherLocation != null) {
            return handleWeatherQuery(userMsg, weatherLocation);
        }

        // 4. Seating Capacity & Passenger Recommendation Intent (Strict Hard Business Rule)
        Integer requestedPassengers = extractPassengerCount(userMsg);
        if (requestedPassengers != null) {
            return handlePassengerRecommendation(userMsg, requestedPassengers);
        }

        // 5. General Platform Questions & Domain Intelligence
        String structuredReply = generateChatbotFallback(userMsg);
        String reply = structuredReply;
        boolean fallbackMode = true;
        String modelProvider;

        if (!geminiClient.isConfigured()) {
            modelProvider = "Heuristic Mobility Engine [Offline Mode]";
        } else {
            String prompt = "You are DriveSphere AI Concierge, a knowledgeable, polite smart mobility assistant for the DriveSphere Fleet Platform. "
                    + "DriveSphere offers verified self-drive vehicles, professional driver hiring with Driver Trust Scores (0-100), smart trip routing with Open-Meteo weather, and test payments. "
                    + "User asks: \"" + userMsg + "\". "
                    + "Provide a helpful, precise 2-3 sentence response. Suggest next steps.";
            GeminiClient.GeminiResult geminiResult = geminiClient.executePrompt(prompt);
            if (geminiResult.isSuccess() && geminiResult.getText() != null) {
                reply = geminiResult.getText();
                fallbackMode = false;
                modelProvider = geminiClient.getModelDisplayName();
            } else {
                modelProvider = "Heuristic Mobility Engine [Gemini Fallback]";
            }
        }

        List<String> suggestions = getContextualSuggestions(userMsg);

        return AiChatResponse.builder()
                .reply(reply)
                .suggestedQuestions(suggestions)
                .fallbackMode(fallbackMode)
                .modelProvider(modelProvider)
                .build();
    }

    private static class RoutePair {
        final String origin;
        final String destination;

        RoutePair(String origin, String destination) {
            this.origin = origin;
            this.destination = destination;
        }
    }

    private RoutePair extractRoutePair(String message) {
        String text = message.trim();

        // Pattern 1: "distance between X and/to Y"
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(
                "(?i)(?:distance|drive|driving|time|travel|route|cost).*?(?:between|from)\\s+([a-zA-Z\\s]+?)\\s+(?:and|to)\\s+([a-zA-Z\\s]+?)(?:\\?|\\.|\\s*take|\\s*cost|\\s*is|\\s*$)"
        );
        java.util.regex.Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return cleanRoutePair(m1.group(1), m1.group(2));
        }

        // Pattern 2: "how far is X from Y"
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
                "(?i)how\\s+far\\s+(?:is\\s+)?([a-zA-Z\\s]+?)\\s+from\\s+([a-zA-Z\\s]+?)(?:\\?|\\.|\\s*$)"
        );
        java.util.regex.Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return cleanRoutePair(m2.group(1), m2.group(2));
        }

        // Pattern 3: "how long does X to Y take"
        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(
                "(?i)how\\s+long\\s+does\\s+([a-zA-Z\\s]+?)\\s+to\\s+([a-zA-Z\\s]+?)\\s+take(?:\\?|\\.|\\s*$)"
        );
        java.util.regex.Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            return cleanRoutePair(m3.group(1), m3.group(2));
        }

        // Pattern 4: "how long to drive from X to Y"
        java.util.regex.Pattern p4 = java.util.regex.Pattern.compile(
                "(?i)how\\s+long\\s+(?:to\\s+drive\\s+)?from\\s+([a-zA-Z\\s]+?)\\s+to\\s+([a-zA-Z\\s]+?)(?:\\?|\\.|\\s*$)"
        );
        java.util.regex.Matcher m4 = p4.matcher(text);
        if (m4.find()) {
            return cleanRoutePair(m4.group(1), m4.group(2));
        }

        // Pattern 5: "X to Y distance" or "X to Y route"
        java.util.regex.Pattern p5 = java.util.regex.Pattern.compile(
                "(?i)([a-zA-Z\\s]{3,20}?)\\s+to\\s+([a-zA-Z\\s]{3,20}?)\\s+(?:distance|duration|route|time|cost|driving|travel)"
        );
        java.util.regex.Matcher m5 = p5.matcher(text);
        if (m5.find()) {
            return cleanRoutePair(m5.group(1), m5.group(2));
        }

        // Pattern 6: General route question mentioning distance/far/long with "from X to Y" or "X to Y"
        String lower = text.toLowerCase();
        if (lower.contains("distance") || lower.contains("far") || lower.contains("route") || lower.contains("how long") || lower.contains("driving time")) {
            java.util.regex.Pattern p6 = java.util.regex.Pattern.compile(
                    "(?i)(?:from\\s+)?([a-zA-Z]{3,20})\\s+(?:to|->|&rarr;)\\s+([a-zA-Z]{3,20})"
            );
            java.util.regex.Matcher m6 = p6.matcher(text);
            if (m6.find()) {
                return cleanRoutePair(m6.group(1), m6.group(2));
            }
        }

        return null;
    }

    private RoutePair cleanRoutePair(String rawOrigin, String rawDest) {
        if (rawOrigin == null || rawDest == null) return null;
        String o = rawOrigin.trim().replaceAll("^(?i)(the|is|how|much|is|a)\\s+", "").trim();
        String d = rawDest.trim().replaceAll("^(?i)(the|is|how|much|is|a)\\s+", "").trim();
        d = d.replaceAll("(?i)\\s+(take|cost|distance|duration|approx|is|hours|km|time)$", "").trim();

        if (o.length() < 2 || d.length() < 2) return null;
        return new RoutePair(o, d);
    }

    private AiChatResponse handleRouteQuery(String userMsg, RoutePair routePair) {
        GeocodeResult originGeo;
        try {
            originGeo = mapsClient.geocode(routePair.origin);
        } catch (Exception e) {
            return AiChatResponse.builder()
                    .reply("I couldn't locate '" + routePair.origin + "'. Please provide a more specific location.")
                    .suggestedQuestions(List.of("Bangalore to Mysore route cost", "How far is Pune from Mumbai?", "What is the distance between Kolhapur and Mumbai?"))
                    .fallbackMode(true)
                    .build();
        }

        GeocodeResult destGeo;
        try {
            destGeo = mapsClient.geocode(routePair.destination);
        } catch (Exception e) {
            return AiChatResponse.builder()
                    .reply("I couldn't locate '" + routePair.destination + "'. Please provide a more specific location.")
                    .suggestedQuestions(List.of("Bangalore to Mysore route cost", "How far is Pune from Mumbai?", "What is the distance between Kolhapur and Mumbai?"))
                    .fallbackMode(true)
                    .build();
        }

        RouteCalculationResult route;
        try {
            route = mapsClient.calculateRoute(originGeo.getLat(), originGeo.getLng(), destGeo.getLat(), destGeo.getLng());
        } catch (Exception e) {
            return AiChatResponse.builder()
                    .reply("I couldn't calculate the route right now. Please try again.")
                    .suggestedQuestions(List.of("How far is Pune from Mumbai?", "Bangalore to Mysore route cost"))
                    .fallbackMode(true)
                    .build();
        }

        if (route == null || route.getDistanceKm() == null || route.getDistanceKm().compareTo(BigDecimal.ZERO) <= 0) {
            return AiChatResponse.builder()
                    .reply("I couldn't calculate the route right now. Please try again.")
                    .suggestedQuestions(List.of("How far is Pune from Mumbai?", "Bangalore to Mysore route cost"))
                    .fallbackMode(true)
                    .build();
        }

        BigDecimal distKm = route.getDistanceKm();
        int durMin = route.getDurationMinutes();
        int hours = durMin / 60;
        int remainingMins = durMin % 60;
        String durationStr = hours > 0 ? (hours + " hr" + (hours > 1 ? "s " : " ") + (remainingMins > 0 ? remainingMins + " min" : "")) : (durMin + " mins");

        BigDecimal evKwh = distKm.multiply(BigDecimal.valueOf(0.152)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal evCost = evKwh.multiply(BigDecimal.valueOf(12.00)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal iceLiters = distKm.divide(BigDecimal.valueOf(14.8), 1, RoundingMode.HALF_UP);
        BigDecimal iceCost = iceLiters.multiply(BigDecimal.valueOf(102.50)).setScale(2, RoundingMode.HALF_UP);

        String originName = originGeo.getDisplayName().split(",")[0].trim();
        String destName = destGeo.getDisplayName().split(",")[0].trim();

        String structuredReply = String.format(
                "The driving distance between **%s** and **%s** is **%s km**, taking approximately **%s** via the highway network (OSRM real-time routing).\n\n"
                + "• **Estimated EV Energy**: ~%s kWh (Rs. %s @ Rs. 12.00/kWh)\n"
                + "• **Estimated Fuel (Petrol/Diesel)**: ~%s L (Rs. %s @ Rs. 102.50/L)\n\n"
                + "Would you like to explore available vehicles or hire a verified driver for this journey?",
                originName, destName, distKm, durationStr, evKwh, evCost, iceLiters, iceCost
        );

        String finalReply = structuredReply;
        boolean fallbackMode = true;
        String modelProvider;

        if (!geminiClient.isConfigured()) {
            modelProvider = "OpenRouteService Highway Engine [Direct Routing]";
        } else {
            String factPrompt = String.format(
                    "You are DriveSphere AI Concierge. The customer asked: \"%s\". "
                    + "Our live routing engine calculated the exact facts: "
                    + "Origin: %s, Destination: %s, Distance: %s km, Duration: %s, EV Energy: %s kWh (Rs. %s @ Rs. 12/kWh), ICE Fuel: %s L (Rs. %s @ Rs. 102.50/L). "
                    + "In 2-3 friendly, factual sentences, answer the customer's question using these EXACT figures. DO NOT change or invent any numbers.",
                    userMsg, originName, destName, distKm, durationStr, evKwh, evCost, iceLiters, iceCost
            );
            GeminiClient.GeminiResult geminiResult = geminiClient.executePrompt(factPrompt);
            if (geminiResult.isSuccess() && geminiResult.getText() != null) {
                finalReply = geminiResult.getText();
                fallbackMode = false;
                modelProvider = geminiClient.getModelDisplayName() + " (with ORS Routing)";
            } else {
                modelProvider = "OpenRouteService Highway Engine [Gemini Fallback]";
            }
        }

        return AiChatResponse.builder()
                .reply(finalReply)
                .suggestedQuestions(List.of(
                        "Can I hire a verified driver for this route?",
                        "What is the weather in " + destName + "?",
                        "Which car is best for highway trips?",
                        "Can I rent an EV for this route?"
                ))
                .fallbackMode(fallbackMode)
                .modelProvider(modelProvider)
                .build();
    }

    private String extractWeatherLocation(String message) {
        String text = message.trim();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)(?:weather|temperature|climate|forecast|rain)\\s+(?:in|at|for)?\\s+([a-zA-Z\\s]+?)(?:\\?|\\.|\\s*$)"
        );
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            String loc = m.group(1).trim().replaceAll("^(?i)(the|is|a)\\s+", "").trim();
            if (loc.length() >= 2) return loc;
        }
        return null;
    }

    private AiChatResponse handleWeatherQuery(String userMsg, String location) {
        GeocodeResult geo;
        try {
            geo = mapsClient.geocode(location);
        } catch (Exception e) {
            return AiChatResponse.builder()
                    .reply("I couldn't locate '" + location + "'. Please provide a more specific location.")
                    .suggestedQuestions(List.of("What is the weather in Goa?", "Weather in Mumbai", "Bangalore to Mysore route cost"))
                    .fallbackMode(true)
                    .build();
        }

        WeatherSummaryDto weather = weatherClient.getWeather(geo.getLat(), geo.getLng());
        String cityName = geo.getDisplayName().split(",")[0].trim();

        String structuredReply = String.format(
                "The current live weather in **%s** is **%.1f°C** (%s) with wind speed of **%.1f km/h** and %d%% precipitation probability. Advisory: %s",
                cityName, weather.getTemperature(), weather.getCondition(), weather.getWindSpeed(),
                weather.getPrecipitationProbability(), weather.getForecastNotice()
        );

        return AiChatResponse.builder()
                .reply(structuredReply)
                .suggestedQuestions(List.of(
                        "Plan a trip to " + cityName,
                        "Which car is best for family?",
                        "How does Driver Trust Score work?",
                        "Can I rent an EV?"
                ))
                .fallbackMode(true)
                .modelProvider("Open-Meteo Weather Intelligence")
                .build();
    }

    private Integer extractPassengerCount(String message) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)([0-9]{1,2})\\s*(?:passenger|people|person|seat|seater)");
        java.util.regex.Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private AiChatResponse handlePassengerRecommendation(String userMsg, int passengers) {
        AiRecommendationResponse rec = recommendVehicles(AiRecommendationRequest.builder()
                .passengers(passengers)
                .maxBudgetPerDay(BigDecimal.valueOf(25000))
                .build());

        String reply;
        if (passengers > 7) {
            reply = String.format(
                    "For **%d passengers**, a single vehicle cannot accommodate your group (maximum single vehicle capacity is 7 seats). "
                    + "Our AI Fleet Matcher enforces a hard seating rule and recommends a **multi-vehicle fleet combination**: %s. "
                    + "Total capacity: **%d seats** across %d vehicles at approximately Rs. %s/day.",
                    passengers,
                    rec.getSummaryExplanation(),
                    rec.getRecommendations().isEmpty() ? passengers : rec.getRecommendations().get(0).getTotalSeatsCapacity(),
                    rec.getRecommendations().isEmpty() ? 2 : rec.getRecommendations().get(0).getVehicleCount(),
                    rec.getRecommendations().isEmpty() ? "10,000" : rec.getRecommendations().get(0).getTotalDailyRental()
            );
        } else if (passengers > 5) {
            reply = "For **" + passengers + " passengers**, single 5-seater vehicles are insufficient. We recommend our spacious 7-seater SUV: the **Mahindra XUV700 AX7 Luxury** (7 seats, Rs. 3,600/day) or the **Toyota Fortuner Legender 4x4** (7 seats, Rs. 4,800/day). Both provide comfortable 3-row seating and premium ADAS safety.";
        } else {
            reply = "For **" + passengers + " passengers**, our top recommendation is the **Tata Nexon EV Empowered** (5 seats, Rs. 2,800/day, 312 km range) or the **Honda City ZX e:HEV Hybrid** (5 seats, Rs. 2,400/day, 27.1 km/l).";
        }

        return AiChatResponse.builder()
                .reply(reply)
                .suggestedQuestions(List.of(
                        "How does Driver Trust Score work?",
                        "Estimate fuel cost for Bangalore to Mysore",
                        "Can I rent an EV?",
                        "What documents are needed for rental?"
                ))
                .fallbackMode(true)
                .modelProvider("DriveSphere Seating & Fleet Capacity Engine")
                .build();
    }

    private boolean isTransactionalIntent(String message) {
        String lower = message.toLowerCase();
        return lower.contains("book ") || lower.contains("book the") || lower.contains("booking")
                || lower.contains("reserve") || lower.contains("reservation") || lower.contains("my booking")
                || lower.contains("cancel my") || lower.contains("hire driver") || lower.contains("rent the");
    }

    private List<String> getContextualSuggestions(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("family") || lower.contains("car") || lower.contains("vehicle")) {
            return List.of(
                    "How does Driver Trust Score work?",
                    "Estimate fuel cost for Bangalore to Mysore",
                    "Can I rent an EV for a family trip?",
                    "What documents are needed for rental?"
            );
        } else if (lower.contains("driver") || lower.contains("trust")) {
            return List.of(
                    "Which car is best for family?",
                    "What is the daily rate for a driver?",
                    "How are drivers verified?",
                    "Can I hire a driver for outstation trips?"
            );
        } else if (lower.contains("ev") || lower.contains("electric")) {
            return List.of(
                    "How much does EV electricity cost per kWh?",
                    "Bangalore to Mysore EV charging stops",
                    "Which car is best for family?",
                    "How does EV range validation work?"
            );
        } else if (lower.contains("route") || lower.contains("trip") || lower.contains("fuel") || lower.contains("cost")) {
            return List.of(
                    "Compare EV vs Diesel cost for Bangalore to Mysore",
                    "Can I hire a verified driver for my trip?",
                    "Which car is best for highway trips?",
                    "How does live destination weather work?"
            );
        }
        return List.of(
                "Which car is best for family?",
                "How does Driver Trust Score work?",
                "Can I rent an EV?",
                "Bangalore to Mysore route cost"
        );
    }

    private String generateChatbotFallback(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("family") || lower.contains("group") || lower.contains("passenger") || lower.contains("seat") || lower.contains("7 seater") || lower.contains("kids")) {
            return "For family roadtrips (5 to 7 passengers), our top recommendation is the **Mahindra XUV700 AX7 Luxury** (7 seats, Rs. 3,600/day) or the **Toyota Fortuner Legender 4x4** (7 seats, Rs. 4,800/day). Both provide spacious 3-row seating, panoramic views, ADAS safety, and expansive luggage space. For groups larger than 7, our AI Fleet Matcher automatically schedules a multi-vehicle convoy!";
        } else if (lower.contains("driver") || lower.contains("hire") || lower.contains("chauffeur") || lower.contains("trust score")) {
            return "At DriveSphere, you can hire verified professional drivers starting at Rs. 950/day. Each driver is evaluated through our **Driver Trust Score (0-100)** based on: Document Verification (up to 25 pts), Professional Experience (up to 20 pts), Customer Ratings (up to 25 pts), Trip Completion (up to 20 pts), and Cancellation/Incident Deductions (up to -10 pts). Only approved drivers with verified status can be assigned to customer trips.";
        } else if (lower.contains("electric") || lower.contains("ev") || lower.contains("charging") || lower.contains("battery") || lower.contains("nexon")) {
            return "Our electric fleet includes the **Tata Nexon EV Empowered** (312 km range) and **Hyundai Ioniq 5 EV** (400+ km range). Trip energy consumption is calculated strictly in **kWh** (at Rs. 12.00/kWh) rather than fuel litres. Our Smart Trip Planner also monitors your remaining battery range and recommends highway charging stops.";
        } else if (lower.contains("car") || lower.contains("vehicle") || lower.contains("fleet") || lower.contains("recommend") || lower.contains("suv") || lower.contains("sedan") || lower.contains("luxury")) {
            return "Our operational fleet features verified vehicles across 4 categories: **ELECTRIC** (Tata Nexon EV, 312 km range, Rs. 2,800/day), **SUV** (Mahindra XUV700, 7 seats, Rs. 3,600/day & Toyota Fortuner 4x4, Rs. 4,800/day), **SEDAN** (Honda City e:HEV Hybrid, 27.1 km/l, Rs. 2,400/day), and **LUXURY** (BMW 330Li M Sport, Rs. 8,500/day). All vehicles include comprehensive insurance and real-time telemetry.";
        } else if (lower.contains("document") || lower.contains("requirement") || lower.contains("license") || lower.contains("eligibility") || lower.contains("age")) {
            return "To rent a self-drive vehicle, you need: (1) A valid Government Driving License held for at least 1 year, (2) Government ID proof (Aadhaar or Passport), and (3) Minimum age of 21. If you hire a DriveSphere verified driver, no driver's license is required from the customer.";
        } else if (lower.contains("cancel") || lower.contains("refund") || lower.contains("policy")) {
            return "Cancellations made 24 hours prior to the scheduled trip start time receive a 100% full refund to the original payment method. Free rescheduling is available anytime before vehicle dispatch.";
        } else if (lower.contains("book") || lower.contains("payment") || lower.contains("rent") || lower.contains("razorpay")) {
            return "Booking is 100% digital: Choose your vehicle, select rental dates, optionally add an approved driver, and complete payment in our Razorpay sandbox test environment. You'll receive instant booking confirmation and simulated GPS telemetry.";
        }
        return "Hello! I am your **DriveSphere AI Concierge**. I can assist you with vehicle recommendations, hiring certified drivers with Driver Trust Scores, trip routing with fuel/kWh estimates, and reservation policies. How may I help you today?";
    }

    @Transactional(readOnly = true)
    public TripPlannerResponse planTrip(TripPlannerRequest request) {
        if (request.getSource() == null || request.getSource().trim().isBlank()) {
            throw new IllegalArgumentException("Source departure location is required.");
        }
        if (request.getDestination() == null || request.getDestination().trim().isBlank()) {
            throw new IllegalArgumentException("Destination location is required.");
        }

        // 1. Authoritative Geocoding (throws clean IllegalArgumentException if unresolvable)
        GeocodeResult startGeo = mapsClient.geocode(request.getSource());
        GeocodeResult destGeo = mapsClient.geocode(request.getDestination());

        double startLat = startGeo.getLat();
        double startLng = startGeo.getLng();
        double destLat = destGeo.getLat();
        double destLng = destGeo.getLng();

        // 2. Real highway route calculation via OSRM / ORS
        RouteCalculationResult routeResult = mapsClient.calculateRoute(startLat, startLng, destLat, destLng);

        BigDecimal distanceKm = routeResult.getDistanceKm();
        int durationMinutes = routeResult.getDurationMinutes();
        int hours = durationMinutes / 60;
        int mins = durationMinutes % 60;
        String durationFormatted = hours > 0 ? String.format("%d hr %d min", hours, mins) : String.format("%d min", mins);

        // 3. Vehicle & Energy vs Fuel Calculations (Distinguish EV kWh vs ICE Litres)
        BigDecimal mileage = BigDecimal.valueOf(14.8); // Default km/l
        String vehName = "Standard Vehicle";
        Long vehId = null;
        boolean isElectric = false;
        String energyUnit = "L";

        if (request.getVehicleId() != null) {
            Vehicle v = vehicleRepository.findById(request.getVehicleId()).orElse(null);
            if (v != null) {
                vehId = v.getId();
                vehName = v.getBrand() + " " + v.getModel();
                isElectric = v.getFuelType() == FuelType.ELECTRIC;
                energyUnit = isElectric ? "kWh" : "L";

                if (v.getMileage() != null && v.getMileage().signum() > 0) {
                    mileage = v.getMileage();
                } else {
                    mileage = isElectric ? BigDecimal.valueOf(6.5) : BigDecimal.valueOf(14.8);
                }
            }
        }

        BigDecimal fuelUsed = distanceKm.divide(mileage, 2, RoundingMode.HALF_UP);
        BigDecimal fuelPrice = request.getFuelPricePerUnit() != null
                ? request.getFuelPricePerUnit()
                : (isElectric ? BigDecimal.valueOf(12.00) : BigDecimal.valueOf(102.50));
        BigDecimal estimatedFuelCost = fuelUsed.multiply(fuelPrice).setScale(2, RoundingMode.HALF_UP);

        // 4. Weather at actual destination coordinates
        WeatherSummaryDto weather = weatherClient.getWeather(destLat, destLng);
        weather.setLocationName(destGeo.getDisplayName());

        // 5. Nearby Places at destination
        List<PoiDto> pois = mapsClient.getNearbyPlaces(destLat, destLng);

        List<String> tips = List.of(
                isElectric ? "Fast DC charging hubs are available along the expressway corridor for quick top-ups." : "Maintain steady highway speeds (80-90 km/h) for up to 15% better fuel economy.",
                "Destination Forecast (" + destGeo.getDisplayName() + "): " + weather.getCondition() + " (" + weather.getTemperature() + "°C). " + weather.getForecastNotice(),
                "Verified 24/7 roadside assistance is active across this travel corridor."
        );

        return TripPlannerResponse.builder()
                .source(startGeo.getDisplayName())
                .destination(destGeo.getDisplayName())
                .destinationLocationName(destGeo.getDisplayName())
                .distanceKm(distanceKm)
                .durationMinutes(durationMinutes)
                .durationFormatted(durationFormatted)
                .vehicleId(vehId)
                .vehicleName(vehName)
                .vehicleMileage(mileage)
                .fuelUsedUnits(fuelUsed)
                .fuelPricePerUnit(fuelPrice)
                .estimatedFuelCost(estimatedFuelCost)
                .energyUnit(energyUnit)
                .isElectric(isElectric)
                .routeCoordinates(routeResult.getCoordinates())
                .destinationWeather(weather)
                .nearbyPlaces(pois)
                .travelTips(tips)
                .build();
    }
}
