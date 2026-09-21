/**
 * DriveSphere – Smart Mobility & Fleet Intelligence Platform
 * Customer-Facing Public Portal Application Logic
 */

document.addEventListener('DOMContentLoaded', () => {
    initAuthNavbarState();
    initPublicVehicleCatalog();
    initPublicTripPlanner();
});

// ===================================================================
// 1. Navbar Session & Auth State Management
// ===================================================================
function initAuthNavbarState() {
    const authControls = document.getElementById('navbar-auth-controls');
    if (!authControls) return;

    if (typeof Auth !== 'undefined' && Auth.isLoggedIn()) {
        const user = Auth.getUser();
        const role = user.primaryRole || (user.roles && user.roles[0]) || 'ROLE_CUSTOMER';
        
        let portalUrl = 'customer/index.html';
        let portalLabel = 'My Workspace';
        let badgeColor = 'bg-info text-dark';

        if (role === 'ROLE_ADMIN') {
            portalUrl = 'admin/index.html';
            portalLabel = 'Admin Governance';
            badgeColor = 'bg-danger text-white';
        } else if (role === 'ROLE_FLEET_MANAGER') {
            portalUrl = 'fleet-manager/index.html';
            portalLabel = 'Fleet Intelligence';
            badgeColor = 'bg-primary text-white';
        } else if (role === 'ROLE_DRIVER') {
            portalUrl = 'driver/index.html';
            portalLabel = 'Driver Console';
            badgeColor = 'bg-warning text-dark';
        }

        authControls.innerHTML = `
            <div class="dropdown">
                <button class="btn btn-outline-light btn-sm dropdown-toggle d-flex align-items-center gap-2" type="button" data-bs-toggle="dropdown">
                    <img src="${user.avatarUrl || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80'}" class="rounded-circle" width="24" height="24" style="object-fit: cover;">
                    <span>${user.firstName || 'My Account'}</span>
                    <span class="badge ${badgeColor} ms-1" style="font-size: 0.68rem;">${role.replace('ROLE_', '')}</span>
                </button>
                <ul class="dropdown-menu dropdown-menu-dark dropdown-menu-end shadow-lg">
                    <li><a class="dropdown-item py-2 fw-semibold text-info" href="${portalUrl}"><i class="bi bi-speedometer2 me-2"></i>${portalLabel}</a></li>
                    <li><hr class="dropdown-divider border-secondary"></li>
                    <li><a class="dropdown-item py-2 text-danger" href="#" onclick="Auth.logout()"><i class="bi bi-box-arrow-right me-2"></i>Sign Out</a></li>
                </ul>
            </div>
        `;
    }
}

// ===================================================================
// 2. Public Vehicle Catalog & Dynamic Search / Filters
// ===================================================================
let allVehiclesCache = [];

async function initPublicVehicleCatalog() {
    const grid = document.getElementById('public-vehicles-grid');
    if (!grid) return;

    grid.innerHTML = `
        <div class="col-12 text-center py-5">
            <span class="spinner-border text-primary spinner-border-sm me-2"></span>
            <span class="text-secondary">Loading operational fleet from DriveSphere database...</span>
        </div>
    `;

    try {
        const response = await fetch('http://localhost:8080/api/vehicles/available', {
            headers: { 'Accept': 'application/json' }
        });

        if (!response.ok) {
            throw new Error(`Server returned status ${response.status}`);
        }

        const resData = await response.json();
        allVehiclesCache = resData.data || [];
        renderVehicleCards(allVehiclesCache);
    } catch (err) {
        console.error('Could not connect to vehicles API:', err);
        grid.innerHTML = `
            <div class="col-12 text-center py-5">
                <div class="p-4 bg-dark rounded-4 border border-secondary d-inline-block shadow-sm" style="max-width: 480px;">
                    <i class="bi bi-exclamation-circle text-warning fs-1 d-block mb-2"></i>
                    <h5 class="text-white mb-2">Unable to load vehicles right now</h5>
                    <p class="text-secondary small mb-3">Please check your network connectivity and try again.</p>
                    <button class="btn btn-primary btn-sm px-4 py-2 fw-semibold" onclick="initPublicVehicleCatalog()">
                        <i class="bi bi-arrow-clockwise me-1"></i> Retry
                    </button>
                </div>
            </div>
        `;
    }

    // Bind Category Filter Pills
    const filterPills = document.querySelectorAll('#fleet-category-pills button');
    filterPills.forEach(btn => {
        btn.addEventListener('click', () => {
            filterPills.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const category = btn.getAttribute('data-filter');
            applyVehicleFilter(category);
        });
    });

    // Bind Hero Search Widget
    const searchForm = document.getElementById('hero-search-form');
    if (searchForm) {
        searchForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const category = document.getElementById('quick-search-category').value;
            applyVehicleFilter(category);
            
            // Smooth scroll down to vehicles grid
            const vehiclesSection = document.getElementById('vehicles-section');
            if (vehiclesSection) {
                vehiclesSection.scrollIntoView({ behavior: 'smooth' });
            }
        });
    }
}

function applyVehicleFilter(category) {
    if (!category) {
        renderVehicleCards(allVehiclesCache);
    } else {
        const filtered = allVehiclesCache.filter(v => (v.vehicleType || '').toUpperCase() === category.toUpperCase());
        renderVehicleCards(filtered);
    }
}

function renderVehicleCards(vehicles) {
    const grid = document.getElementById('public-vehicles-grid');
    if (!grid) return;

    if (!vehicles || vehicles.length === 0) {
        grid.innerHTML = `
            <div class="col-12 text-center py-5">
                <i class="bi bi-car-front fs-1 d-block mb-2 text-info"></i>
                <h5 class="text-white">No vehicles found in this category</h5>
                <p class="small text-secondary">Try selecting "All Fleet" to view all currently operational vehicles.</p>
            </div>
        `;
        return;
    }

    const defaultImages = [
        "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=600&q=80",
        "https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=600&q=80",
        "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=600&q=80",
        "https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=600&q=80",
        "https://images.unsplash.com/photo-1580273916550-e323be2ae537?auto=format&fit=crop&w=600&q=80"
    ];

    grid.innerHTML = vehicles.map((v, idx) => {
        const imgUrl = v.primaryImageUrl || defaultImages[idx % defaultImages.length];
        const isEV = (v.vehicleType || '').toUpperCase() === 'ELECTRIC' || (v.fuelType || '').toUpperCase() === 'ELECTRIC';
        const typeBadge = isEV ? 'bg-info bg-opacity-25 text-info' : 'bg-primary bg-opacity-25 text-primary';
        const mileageLabel = isEV ? (v.mileage ? `${v.mileage} km Range` : '312 km Range') : (v.mileage ? `${v.mileage} km/l` : 'High Efficiency');
        
        return `
            <div class="col-md-6 col-lg-4">
                <div class="card bg-surface border-secondary rounded-4 overflow-hidden h-100 shadow-sm hover-elevate">
                    <div class="position-relative">
                        <img src="${imgUrl}" class="card-img-top" style="height: 210px; object-fit: cover;" alt="${v.displayName || v.model}">
                        <span class="position-absolute top-0 start-0 m-3 badge ${typeBadge} border border-secondary">
                            ${v.vehicleType || 'SEDAN'}
                        </span>
                        <span class="position-absolute top-0 end-0 m-3 badge bg-success shadow-sm">
                            <i class="bi bi-check-circle-fill me-1"></i>Available
                        </span>
                    </div>
                    <div class="card-body p-4 d-flex flex-column justify-content-between">
                        <div>
                            <div class="d-flex justify-content-between align-items-center mb-1">
                                <span class="small text-secondary fw-semibold">${v.brand || 'DriveSphere'}</span>
                                <span class="text-warning small"><i class="bi bi-star-fill me-1"></i>${v.averageRating ? v.averageRating.toFixed(1) : '4.9'}★</span>
                            </div>
                            <h5 class="fw-bold text-white mb-2">${v.displayName || `${v.brand} ${v.model}`}</h5>
                            
                            <!-- Vehicle Specs Grid -->
                            <div class="row g-2 mb-3 text-secondary small">
                                <div class="col-6"><i class="bi bi-people text-info me-1"></i> ${v.seatingCapacity || 5} Seats</div>
                                <div class="col-6"><i class="bi bi-fuel-pump text-warning me-1"></i> ${v.fuelType || 'Electric'}</div>
                                <div class="col-6"><i class="bi bi-gear text-secondary me-1"></i> ${v.transmission || 'Automatic'}</div>
                                <div class="col-6"><i class="bi bi-speedometer text-success me-1"></i> ${mileageLabel}</div>
                            </div>
                        </div>

                        <div class="pt-3 border-top border-secondary d-flex justify-content-between align-items-center">
                            <div>
                                <span class="text-secondary small d-block" style="font-size: 0.75rem;">Daily Rate</span>
                                <span class="fs-5 fw-bold text-white">₹${v.rentalPricePerDay ? Number(v.rentalPricePerDay).toLocaleString('en-IN') : '2,800'} <small class="text-secondary fs-7">/day</small></span>
                            </div>
                            <button type="button" class="btn btn-primary btn-sm px-3 py-2 fw-semibold" onclick="handlePublicBookNow(${v.id})">
                                <i class="bi bi-calendar-check me-1"></i>Book Now
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function handlePublicBookNow(vehicleId) {
    const isAuth = (typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false;
    if (isAuth) {
        window.location.href = `customer/index.html?vehicleId=${vehicleId}&action=book`;
    } else {
        const returnTo = encodeURIComponent(`customer/index.html?vehicleId=${vehicleId}&action=book`);
        const msg = encodeURIComponent('Please sign in to continue with your booking.');
        window.location.href = `login.html?returnTo=${returnTo}&message=${msg}`;
    }
}

function handlePublicHireDriver(driverId) {
    const isAuth = (typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false;
    if (isAuth) {
        window.location.href = `customer/index.html?driverId=${driverId || ''}&action=hire_driver`;
    } else {
        const returnTo = encodeURIComponent(`customer/index.html?driverId=${driverId || ''}&action=hire_driver`);
        const msg = encodeURIComponent('Please sign in to hire a verified driver.');
        window.location.href = `login.html?returnTo=${returnTo}&message=${msg}`;
    }
}

// ===================================================================
// 3. Smart Trip Planner (Interactive Leaflet Map & Weather)
// ===================================================================
let publicMap = null;
let publicRoutePolyline = null;
let startMarker = null;
let destMarker = null;

function initPublicTripPlanner() {
    const mapElement = document.getElementById('public-planner-map');
    if (!mapElement) return;

    // Bangalore Coordinates
    const blrCoords = [12.9716, 77.5946];
    // Mysore Coordinates
    const mysCoords = [12.2958, 76.6394];

    publicMap = L.map('public-planner-map', {
        zoomControl: true,
        attributionControl: true
    }).setView([12.6337, 77.1170], 9);

    // Free OpenStreetMap Standard Tile Layer (Zero API key required, visible attribution)
    const osmTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
    });
    osmTileLayer.on('tileerror', () => {
        console.warn('OpenStreetMap tile layer temporarily delayed.');
    });
    osmTileLayer.addTo(publicMap);

    // Initial markers for Bangalore -> Mysore corridor
    startMarker = L.circleMarker(blrCoords, {
        radius: 8,
        color: '#00f2fe',
        fillColor: '#00f2fe',
        fillOpacity: 1
    }).addTo(publicMap).bindPopup('<b class="text-white">Pickup: Bangalore Hub</b>');

    destMarker = L.circleMarker(mysCoords, {
        radius: 8,
        color: '#10b981',
        fillColor: '#10b981',
        fillOpacity: 1
    }).addTo(publicMap).bindPopup('<b class="text-white">Destination: Mysore Hub</b>');

    // Route Polyline (Bangalore - Ramanagara - Mandya - Mysore)
    const waypoints = [
        [12.9716, 77.5946],
        [12.8750, 77.4950],
        [12.7230, 77.2800],
        [12.5250, 76.8980],
        [12.4150, 76.7500],
        [12.2958, 76.6394]
    ];

    publicRoutePolyline = L.polyline(waypoints, {
        color: '#00f2fe',
        weight: 4,
        opacity: 0.85,
        dashArray: '8, 8'
    }).addTo(publicMap);

    publicMap.fitBounds(publicRoutePolyline.getBounds(), { padding: [30, 30] });

    // Handle Vehicle selection change to dynamically toggle EV vs ICE pricing and labels
    const vehSelectEl = document.getElementById('planner-veh-select');
    const fuelLabelEl = document.getElementById('planner-fuel-label');
    const fuelPriceEl = document.getElementById('planner-fuel-price');
    const energyLabelEl = document.getElementById('pub-energy-label');
    const costLabelEl = document.getElementById('pub-cost-label');
    const evRangeCard = document.getElementById('pub-ev-range-card');

    if (vehSelectEl && fuelLabelEl && fuelPriceEl) {
        vehSelectEl.addEventListener('change', () => {
            const selectedOpt = vehSelectEl.options[vehSelectEl.selectedIndex];
            const isEV = (selectedOpt.getAttribute('data-type') || '').toUpperCase() === 'ELECTRIC';
            
            if (isEV) {
                fuelLabelEl.innerText = 'Electricity Price (₹/kWh)';
                fuelPriceEl.value = '12.0';
                if (energyLabelEl) energyLabelEl.innerText = 'ENERGY USED';
                if (costLabelEl) costLabelEl.innerText = 'EST. ENERGY COST';
                if (evRangeCard) evRangeCard.classList.remove('d-none');
            } else {
                fuelLabelEl.innerText = 'Fuel Price (₹/L)';
                fuelPriceEl.value = '102.5';
                if (energyLabelEl) energyLabelEl.innerText = 'FUEL USED';
                if (costLabelEl) costLabelEl.innerText = 'EST. FUEL EXPENSE';
                if (evRangeCard) evRangeCard.classList.add('d-none');
            }
        });
    }

    // Fetch live weather for Mysore via Open-Meteo
    fetchDestinationWeather(12.2958, 76.6394);

    // Form submission recalculates route & costs via real backend API
    const tripForm = document.getElementById('public-trip-form');
    if (tripForm) {
        tripForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const source = document.getElementById('planner-source').value.trim();
            const dest = document.getElementById('planner-dest').value.trim();
            const fuelPrice = parseFloat(document.getElementById('planner-fuel-price').value) || 12.0;
            const vehSelect = document.getElementById('planner-veh-select').value;
            const selectedOpt = vehSelectEl.options[vehSelectEl.selectedIndex];
            const ratedRange = parseFloat(selectedOpt.getAttribute('data-range') || '312');
            const isElectric = (selectedOpt.getAttribute('data-type') || '').toUpperCase() === 'ELECTRIC';

            const submitBtn = tripForm.querySelector('button[type="submit"]');
            const originalText = submitBtn.innerHTML;
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Calculating Highway Route...';

            try {
                const response = await fetch('http://localhost:8080/api/ai/trip-assistant', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        source: source,
                        destination: dest,
                        vehicleId: vehSelect ? parseInt(vehSelect, 10) : 1,
                        fuelPricePerUnit: fuelPrice
                    })
                });

                const resData = await response.json();
                if (!response.ok) {
                    throw new Error(resData.message || 'Could not locate one of the selected locations.');
                }

                const plan = resData.data;

                document.getElementById('pub-trip-dist').innerText = `${plan.distanceKm} km`;
                document.getElementById('pub-trip-duration').innerText = plan.durationFormatted;
                
                const unit = plan.energyUnit || (plan.electric ? 'kWh' : 'L');
                if (energyLabelEl) energyLabelEl.innerText = unit === 'kWh' ? 'ENERGY USED' : 'FUEL USED';
                if (costLabelEl) costLabelEl.innerText = unit === 'kWh' ? 'EST. ENERGY COST' : 'EST. FUEL EXPENSE';
                
                document.getElementById('pub-trip-fuel').innerText = `${plan.fuelUsedUnits} ${unit}`;
                document.getElementById('pub-trip-cost').innerText = `₹${Number(plan.estimatedFuelCost).toLocaleString('en-IN')}`;

                // Update EV Range Validation
                if (isElectric && evRangeCard) {
                    evRangeCard.classList.remove('d-none');
                    const distKm = parseFloat(plan.distanceKm);
                    const remainingRange = Math.max(0, ratedRange - distKm);
                    
                    document.getElementById('pub-ev-req').innerText = `${distKm.toFixed(1)} km`;
                    document.getElementById('pub-ev-total').innerText = `${ratedRange.toFixed(1)} km`;
                    document.getElementById('pub-ev-rem').innerText = `${remainingRange.toFixed(1)} km`;

                    const statusBadge = document.getElementById('pub-ev-status');
                    const warningBox = document.getElementById('pub-ev-warning');

                    if (distKm > ratedRange) {
                        statusBadge.className = 'badge bg-danger';
                        statusBadge.innerText = 'Charging Stop Required';
                        if (warningBox) warningBox.classList.remove('d-none');
                    } else {
                        statusBadge.className = 'badge bg-success';
                        statusBadge.innerText = 'Range Sufficient';
                        if (warningBox) warningBox.classList.add('d-none');
                    }
                } else if (evRangeCard) {
                    evRangeCard.classList.add('d-none');
                }

                // Update destination weather
                if (plan.destinationWeather) {
                    const w = plan.destinationWeather;
                    const tempEl = document.getElementById('pub-weather-temp');
                    const condEl = document.getElementById('pub-weather-condition');
                    const advEl = document.getElementById('pub-weather-advisory');
                    const locLabel = document.getElementById('pub-weather-location');

                    if (locLabel) locLabel.innerText = plan.destinationLocationName || plan.destination;
                    if (tempEl) tempEl.innerText = `${Math.round(w.temperature)}°C`;
                    if (condEl) condEl.innerText = w.condition;
                    if (advEl) advEl.innerText = w.forecastNotice;
                }

                // Render Route Polyline on Public Map
                if (plan.routeCoordinates && plan.routeCoordinates.length > 0) {
                    if (publicRoutePolyline) publicMap.removeLayer(publicRoutePolyline);
                    publicRoutePolyline = L.polyline(plan.routeCoordinates, {
                        color: '#00f2fe',
                        weight: 4,
                        opacity: 0.9
                    }).addTo(publicMap);

                    const startCoord = plan.routeCoordinates[0];
                    const endCoord = plan.routeCoordinates[plan.routeCoordinates.length - 1];
                    if (startMarker) startMarker.setLatLng(startCoord);
                    if (destMarker) destMarker.setLatLng(endCoord);

                    publicMap.invalidateSize();
                    publicMap.fitBounds(publicRoutePolyline.getBounds(), { padding: [30, 30] });
                }
            } catch (err) {
                alert(err.message || 'Could not locate one of the selected locations. Please choose a more specific location.');
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalText;
            }
        });
    }
}

async function fetchDestinationWeather(lat, lng) {
    try {
        const res = await fetch(`http://localhost:8080/api/weather?lat=${lat}&lng=${lng}`);
        if (res.ok) {
            const data = await res.json();
            const w = data.data;
            if (w) {
                const tempEl = document.getElementById('pub-weather-temp');
                const condEl = document.getElementById('pub-weather-condition');
                const advEl = document.getElementById('pub-weather-advisory');

                if (tempEl) tempEl.innerText = `${Math.round(w.temperature || 24)}°C`;
                if (condEl) condEl.innerText = w.condition || 'Clear & Sunny';
                if (advEl) advEl.innerText = w.forecastNotice || 'Optimal road conditions for driving.';
            }
        }
    } catch (err) {
        console.warn('Weather endpoint standby:', err);
    }
}
