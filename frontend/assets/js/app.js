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
            portalLabel = 'Admin Dashboard';
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
// 2. Public Vehicle Catalog, Hero Carousel & Dynamic Search / Filters
// ===================================================================
const PLACEHOLDER_CAR_IMG = "data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%20800%20500%22%20width%3D%22100%25%22%20height%3D%22100%25%22%3E%3Crect%20width%3D%22800%22%20height%3D%22500%22%20fill%3D%22%230d1018%22%2F%3E%3Cpath%20d%3D%22M200%20320%20L260%20220%20L540%20220%20L600%20320%20Z%22%20fill%3D%22%231e293b%22%2F%3E%3Ccircle%20cx%3D%22270%22%20cy%3D%22330%22%20r%3D%2240%22%20fill%3D%22%2338bdf8%22%20opacity%3D%220.4%22%2F%3E%3Ccircle%20cx%3D%22530%22%20cy%3D%22330%22%20r%3D%2240%22%20fill%3D%22%2338bdf8%22%20opacity%3D%220.4%22%2F%3E%3Ctext%20x%3D%2250%25%22%20y%3D%22420%22%20text-anchor%3D%22middle%22%20fill%3D%22%2364748b%22%20font-family%3D%22sans-serif%22%20font-size%3D%2222%22%3EDriveSphere%20Fleet%3C%2Ftext%3E%3C%2Fsvg%3E";

let allVehiclesCache = [];
let hasBoundCatalogEvents = false;
let lastCatalogFetchTime = 0;
let heroCarouselInterval = null;
let heroCurrentIndex = 0;

async function initPublicVehicleCatalog() {
    const grid = document.getElementById('public-vehicles-grid');
    if (grid) {
        grid.innerHTML = `
            <div class="col-12 text-center py-5">
                <span class="spinner-border text-primary spinner-border-sm me-2"></span>
                <span class="text-secondary">Loading operational fleet from DriveSphere database...</span>
            </div>
        `;
    }

    try {
        const baseUrl = (typeof API !== 'undefined' && API.BASE_URL) ? API.BASE_URL : '/api';
        const response = await fetch(`${baseUrl}/vehicles/available`, {
            headers: { 'Accept': 'application/json' }
        });

        if (!response.ok) {
            throw new Error(`Server returned status ${response.status}`);
        }

        const resData = await response.json();
        allVehiclesCache = resData.data || [];
        lastCatalogFetchTime = Date.now();

        // 1. Render Hero Showcase Carousel
        renderHeroCarousel(allVehiclesCache);

        // 2. Render Featured Vehicles Cards
        renderVehicleCards(allVehiclesCache);

        // 3. Populate Trip Planner Vehicle Selector
        populatePlannerVehicleSelect(allVehiclesCache);
    } catch (err) {
        console.error('Could not connect to vehicles API:', err);
        if (grid) {
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
        renderHeroCarousel([]);
        populatePlannerVehicleSelect([]);
    }

    // Bind Filter Pills and Search Form only once
    if (!hasBoundCatalogEvents) {
        hasBoundCatalogEvents = true;

        // Category Filter Pills
        const filterPills = document.querySelectorAll('#fleet-category-pills button');
        filterPills.forEach(btn => {
            btn.addEventListener('click', () => {
                filterPills.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const category = btn.getAttribute('data-filter');
                applyVehicleFilter(category);
            });
        });

        // Hero Search Widget
        const searchForm = document.getElementById('hero-search-form');
        if (searchForm) {
            searchForm.addEventListener('submit', (e) => {
                e.preventDefault();
                const category = document.getElementById('quick-search-category').value;
                applyVehicleFilter(category);
                
                const vehiclesSection = document.getElementById('vehicles-section');
                if (vehiclesSection) {
                    vehiclesSection.scrollIntoView({ behavior: 'smooth' });
                }
            });
        }

        // Silent cache revalidation when user returns to this browser tab
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible' && Date.now() - lastCatalogFetchTime > 60000) {
                initPublicVehicleCatalog();
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

// -------------------------------------------------------------------
// Dynamic Hero Vehicle Showcase Carousel
// -------------------------------------------------------------------
function renderHeroCarousel(vehicles) {
    const mount = document.getElementById('hero-carousel-mount');
    if (!mount) return;

    if (heroCarouselInterval) {
        clearInterval(heroCarouselInterval);
        heroCarouselInterval = null;
    }

    if (!vehicles || vehicles.length === 0) {
        mount.innerHTML = `
            <div class="card bg-surface border-secondary shadow-lg rounded-4 p-5 text-center">
                <i class="bi bi-car-front fs-1 text-secondary mb-3 d-block"></i>
                <h4 class="fw-bold text-white mb-2">Fleet Updating</h4>
                <p class="text-secondary small mb-0">Our operational fleet is currently undergoing scheduled updates. Please check back shortly or explore our chauffeur concierge.</p>
            </div>
        `;
        return;
    }

    function buildHeroSlideMarkup(v) {
        const isEV = (v.vehicleType || '').toUpperCase() === 'ELECTRIC' || (v.fuelType || '').toUpperCase() === 'ELECTRIC';
        const typeBadge = isEV ? 'bg-info bg-opacity-25 text-info' : 'bg-primary bg-opacity-25 text-primary';
        const ratingHtml = (v.averageRating && v.totalRatings > 0)
            ? `<span class="text-warning small"><i class="bi bi-star-fill me-1"></i>${Number(v.averageRating).toFixed(1)} (${v.totalRatings} Ratings)</span>`
            : `<span class="badge bg-secondary-subtle text-secondary border border-secondary" style="font-size: 0.72rem;">New Fleet Addition</span>`;
        
        let rangeMileageHtml;
        if (isEV) {
            const range = v.rangeKm || v.mileage;
            rangeMileageHtml = range ? `${range} km Range` : 'Electric Powertrain';
        } else {
            rangeMileageHtml = v.mileage ? `${v.mileage} km/l` : `${v.fuelType || 'Fuel'} Efficiency`;
        }

        const title = v.displayName || `${v.brand} ${v.model} ${v.year ? `(${v.year})` : ''}`.trim();
        const description = v.description || `High-performance ${v.vehicleType || 'vehicle'} equipped for comfort, safety, and seamless highway mobility.`;
        const locationText = v.location ? `${v.location} Hub` : 'Main Hub';
        const price = v.rentalPricePerDay ? Number(v.rentalPricePerDay).toLocaleString('en-IN') : '—';
        const imgSrc = v.primaryImageUrl || PLACEHOLDER_CAR_IMG;

        return `
            <div class="hero-carousel-card">
                <div class="hero-carousel-img-container mb-3 position-relative">
                    <span class="position-absolute top-0 end-0 m-3 badge bg-success px-3 py-2 shadow z-2">
                        <i class="bi bi-lightning-charge-fill me-1"></i> Available Now
                    </span>
                    <img src="${imgSrc}" 
                         alt="${title}"
                         class="hero-carousel-img rounded-4" 
                         onerror="this.onerror=null; this.src='${PLACEHOLDER_CAR_IMG}'">
                </div>
                
                <div class="px-2">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <span class="badge ${typeBadge}">${v.vehicleType || 'SEDAN'}</span>
                        ${ratingHtml}
                    </div>
                    <h4 class="fw-bold text-white mb-1">${title}</h4>
                    <p class="text-muted small mb-3" style="min-height: 2.4rem;">${description}</p>
                    
                    <div class="row g-2 mb-3 small text-secondary">
                        <div class="col-6"><i class="bi bi-people text-info me-1"></i> ${v.seatingCapacity || 4} Passenger Seats</div>
                        <div class="col-6"><i class="bi bi-speedometer2 text-success me-1"></i> ${rangeMileageHtml}</div>
                        <div class="col-6"><i class="bi bi-gear text-secondary me-1"></i> ${v.transmission || 'Automatic'}</div>
                        <div class="col-6"><i class="bi bi-geo-alt text-danger me-1"></i> ${locationText}</div>
                    </div>

                    <div class="d-flex align-items-center justify-content-between pt-3 border-top border-secondary">
                        <div>
                            <div class="text-muted small">Daily Rental Rate</div>
                            <div class="fs-4 fw-bold text-white">₹${price} <span class="fs-6 text-muted font-normal">/ day</span></div>
                        </div>
                        <button type="button" class="btn btn-primary px-3 py-2 fw-semibold" onclick="handlePublicBookNow(${v.id})">
                            <i class="bi bi-calendar-check me-1"></i> Book Vehicle
                        </button>
                    </div>
                </div>
            </div>
        `;
    }

    // Single vehicle: render directly without rotation controls
    if (vehicles.length === 1) {
        mount.innerHTML = buildHeroSlideMarkup(vehicles[0]);
        return;
    }

    // Multiple vehicles: render interactive carousel with controls and dots
    const slidesHtml = vehicles.map((v, i) => `
        <div class="hero-carousel-slide" role="group" aria-roledescription="slide" aria-label="Vehicle ${i + 1} of ${vehicles.length}">
            ${buildHeroSlideMarkup(v)}
        </div>
    `).join('');

    const dotsHtml = vehicles.map((_, i) => `
        <button type="button" class="hero-carousel-dot ${i === 0 ? 'active' : ''}" data-slide="${i}" aria-label="Go to vehicle ${i + 1}" role="tab" aria-selected="${i === 0 ? 'true' : 'false'}"></button>
    `).join('');

    mount.innerHTML = `
        <div class="visually-hidden" id="hero-sr-status" aria-live="polite"></div>
        <button type="button" class="hero-carousel-nav-btn prev" id="hero-carousel-prev" aria-label="Previous Vehicle">
            <i class="bi bi-chevron-left"></i>
        </button>
        <button type="button" class="hero-carousel-nav-btn next" id="hero-carousel-next" aria-label="Next Vehicle">
            <i class="bi bi-chevron-right"></i>
        </button>
        <div class="hero-carousel-viewport" id="hero-carousel-viewport">
            <div class="hero-carousel-track" id="hero-carousel-track">
                ${slidesHtml}
            </div>
        </div>
        <div class="hero-carousel-dots" id="hero-carousel-dots" role="tablist" aria-label="Vehicle slides">
            ${dotsHtml}
        </div>
    `;

    const track = document.getElementById('hero-carousel-track');
    const dots = document.querySelectorAll('.hero-carousel-dot');
    const srStatus = document.getElementById('hero-sr-status');
    const prevBtn = document.getElementById('hero-carousel-prev');
    const nextBtn = document.getElementById('hero-carousel-next');
    const wrapper = document.getElementById('hero-carousel-wrapper');
    const total = vehicles.length;
    heroCurrentIndex = 0;

    function goToSlide(idx) {
        if (idx < 0) {
            heroCurrentIndex = total - 1;
        } else if (idx >= total) {
            heroCurrentIndex = 0;
        } else {
            heroCurrentIndex = idx;
        }

        if (track) {
            track.style.transform = `translateX(-${heroCurrentIndex * 100}%)`;
        }

        dots.forEach((dot, dIdx) => {
            const isActive = dIdx === heroCurrentIndex;
            dot.classList.toggle('active', isActive);
            dot.setAttribute('aria-selected', isActive ? 'true' : 'false');
        });

        if (srStatus && vehicles[heroCurrentIndex]) {
            const curVeh = vehicles[heroCurrentIndex];
            srStatus.textContent = `Showing vehicle ${heroCurrentIndex + 1} of ${total}: ${curVeh.brand} ${curVeh.model}`;
        }
    }

    function startAutoplay() {
        stopAutoplay();
        heroCarouselInterval = setInterval(() => {
            goToSlide(heroCurrentIndex + 1);
        }, 5000);
    }

    function stopAutoplay() {
        if (heroCarouselInterval) {
            clearInterval(heroCarouselInterval);
            heroCarouselInterval = null;
        }
    }

    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            goToSlide(heroCurrentIndex - 1);
            startAutoplay();
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            goToSlide(heroCurrentIndex + 1);
            startAutoplay();
        });
    }

    dots.forEach((dot) => {
        dot.addEventListener('click', () => {
            const targetIdx = parseInt(dot.getAttribute('data-slide'), 10);
            goToSlide(targetIdx);
            startAutoplay();
        });
    });

    if (wrapper) {
        wrapper.addEventListener('mouseenter', stopAutoplay);
        wrapper.addEventListener('mouseleave', startAutoplay);
        wrapper.addEventListener('focusin', stopAutoplay);
        wrapper.addEventListener('focusout', startAutoplay);

        // Keyboard arrow navigation
        wrapper.setAttribute('tabindex', '0');
        wrapper.addEventListener('keydown', (e) => {
            if (e.key === 'ArrowLeft') {
                e.preventDefault();
                goToSlide(heroCurrentIndex - 1);
                startAutoplay();
            } else if (e.key === 'ArrowRight') {
                e.preventDefault();
                goToSlide(heroCurrentIndex + 1);
                startAutoplay();
            }
        });

        // Mobile touch & swipe handling
        let touchStartX = 0;
        wrapper.addEventListener('touchstart', (e) => {
            if (e.touches && e.touches[0]) {
                touchStartX = e.touches[0].clientX;
            }
        }, { passive: true });

        wrapper.addEventListener('touchend', (e) => {
            if (e.changedTouches && e.changedTouches[0]) {
                const touchEndX = e.changedTouches[0].clientX;
                const diff = touchStartX - touchEndX;
                if (Math.abs(diff) > 40) {
                    if (diff > 0) {
                        goToSlide(heroCurrentIndex + 1);
                    } else {
                        goToSlide(heroCurrentIndex - 1);
                    }
                    startAutoplay();
                }
            }
        }, { passive: true });
    }

    startAutoplay();
}

// -------------------------------------------------------------------
// Trip Planner Vehicle Select Synchronization
// -------------------------------------------------------------------
function populatePlannerVehicleSelect(vehicles) {
    const vehSelectEl = document.getElementById('planner-veh-select');
    const submitBtn = document.getElementById('planner-calculate-btn');
    if (!vehSelectEl) return;

    if (!vehicles || vehicles.length === 0) {
        vehSelectEl.innerHTML = '<option value="">No vehicles currently available</option>';
        vehSelectEl.disabled = true;
        if (submitBtn) submitBtn.disabled = true;
        return;
    }

    vehSelectEl.disabled = false;
    if (submitBtn) submitBtn.disabled = false;

    vehSelectEl.innerHTML = vehicles.map(v => {
        const isEV = (v.vehicleType || '').toUpperCase() === 'ELECTRIC' || (v.fuelType || '').toUpperCase() === 'ELECTRIC';
        const rangeVal = v.rangeKm || v.mileage || 0;
        const specLabel = isEV
            ? (rangeVal ? `${rangeVal} km Range • Electric` : 'Electric')
            : (v.mileage ? `${v.mileage} km/l • ${v.fuelType || 'Fuel'}` : (v.fuelType || 'Standard'));
        return `<option value="${v.id}" data-type="${v.vehicleType || ''}" data-fuel="${v.fuelType || ''}" data-range="${rangeVal}">${v.brand} ${v.model} (${specLabel})</option>`;
    }).join('');

    // Trigger change event to initialize EV vs ICE price and range cards
    vehSelectEl.dispatchEvent(new Event('change'));
}

// -------------------------------------------------------------------
// Featured Vehicles Grid Renderer
// -------------------------------------------------------------------
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

    grid.innerHTML = vehicles.map((v) => {
        const imgUrl = v.primaryImageUrl || PLACEHOLDER_CAR_IMG;
        const isEV = (v.vehicleType || '').toUpperCase() === 'ELECTRIC' || (v.fuelType || '').toUpperCase() === 'ELECTRIC';
        const typeBadge = isEV ? 'bg-info bg-opacity-25 text-info' : 'bg-primary bg-opacity-25 text-primary';
        
        let mileageLabel;
        if (isEV) {
            const range = v.rangeKm || v.mileage;
            mileageLabel = range ? `${range} km Range` : 'Electric Powertrain';
        } else {
            mileageLabel = v.mileage ? `${v.mileage} km/l` : (v.fuelType || 'High Efficiency');
        }

        const ratingHtml = (v.averageRating && v.totalRatings > 0)
            ? `<span class="text-warning small"><i class="bi bi-star-fill me-1"></i>${Number(v.averageRating).toFixed(1)}★</span>`
            : `<span class="text-secondary small" style="font-size: 0.72rem;">New</span>`;

        const title = v.displayName || `${v.brand} ${v.model}`;
        const price = v.rentalPricePerDay ? Number(v.rentalPricePerDay).toLocaleString('en-IN') : '—';

        return `
            <div class="col-md-6 col-lg-4">
                <div class="card bg-surface border-secondary rounded-4 overflow-hidden h-100 shadow-sm hover-elevate">
                    <div class="position-relative">
                        <img src="${imgUrl}" 
                             class="card-img-top" 
                             style="height: 210px; object-fit: cover;" 
                             alt="${title}"
                             onerror="this.onerror=null; this.src='${PLACEHOLDER_CAR_IMG}'">
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
                                ${ratingHtml}
                            </div>
                            <h5 class="fw-bold text-white mb-2">${title}</h5>
                            
                            <!-- Vehicle Specs Grid -->
                            <div class="row g-2 mb-3 text-secondary small">
                                <div class="col-6"><i class="bi bi-people text-info me-1"></i> ${v.seatingCapacity || 4} Seats</div>
                                <div class="col-6"><i class="bi bi-fuel-pump text-warning me-1"></i> ${v.fuelType || 'Fuel'}</div>
                                <div class="col-6"><i class="bi bi-gear text-secondary me-1"></i> ${v.transmission || 'Automatic'}</div>
                                <div class="col-6"><i class="bi bi-speedometer text-success me-1"></i> ${mileageLabel}</div>
                            </div>
                        </div>

                        <div class="pt-3 border-top border-secondary d-flex justify-content-between align-items-center">
                            <div>
                                <span class="text-secondary small d-block" style="font-size: 0.75rem;">Daily Rate</span>
                                <span class="fs-5 fw-bold text-white">₹${price} <small class="text-secondary fs-7">/day</small></span>
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
    const isAuth = (typeof Auth !== 'undefined' && Auth.isLoggedIn) ? Auth.isLoggedIn() : ((typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false);
    if (isAuth) {
        window.location.href = `customer/index.html?vehicleId=${vehicleId}&action=book`;
    } else {
        const returnTo = encodeURIComponent(`customer/index.html?vehicleId=${vehicleId}&action=book`);
        const msg = encodeURIComponent('Please sign in to continue with your booking.');
        window.location.href = `login.html?returnTo=${returnTo}&message=${msg}`;
    }
}

function handlePublicHireDriver(driverId) {
    const isAuth = (typeof Auth !== 'undefined' && Auth.isLoggedIn) ? Auth.isLoggedIn() : ((typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false);
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

    // Defensive check: If Leaflet failed to load, render standby notice without crashing
    if (typeof L === 'undefined' || !L.map) {
        console.warn('Leaflet mapping library is not available; running trip planner in standalone mode.');
        mapElement.innerHTML = `
            <div class="d-flex flex-column align-items-center justify-content-center h-100 p-4 text-center text-secondary">
                <i class="bi bi-geo-alt fs-1 text-warning mb-2"></i>
                <h6 class="text-white">Interactive Map Standby</h6>
                <p class="small mb-0">The mapping engine is currently unavailable. Route calculation and fuel estimates remain fully functional below.</p>
            </div>
        `;
    } else {
        try {
            // Bangalore Coordinates
            const blrCoords = [12.9716, 77.5946];
            // Mysore Coordinates
            const mysCoords = [12.2958, 76.6394];

            publicMap = L.map('public-planner-map', {
                zoomControl: true,
                attributionControl: true
            }).setView([12.6337, 77.1170], 9);

            const osmTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
            });
            osmTileLayer.on('tileerror', () => {
                console.warn('OpenStreetMap tile layer temporarily delayed.');
            });
            osmTileLayer.addTo(publicMap);

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
        } catch (mapErr) {
            console.warn('Could not initialize Leaflet map:', mapErr);
        }
    }

    // Handle Vehicle selection change to dynamically toggle EV vs ICE pricing and labels
    const vehSelectEl = document.getElementById('planner-veh-select');
    const fuelLabelEl = document.getElementById('planner-fuel-label');
    const fuelPriceEl = document.getElementById('planner-fuel-price');
    const energyLabelEl = document.getElementById('pub-energy-label');
    const costLabelEl = document.getElementById('pub-cost-label');
    const evRangeCard = document.getElementById('pub-ev-range-card');

    if (vehSelectEl && fuelLabelEl && fuelPriceEl) {
        vehSelectEl.addEventListener('change', () => {
            if (vehSelectEl.selectedIndex < 0) return;
            const selectedOpt = vehSelectEl.options[vehSelectEl.selectedIndex];
            if (!selectedOpt) return;
            const isEV = (selectedOpt.getAttribute('data-type') || '').toUpperCase() === 'ELECTRIC' ||
                         (selectedOpt.getAttribute('data-fuel') || '').toUpperCase() === 'ELECTRIC';
            
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
            
            if (!vehSelect) {
                alert('Please select a vehicle from our fleet before calculating route.');
                return;
            }

            const selectedOpt = vehSelectEl.options[vehSelectEl.selectedIndex];
            const ratedRange = parseFloat(selectedOpt.getAttribute('data-range') || '0');
            const isElectric = (selectedOpt.getAttribute('data-type') || '').toUpperCase() === 'ELECTRIC' ||
                               (selectedOpt.getAttribute('data-fuel') || '').toUpperCase() === 'ELECTRIC';

            const submitBtn = tripForm.querySelector('button[type="submit"]');
            const originalText = submitBtn.innerHTML;
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Calculating Highway Route...';

            try {
                const baseUrl = (typeof API !== 'undefined' && API.BASE_URL) ? API.BASE_URL : '/api';
                const response = await fetch(`${baseUrl}/ai/trip-assistant`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        source: source,
                        destination: dest,
                        vehicleId: parseInt(vehSelect, 10),
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
        const baseUrl = (typeof API !== 'undefined' && API.BASE_URL) ? API.BASE_URL : '/api';
        const res = await fetch(`${baseUrl}/weather?lat=${lat}&lng=${lng}`);
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
