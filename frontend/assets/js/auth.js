/**
 * DriveSphere Authentication & Role Guard Utility
 */
const Auth = {
    isAuthenticated() {
        return !!API.getToken();
    },

    isLoggedIn() {
        return this.isAuthenticated();
    },

    getCurrentUser() {
        return API.getUser();
    },

    getUser() {
        return this.getCurrentUser();
    },

    getPrimaryRole() {
        const user = this.getCurrentUser();
        if (!user) return null;
        if (user.primaryRole) return user.primaryRole;
        if (user.roles && user.roles.length > 0) return user.roles[0];
        return 'ROLE_CUSTOMER';
    },

    redirectToDashboard() {
        const role = this.getPrimaryRole();
        if (!role) {
            window.location.href = '/login.html';
            return;
        }

        switch (role) {
            case 'ROLE_ADMIN':
                window.location.href = '/admin/index.html';
                break;
            case 'ROLE_FLEET_MANAGER':
                window.location.href = '/fleet-manager/index.html';
                break;
            case 'ROLE_DRIVER':
                window.location.href = '/driver/index.html';
                break;
            case 'ROLE_CUSTOMER':
            default:
                window.location.href = '/customer/index.html';
                break;
        }
    },

    requireAuth(allowedRoles = []) {
        if (!this.isAuthenticated()) {
            const currentPath = window.location.pathname + window.location.search;
            window.location.href = '../login.html?returnTo=' + encodeURIComponent(currentPath) + '&message=' + encodeURIComponent('Please sign in to continue with your booking.');
            return false;
        }

        if (allowedRoles.length > 0) {
            const role = this.getPrimaryRole();
            if (!allowedRoles.includes(role)) {
                alert('Access Denied: Your account role does not have authorization to view this module.');
                this.redirectToDashboard();
                return false;
            }
        }

        return true;
    },

    logout() {
        API.clearSession();
        window.location.href = '../login.html?logged_out=true';
    },

    updateNavbarUserUI() {
        const user = this.getCurrentUser();
        const navContainer = document.getElementById('navbar-auth-controls');
        if (!navContainer) return;

        if (user) {
            const roleBadge = user.primaryRole ? user.primaryRole.replace('ROLE_', '') : 'USER';
            navContainer.innerHTML = `
                <div class="dropdown">
                    <button class="btn btn-outline-light btn-sm dropdown-toggle d-flex align-items-center" type="button" data-bs-toggle="dropdown">
                        <img src="${user.avatarUrl || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80'}" class="rounded-circle me-2" width="24" height="24">
                        <span>${user.firstName || 'My Account'}</span>
                        <span class="badge bg-primary ms-2">${roleBadge}</span>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end dropdown-menu-dark">
                        <li><a class="dropdown-item" href="javascript:Auth.redirectToDashboard()"><i class="bi bi-speedometer2 me-2"></i>Dashboard</a></li>
                        <li><hr class="dropdown-divider"></li>
                        <li><a class="dropdown-item text-danger" href="javascript:Auth.logout()"><i class="bi bi-box-arrow-right me-2"></i>Logout</a></li>
                    </ul>
                </div>
            `;
        } else {
            navContainer.innerHTML = `
                <a href="login.html" class="btn btn-outline-light btn-sm me-2"><i class="bi bi-box-arrow-in-right me-1"></i>Login</a>
                <a href="register.html" class="btn btn-primary btn-sm"><i class="bi bi-person-plus me-1"></i>Register</a>
            `;
        }
    }
};
