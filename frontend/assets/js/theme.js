/**
 * DriveSphere Unified Dark Theme Engine
 * Enforces a single, consistent signature dark theme across all portals and pages.
 * Precision modern aesthetics with deep charcoal canvas and cyber cyan accents.
 */
const Theme = (function () {
    const STORAGE_KEY = 'drivesphere_theme';
    const THEME_UNIFIED = 'dark';

    function applyTheme() {
        document.documentElement.setAttribute('data-theme', THEME_UNIFIED);
        document.documentElement.classList.add('theme-unified-drivesphere');
        try {
            localStorage.setItem(STORAGE_KEY, THEME_UNIFIED);
        } catch (e) {
            // Storage access restricted in some sandboxed environments
        }
    }

    function toggle() {
        // Enforce the project's single unified theme everywhere
        applyTheme();
    }

    function init() {
        applyTheme();
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', applyTheme);
        }
    }

    // Run immediately to eliminate any flash
    init();

    return {
        init: init,
        toggle: toggle,
        getTheme: function () {
            return THEME_UNIFIED;
        }
    };
})();

// Expose globally
window.Theme = Theme;
