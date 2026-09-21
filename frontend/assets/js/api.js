/**
 * DriveSphere Centralized API Utility Client
 * Handles JWT injection, base URL resolution, error notifications, and response wrapping.
 */
const API = {
    BASE_URL: (() => {
        if (typeof window !== 'undefined' && window.location.protocol && window.location.protocol.startsWith('http')) {
            if (window.location.port === '8080' || window.location.port === '') {
                return '/api';
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
        localStorage.removeItem('drivesphere_user');
    },

    async request(endpoint, options = {}) {
        const url = `${this.BASE_URL}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
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
            
            // Handle HTTP 401 Unauthorized
            if (response.status === 401) {
                if (!endpoint.includes('/auth/login') && !endpoint.includes('/auth/register')) {
                    this.clearSession();
                    if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('index.html')) {
                        window.location.href = '../login.html?expired=true';
                    }
                }
            }

            const data = await response.json();

            if (!response.ok) {
                const errorMessage = data.message || `Request failed with status ${response.status}`;
                throw new Error(errorMessage);
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
            body: JSON.stringify(body)
        });
    },

    put(endpoint, body) {
        return this.request(endpoint, {
            method: 'PUT',
            body: body ? JSON.stringify(body) : undefined
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
    }
};
