/**
 * DriveSphere – Smart Mobility & Fleet Intelligence Platform
 * Frontend Client Logic
 */

document.addEventListener('DOMContentLoaded', () => {
    initHealthChecker();
    initTelemetryMap();
    initTelemetryChart();
});

// ===================================================================
// 1. Backend Health Check & Diagnostic Tool
// ===================================================================
function initHealthChecker() {
    const pingBtn = document.getElementById('pingHealthBtn');
    const statusDot = document.getElementById('healthStatusDot');
    const statusText = document.getElementById('healthStatusText');
    const responseTimeEl = document.getElementById('healthResponseTime');
    const jsonOutputEl = document.getElementById('healthJsonOutput');
    const headerStatusDot = document.getElementById('headerStatusDot');
    const headerStatusText = document.getElementById('headerStatusText');

    async function checkBackendHealth() {
        if (statusDot) statusDot.className = 'pulse-dot checking';
        if (statusText) statusText.textContent = 'Pinging...';
        if (jsonOutputEl) jsonOutputEl.textContent = '// Connecting to http://localhost:8080/api/health...';

        const startTime = performance.now();

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 4000);

            const response = await fetch('http://localhost:8080/api/health', {
                method: 'GET',
                headers: { 'Accept': 'application/json' },
                signal: controller.signal
            });

            clearTimeout(timeoutId);
            const duration = Math.round(performance.now() - startTime);

            if (response.ok) {
                const data = await response.json();
                if (statusDot) statusDot.className = 'pulse-dot online';
                if (statusText) statusText.textContent = 'Operational (HTTP 200)';
                if (headerStatusDot) headerStatusDot.className = 'pulse-dot online';
                if (headerStatusText) headerStatusText.textContent = 'API Online';
                if (responseTimeEl) responseTimeEl.textContent = `${duration}ms`;
                if (jsonOutputEl) jsonOutputEl.textContent = JSON.stringify(data, null, 2);
            } else {
                throw new Error(`HTTP error status: ${response.status}`);
            }
        } catch (error) {
            const duration = Math.round(performance.now() - startTime);
            if (statusDot) statusDot.className = 'pulse-dot offline';
            if (statusText) statusText.textContent = 'Offline / Standby';
            if (headerStatusDot) headerStatusDot.className = 'pulse-dot offline';
            if (headerStatusText) headerStatusText.textContent = 'API Standby';
            if (responseTimeEl) responseTimeEl.textContent = `${duration}ms`;

            if (jsonOutputEl) {
                jsonOutputEl.textContent = JSON.stringify({
                    platform: "DriveSphere Backend",
                    endpoint: "http://localhost:8080/api/health",
                    status: "STANDBY",
                    message: "Backend server is not running or CORS blocked. Start backend with: .\\mvnw.cmd spring-boot:run",
                    errorDetails: error.message
                }, null, 2);
            }
        }
    }

    if (pingBtn) {
        pingBtn.addEventListener('click', checkBackendHealth);
    }

    // Run initial health ping on page load
    checkBackendHealth();
}

// ===================================================================
// 2. Leaflet Fleet Telemetry Interactive Map Preview
// ===================================================================
function initTelemetryMap() {
    const mapElement = document.getElementById('telemetryMap');
    if (!mapElement) return;

    // Center coordinates (Metro Mobility Corridor)
    const centerCoords = [12.9716, 77.5946];
    const map = L.map('telemetryMap', {
        zoomControl: false,
        attributionControl: false
    }).setView(centerCoords, 13);

    // Zoom control in top right
    L.control.zoom({ position: 'topright' }).addTo(map);

    // Dark sleek tile layer (CartoDB Dark Matter)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        subdomains: 'abcd'
    }).addTo(map);

    // Sample connected fleet vehicles preview
    const fleetVehicles = [
        {
            name: "Aero EV3 (KA-01-EQ-1001)",
            type: "Eco Sedan",
            status: "AVAILABLE",
            battery: "88%",
            speed: "42 km/h",
            lat: 12.9716,
            lng: 77.5946,
            color: "#00f2fe"
        },
        {
            name: "Apex Pulse (KA-01-EQ-1002)",
            type: "Executive EV",
            status: "EN ROUTE",
            battery: "76%",
            speed: "58 km/h",
            lat: 12.9850,
            lng: 77.6100,
            color: "#8b5cf6"
        },
        {
            name: "Vanguard Express (KA-01-EQ-1003)",
            type: "Logistics Van",
            status: "CHARGING",
            battery: "45%",
            speed: "0 km/h",
            lat: 12.9550,
            lng: 77.5800,
            color: "#10b981"
        }
    ];

    fleetVehicles.forEach(vehicle => {
        const markerIcon = L.divIcon({
            className: 'custom-fleet-marker',
            html: `
                <div style="
                    width: 24px; 
                    height: 24px; 
                    border-radius: 50%; 
                    background: ${vehicle.color}; 
                    box-shadow: 0 0 16px ${vehicle.color};
                    border: 2px solid #ffffff;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                ">
                    <div style="width: 6px; height: 6px; border-radius: 50%; background: #0b1120;"></div>
                </div>
            `,
            iconSize: [24, 24],
            iconAnchor: [12, 12]
        });

        const marker = L.marker([vehicle.lat, vehicle.lng], { icon: markerIcon }).addTo(map);
        marker.bindPopup(`
            <div style="padding: 4px 6px; font-family: 'Inter', sans-serif;">
                <div style="font-weight: 700; font-size: 0.9rem; color: #00f2fe; margin-bottom: 4px;">${vehicle.name}</div>
                <div style="font-size: 0.8rem; color: #94a3b8; margin-bottom: 2px;">Category: <strong style="color: #fff;">${vehicle.type}</strong></div>
                <div style="font-size: 0.8rem; color: #94a3b8; margin-bottom: 2px;">Battery: <strong style="color: #10b981;">${vehicle.battery}</strong></div>
                <div style="font-size: 0.8rem; color: #94a3b8; margin-bottom: 2px;">Speed: <strong style="color: #fff;">${vehicle.speed}</strong></div>
                <div style="font-size: 0.75rem; color: #94a3b8;">Status: <span style="color: #00f2fe; font-weight: 600;">${vehicle.status}</span></div>
            </div>
        `);
    });
}

// ===================================================================
// 3. Chart.js Fleet Telemetry & Energy Efficiency Preview
// ===================================================================
function initTelemetryChart() {
    const chartCanvas = document.getElementById('telemetryChart');
    if (!chartCanvas) return;

    const ctx = chartCanvas.getContext('2d');

    const gradientCyan = ctx.createLinearGradient(0, 0, 0, 250);
    gradientCyan.addColorStop(0, 'rgba(0, 242, 254, 0.3)');
    gradientCyan.addColorStop(1, 'rgba(0, 242, 254, 0.0)');

    const gradientPurple = ctx.createLinearGradient(0, 0, 0, 250);
    gradientPurple.addColorStop(0, 'rgba(139, 92, 246, 0.3)');
    gradientPurple.addColorStop(1, 'rgba(139, 92, 246, 0.0)');

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: ['06:00', '09:00', '12:00', '15:00', '18:00', '21:00', '00:00'],
            datasets: [
                {
                    label: 'Fleet Efficiency (Wh/km)',
                    data: [135, 142, 138, 145, 149, 137, 132],
                    borderColor: '#00f2fe',
                    backgroundColor: gradientCyan,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 4,
                    pointBackgroundColor: '#00f2fe'
                },
                {
                    label: 'Active Connected Units',
                    data: [24, 78, 92, 85, 110, 64, 30],
                    borderColor: '#8b5cf6',
                    backgroundColor: gradientPurple,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 4,
                    pointBackgroundColor: '#8b5cf6',
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                legend: {
                    labels: {
                        color: '#94a3b8',
                        font: { family: 'Inter', size: 12 }
                    }
                },
                tooltip: {
                    backgroundColor: 'rgba(15, 23, 42, 0.9)',
                    titleColor: '#00f2fe',
                    bodyColor: '#f8fafc',
                    borderColor: 'rgba(255, 255, 255, 0.1)',
                    borderWidth: 1
                }
            },
            scales: {
                x: {
                    grid: { color: 'rgba(255, 255, 255, 0.05)' },
                    ticks: { color: '#64748b', font: { family: 'Inter' } }
                },
                y: {
                    grid: { color: 'rgba(255, 255, 255, 0.05)' },
                    ticks: { color: '#64748b', font: { family: 'Inter' } }
                },
                y1: {
                    position: 'right',
                    grid: { display: false },
                    ticks: { color: '#8b5cf6', font: { family: 'Inter' } }
                }
            }
        }
    });
}
