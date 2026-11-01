Add-Type -AssemblyName System.Drawing

function Render-CodeSnippet {
    param(
        [string]$code,
        [string]$outputPath,
        [int]$width = 1100
    )

    $lines = $code -split "`r?`n"
    $lineHeight = 24
    $paddingTop = 20
    $paddingLeft = 24
    $height = ($lines.Length * $lineHeight) + ($paddingTop * 2)

    $bmp = New-Object System.Drawing.Bitmap($width, $height)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#1e1e1e"))
    $g.FillRectangle($bgBrush, 0, 0, $width, $height)

    $font = New-Object System.Drawing.Font("Consolas", 12.0, [System.Drawing.FontStyle]::Regular)
    $fontBold = New-Object System.Drawing.Font("Consolas", 12.0, [System.Drawing.FontStyle]::Bold)

    $brushDefault = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#d4d4d4"))
    $brushKeyword = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#c586c0")) # purple/pink keywords
    $brushType = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#4ec9b0")) # cyan/teal types
    $brushMethod = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#dcdcaa")) # yellow methods
    $brushString = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#ce9178")) # orange strings
    $brushComment = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#6a9955")) # green comments
    $brushNumber = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#b5cea8")) # light green numbers
    $brushAnnotation = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#569cd6")) # blue annotations

    $keywords = @("public", "private", "protected", "class", "void", "return", "new", "if", "else", "for", "while", "throw", "import", "package", "boolean", "int", "long", "double", "final", "static", "this", "null", "true", "false", "default")
    $types = @("PaymentOrderResponse", "PaymentVerifyRequest", "Payment", "Booking", "Vehicle", "Driver", "Trip", "GpsLog", "TripDto", "GpsUpdateDto", "TripPlannerResponse", "TripPlannerRequest", "AiChatResponse", "AiChatRequest", "AiRecommendationResponse", "AiRecommendationRequest", "DriverVerificationDto", "DriverTrustScore", "DriverVerification", "FleetDashboardDto", "VerificationStatus", "VehicleStatus", "BookingStatus", "PaymentStatus", "TrustLevel", "BigDecimal", "String", "List", "Optional", "Instant", "LocalDateTime", "Map")

    $y = $paddingTop
    foreach ($line in $lines) {
        $x = $paddingLeft
        # Tokenize by regex preserving spaces and punctuation
        $tokens = [regex]::Matches($line, '("[^"]*"|//.*|@[A-Za-z0-9_]+|[A-Za-z0-9_]+|\s+|[^\s\w])')
        foreach ($token in $tokens) {
            $val = $token.Value
            $brush = $brushDefault
            $useFont = $font

            if ($val.StartsWith("//")) {
                $brush = $brushComment
            } elseif ($val.StartsWith('"')) {
                $brush = $brushString
            } elseif ($val.StartsWith("@")) {
                $brush = $brushAnnotation
                $useFont = $fontBold
            } elseif ($keywords -contains $val) {
                $brush = $brushKeyword
                $useFont = $fontBold
            } elseif ($types -contains $val) {
                $brush = $brushType
            } elseif ($val -match '^\d+(\.\d+)?$') {
                $brush = $brushNumber
            }

            # Use Graphics.MeasureString for character fidelity
            $g.DrawString($val, $useFont, $brush, $x, $y)
            $stringSize = $g.MeasureString($val, $useFont, [System.Drawing.PointF]::new(0, 0), [System.Drawing.StringFormat]::GenericTypographic)
            $x += $stringSize.Width
        }
        $y += $lineHeight
    }

    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    Write-Host "Generated: $outputPath"
}

# --- Code 1: Payment Integration (10.4) ---
$code_10_4 = @"
    @Transactional
    public PaymentOrderResponse createPaymentOrder(Long bookingId, Long currentUserId, boolean isStaff) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!isStaff && currentUserId != null) {
            if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Unauthorized: You do not own this booking.");
            }
        }
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Booking is already paid.");
        }

        PaymentOrderResponse response = razorpayClient.createOrder(
                booking.getTotalAmount(),
                "INR",
                booking.getBookingReference()
        );

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalAmount())
                .currency("INR")
                .gateway(response.getGateway())
                .gatewayOrderId(response.getOrderId())
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        return response;
    }
"@
Render-CodeSnippet -code $code_10_4 -outputPath "docs/report_assets/chapter10/Fig_10_4_Payment_Integration.png"

# --- Code 2: AI Concierge & Fleet Matcher (10.5) ---
$code_10_5 = @"
    @Transactional(readOnly = true)
    public AiRecommendationResponse recommendVehicles(AiRecommendationRequest request) {
        int requestedPassengers = request.getPassengers() != null && request.getPassengers() > 0
                ? request.getPassengers() : 1;

        // Retrieve genuine operational available fleet from database (source of truth)
        List<Vehicle> operational;
        if (request.getStartDateTime() != null && request.getEndDateTime() != null) {
            operational = vehicleRepository.findAvailableVehiclesForPeriod(request.getStartDateTime(), request.getEndDateTime());
        } else {
            operational = vehicleRepository.findOperationalAvailableVehicles();
        }

        // Hard constraint: Never fall back to findAll(). Strict validation against total fleet capacity
        if (operational == null || operational.isEmpty()) {
            return AiRecommendationResponse.builder()
                    .requestedPassengers(requestedPassengers)
                    .totalAvailableFleetCapacity(0)
                    .status("NO_FLEET_AVAILABLE")
                    .recommendations(Collections.emptyList())
                    .summaryExplanation("No operational vehicles are available in the active fleet.")
                    .build();
        }

        int totalFleetCapacity = operational.stream().mapToInt(Vehicle::getSeatingCapacity).sum();
        if (requestedPassengers > totalFleetCapacity) {
            return AiRecommendationResponse.builder()
                    .requestedPassengers(requestedPassengers)
                    .totalAvailableFleetCapacity(totalFleetCapacity)
                    .status("NO_FLEET_AVAILABLE")
                    .build();
        }

        return buildRecommendations(operational, requestedPassengers, request.getMaxBudgetPerDay());
    }
"@
Render-CodeSnippet -code $code_10_5 -outputPath "docs/report_assets/chapter10/Fig_10_5_AI_Concierge.png"

# --- Code 3: Smart Trip Planning (10.6) ---
$code_10_6 = @"
    @Transactional(readOnly = true)
    public TripPlannerResponse planTrip(TripPlannerRequest request) {
        // 1. Authoritative Geocoding for Origin and Destination
        GeocodeResult startGeo = mapsClient.geocode(request.getSource());
        GeocodeResult destGeo = mapsClient.geocode(request.getDestination());

        // 2. Real highway route calculation via OSRM / ORS
        RouteCalculationResult routeResult = mapsClient.calculateRoute(
                startGeo.getLat(), startGeo.getLng(), destGeo.getLat(), destGeo.getLng());

        BigDecimal distanceKm = routeResult.getDistanceKm();
        int durationMinutes = routeResult.getDurationMinutes();

        // 3. Vehicle Energy vs Fuel Calculations (Distinguish EV kWh vs ICE Litres)
        boolean isElectric = false;
        BigDecimal mileage = BigDecimal.valueOf(14.8);
        if (request.getVehicleId() != null) {
            Vehicle v = vehicleRepository.findById(request.getVehicleId()).orElse(null);
            if (v != null) {
                isElectric = v.getFuelType() == FuelType.ELECTRIC;
                mileage = (v.getMileage() != null && v.getMileage().signum() > 0) ? v.getMileage() : mileage;
            }
        }

        BigDecimal fuelUsed = distanceKm.divide(mileage, 2, RoundingMode.HALF_UP);
        BigDecimal fuelPrice = request.getFuelPricePerUnit() != null ? request.getFuelPricePerUnit()
                : (isElectric ? BigDecimal.valueOf(12.00) : BigDecimal.valueOf(102.50));
        BigDecimal estimatedFuelCost = fuelUsed.multiply(fuelPrice).setScale(2, RoundingMode.HALF_UP);

        // 4. Live destination weather and nearby highway amenities
        WeatherSummaryDto weather = weatherClient.getWeather(destGeo.getLat(), destGeo.getLng());
        List<PoiDto> pois = mapsClient.getNearbyPlaces(destGeo.getLat(), destGeo.getLng());

        return TripPlannerResponse.builder()
                .distanceKm(distanceKm)
                .durationMinutes(durationMinutes)
                .fuelUsedUnits(fuelUsed)
                .estimatedFuelCost(estimatedFuelCost)
                .isElectric(isElectric)
                .routeCoordinates(routeResult.getCoordinates())
                .destinationWeather(weather)
                .nearbyPlaces(pois)
                .build();
    }
"@
Render-CodeSnippet -code $code_10_6 -outputPath "docs/report_assets/chapter10/Fig_10_6_Smart_Trip_Planning.png"

# --- Code 4: GPS Tracking (10.7) ---
$code_10_7 = @"
    @Transactional
    public TripDto updateGpsLocation(GpsUpdateDto dto, Long currentUserId, boolean isStaff) {
        double lat = dto.getLatitude().doubleValue();
        double lng = dto.getLongitude().doubleValue();
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Invalid geographic coordinate bounds.");
        }

        Trip trip = tripRepository.findById(dto.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + dto.getTripId()));

        if (trip.getStatus() == TripStatus.COMPLETED) {
            throw new IllegalStateException("Cannot update location for a completed trip.");
        }

        trip.setCurrentLatitude(dto.getLatitude());
        trip.setCurrentLongitude(dto.getLongitude());

        Vehicle vehicle = trip.getBooking() != null ? trip.getBooking().getVehicle() : null;
        if (vehicle != null) {
            vehicle.setLatitude(dto.getLatitude());
            vehicle.setLongitude(dto.getLongitude());
            vehicleRepository.save(vehicle);

            GpsLog logEntry = GpsLog.builder()
                    .trip(trip)
                    .vehicle(vehicle)
                    .latitude(dto.getLatitude())
                    .longitude(dto.getLongitude())
                    .speedKmh(dto.getSpeedKmh())
                    .recordedAt(Instant.now())
                    .build();
            gpsLogRepository.save(logEntry);
        }

        return mapper.toTripDto(tripRepository.save(trip));
    }
"@
Render-CodeSnippet -code $code_10_7 -outputPath "docs/report_assets/chapter10/Fig_10_7_GPS_Tracking.png"

# --- Code 5: Driver Verification & Trust Score (10.8) ---
$code_10_8 = @"
    @Transactional
    public DriverTrustScore calculateAndSaveTrustScore(Driver driver) {
        // 1. Verification Factor (Max 25 pts)
        BigDecimal verificationFactor = BigDecimal.ZERO;
        if (driver.getVerification() != null && driver.getVerification().getStatus() == VerificationStatus.APPROVED) {
            verificationFactor = BigDecimal.valueOf(25.00);
        }

        // 2. Experience Factor (Max 20 pts: ~3.5 pts/yr, capped at 20)
        int expYears = driver.getExperienceYears() != null ? driver.getExperienceYears() : 0;
        BigDecimal experienceFactor = BigDecimal.valueOf(Math.min(20.0, expYears * 3.5)).setScale(2, RoundingMode.HALF_UP);

        // 3. Customer Rating Factor (Max 25 pts: proportional to 5-star rating)
        BigDecimal ratingFactor = BigDecimal.ZERO;
        if (driver.getRating() != null) {
            ratingFactor = driver.getRating().divide(BigDecimal.valueOf(5.0), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(25.0)).setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Trip Completion Factor (Max 20 pts)
        int totalTrips = driver.getTotalTrips() != null ? driver.getTotalTrips() : 0;
        BigDecimal completionFactor = totalTrips >= 50 ? BigDecimal.valueOf(20.00)
                : totalTrips >= 20 ? BigDecimal.valueOf(18.00)
                : totalTrips >= 5 ? BigDecimal.valueOf(14.00)
                : totalTrips > 0 ? BigDecimal.valueOf(8.00) : BigDecimal.ZERO;

        // 5. Incident / Cancellation Penalty (Max 10 pts penalty)
        BigDecimal cancellationRate = driver.getCancellationRate() != null ? driver.getCancellationRate() : BigDecimal.ZERO;
        BigDecimal incidentPenalty = cancellationRate.multiply(BigDecimal.valueOf(0.5)).min(BigDecimal.valueOf(10.00));

        // Composite Overall Score (0 to 100)
        BigDecimal overallScore = verificationFactor.add(experienceFactor).add(ratingFactor)
                .add(completionFactor).subtract(incidentPenalty)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100.00)).setScale(2, RoundingMode.HALF_UP);

        DriverTrustScore trustScore = trustScoreRepository.findByDriver(driver)
                .orElse(DriverTrustScore.builder().driver(driver).build());
        trustScore.setOverallScore(overallScore);
        return trustScoreRepository.save(trustScore);
    }
"@
Render-CodeSnippet -code $code_10_8 -outputPath "docs/report_assets/chapter10/Fig_10_8_Driver_Verification.png"

# --- Code 6: Fleet Management & Analytics (10.9) ---
$code_10_9 = @"
    @Transactional(readOnly = true)
    public FleetDashboardDto getFleetDashboard() {
        long totalVehicles = vehicleRepository.count();
        long availableVehicles = vehicleRepository.countByStatus(VehicleStatus.AVAILABLE);
        long rentedVehicles = vehicleRepository.countByStatus(VehicleStatus.RENTED) +
                vehicleRepository.countByStatus(VehicleStatus.BOOKED);
        long maintenanceVehicles = vehicleRepository.countByStatus(VehicleStatus.MAINTENANCE);

        // Utilization Rate % = (rented / operational) * 100
        BigDecimal utilizationRate = BigDecimal.ZERO;
        long operational = totalVehicles - vehicleRepository.countByStatus(VehicleStatus.INACTIVE);
        if (operational > 0) {
            utilizationRate = BigDecimal.valueOf((double) rentedVehicles / operational * 100.0)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        BigDecimal totalRevenue = bookingRepository.sumTotalPaidRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long totalBookings = bookingRepository.count();
        long activeBookings = bookingRepository.countByBookingStatus(BookingStatus.ACTIVE);
        long pendingVerifications = verificationRepository.countByStatus(VerificationStatus.PENDING) +
                verificationRepository.countByStatus(VerificationStatus.UNDER_REVIEW);
        long upcomingMaintenance = maintenanceRepository.findUpcomingMaintenance(LocalDate.now().plusDays(14)).size();
        long activeDrivers = driverRepository.findByAvailableTrue().size();

        return FleetDashboardDto.builder()
                .totalVehicles(totalVehicles)
                .availableVehicles(availableVehicles)
                .rentedVehicles(rentedVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .utilizationRatePercent(utilizationRate)
                .totalRevenue(totalRevenue)
                .totalBookings(totalBookings)
                .activeBookings(activeBookings)
                .pendingVerifications(pendingVerifications)
                .upcomingMaintenanceCount(upcomingMaintenance)
                .activeDrivers(activeDrivers)
                .build();
    }
"@
Render-CodeSnippet -code $code_10_9 -outputPath "docs/report_assets/chapter10/Fig_10_9_Fleet_Analytics.png"
