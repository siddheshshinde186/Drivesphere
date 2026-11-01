/**
 * DriveSphere Centralized API Utility Client
 * Handles JWT injection, base URL resolution, error notifications, and response wrapping.
 */
const API = {
    BASE_URL: (() => {
        if (typeof window !== 'undefined') {
            if (window.__DRIVESPHERE_API_URL__) {
                return window.__DRIVESPHERE_API_URL__;
            }
            if (window.localStorage && window.localStorage.getItem('drivesphere_api_url')) {
                return window.localStorage.getItem('drivesphere_api_url');
            }
            if (window.location.protocol && window.location.protocol.startsWith('http')) {
                if (window.location.port === '8080' || window.location.port === '') {
                    return '/api';
                }
            }
        }
        return 'http://localhost:8080/api';
    })(),

    getToken() {
        return localStorage.getItem('drivesphere_token');
    },

    setToken(token) {
        if (token) {
            localStorage.setItem('drivesphere_token', token);
        } else {
            localStorage.removeItem('drivesphere_token');
        }
    },

    getRefreshToken() {
        return localStorage.getItem('drivesphere_refresh_token');
    },

    setRefreshToken(token) {
        if (token) {
            localStorage.setItem('drivesphere_refresh_token', token);
        } else {
            localStorage.removeItem('drivesphere_refresh_token');
        }
    },

    getUser() {
        try {
            const userStr = localStorage.getItem('drivesphere_user');
            return userStr ? JSON.parse(userStr) : null;
        } catch (e) {
            return null;
        }
    },

    setUser(user) {
        if (user) {
            localStorage.setItem('drivesphere_user', JSON.stringify(user));
        } else {
            localStorage.removeItem('drivesphere_user');
        }
    },

    clearSession() {
        localStorage.removeItem('drivesphere_token');
        localStorage.removeItem('drivesphere_refresh_token');
        localStorage.removeItem('drivesphere_user');
    },

    resolveMediaUrl(url) {
        if (!url || typeof url !== 'string') return '';
        let cleanUrl = url.trim();
        // Transparently rewrite legacy download endpoints for images to public streaming endpoint
        if (cleanUrl.includes('/api/documents/download/') && 
            (cleanUrl.endsWith('.jpg') || cleanUrl.endsWith('.jpeg') || cleanUrl.endsWith('.png') || cleanUrl.endsWith('.webp'))) {
            cleanUrl = cleanUrl.replace('/api/documents/download/', '/api/documents/public/');
        }
        if (cleanUrl.startsWith('http://') || cleanUrl.startsWith('https://') || cleanUrl.startsWith('data:') || cleanUrl.startsWith('blob:')) {
            return cleanUrl;
        }
        const origin = (typeof this.BASE_URL === 'string' ? this.BASE_URL : 'http://localhost:8080/api').replace(/\/api$/, '');
        return origin + (cleanUrl.startsWith('/') ? cleanUrl : '/' + cleanUrl);
    },

    async request(endpoint, options = {}, isRetry = false) {
        const url = `${this.BASE_URL}${endpoint}`;
        const headers = {
            ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
            ...(options.headers || {})
        };

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...options,
            headers
        };

        try {
            const response = await fetch(url, config);
            
            // Handle HTTP 401 Unauthorized with automatic refresh token rotation
            if (response.status === 401 && !isRetry) {
                if (!endpoint.includes('/auth/login') && !endpoint.includes('/auth/register') && !endpoint.includes('/auth/refresh')) {
                    const rToken = this.getRefreshToken();
                    if (rToken) {
                        try {
                            const refreshRes = await fetch(`${this.BASE_URL}/auth/refresh`, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ refreshToken: rToken })
                            });
                            if (refreshRes.ok) {
                                const refreshData = await refreshRes.json();
                                if (refreshData.data && refreshData.data.accessToken) {
                                    this.setToken(refreshData.data.accessToken);
                                    if (refreshData.data.refreshToken) {
                                        this.setRefreshToken(refreshData.data.refreshToken);
                                    }
                                    return this.request(endpoint, options, true);
                                }
                            }
                        } catch (refreshErr) {
                            console.warn('Silent token refresh failed:', refreshErr);
                        }
                    }

                    this.clearSession();
                    if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('index.html')) {
                        window.location.href = '../login.html?expired=true';
                    }
                }
            }

            const data = await response.json();

            if (!response.ok) {
                const errorMessage = data.message || `Request failed with status ${response.status}`;
                const err = new Error(errorMessage);
                err.status = response.status;
                err.code = data.code || null;
                err.data = data;
                throw err;
            }

            return data;
        } catch (error) {
            console.error(`API Error [${endpoint}]:`, error.message);
            throw error;
        }
    },

    get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    },

    post(endpoint, body) {
        return this.request(endpoint, {
            method: 'POST',
            body: body instanceof FormData ? body : JSON.stringify(body)
        });
    },

    upload(endpoint, formData) {
        return this.request(endpoint, {
            method: 'POST',
            body: formData
        });
    },

    put(endpoint, body) {
        return this.request(endpoint, {
            method: 'PUT',
            body: body instanceof FormData ? body : (body ? JSON.stringify(body) : undefined)
        });
    },

    delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    },

    // UI Toast Notification helper
    showToast(message, type = 'info') {
        let container = document.getElementById('ds-toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'ds-toast-container';
            container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            container.style.zIndex = '1090';
            document.body.appendChild(container);
        }

        const bgClass = type === 'success' ? 'bg-success text-white'
            : type === 'danger' ? 'bg-danger text-white'
            : type === 'warning' ? 'bg-warning text-dark'
            : 'bg-info text-white';

        const toastId = 'toast-' + Date.now();
        const toastEl = document.createElement('div');
        toastEl.className = `toast align-items-center ${bgClass} border-0 shadow-lg`;
        toastEl.id = toastId;
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');

        toastEl.innerHTML = `
            <div class="d-flex">
                <div class="toast-body d-flex align-items-center">
                    <i class="bi bi-${type === 'success' ? 'check-circle' : type === 'danger' ? 'exclamation-circle' : 'info-circle'} me-2 fs-5"></i>
                    <div>${message}</div>
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        `;

        container.appendChild(toastEl);
        if (window.bootstrap && window.bootstrap.Toast) {
            const toast = new bootstrap.Toast(toastEl, { delay: 4500 });
            toast.show();
        }
    },

    // Domain helpers
    drivers: {
        getAvailable() {
            return API.get('/drivers/available');
        },
        getMe() {
            return API.get('/drivers/me');
        },
        sendHeartbeat() {
            return API.post('/drivers/heartbeat', {});
        },
        setOnlineStatus(online) {
            return API.post('/drivers/online-status', { online: !!online });
        },
        toggleAvailability(id) {
            return API.put(`/drivers/${id}/availability`, {});
        }
    },

    admin: {
        getSummary() {
            return API.get('/admin/summary');
        },
        getUsers() {
            return API.get('/admin/users');
        },
        toggleUserStatus(id) {
            return API.put(`/admin/users/${id}/toggle-status`, {});
        },
        getAuditLogs() {
            return API.get('/admin/audit-logs');
        },
        getReviews() {
            return API.get('/admin/reviews');
        },
        getReviewById(id) {
            return API.get(`/admin/reviews/${id}`);
        },
        deleteReview(id) {
            return API.delete(`/admin/reviews/${id}`);
        },
        getDriverPresence() {
            return API.get('/admin/drivers/presence');
        }
    },

    trips: {
        getByBooking(bookingId) {
            return API.get(`/trips/booking/${bookingId}`);
        },
        getById(id) {
            return API.get(`/trips/${id}`);
        },
        updateLocation(payload) {
            return API.post('/trips/location', payload);
        },
        simulateStep(id) {
            return API.post(`/trips/${id}/simulate`, {});
        }
    },

    bookings: {
        getMy() {
            return API.get('/bookings/my');
        },
        getById(id) {
            return API.get(`/bookings/${id}`);
        },
        create(payload) {
            return API.post('/bookings', payload);
        },
        cancel(id) {
            return API.put(`/bookings/${id}/cancel`, {});
        },
        pickup(id, payload = {}) {
            return API.post(`/bookings/${id}/pickup`, payload);
        },
        return(id, payload = {}) {
            return API.post(`/bookings/${id}/return`, payload);
        }
    },

    reviews: {
        submit(payload) {
            return API.post('/reviews', payload);
        },
        update(id, payload) {
            return API.put(`/reviews/${id}`, payload);
        },
        getByBooking(bookingId) {
            return API.get(`/reviews/booking/${bookingId}`);
        },
        getByVehicle(vehicleId) {
            return API.get(`/reviews/vehicle/${vehicleId}`);
        },
        getByDriver(driverId) {
            return API.get(`/reviews/driver/${driverId}`);
        }
    }
};

// Explicit global window export to ensure all consumers can access API reliably
if (typeof window !== 'undefined') {
    window.API = API;
}
if (typeof module !== 'undefined' && module.exports) {
    module.exports = API;
}
