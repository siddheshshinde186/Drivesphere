/**
 * DriveSphere AI Concierge Chatbot Widget
 * Supports Google Gemini AI live responses, instant suggestion pills,
 * rich message formatting, Enter to send / Shift+Enter for newline,
 * duplicate submission prevention, accessible labels, and resilient honest fallback handling.
 */
const Chatbot = {
    isOpen: false,
    isSending: false,

    init() {
        this.renderWidget();
        this.attachEvents();
    },

    renderWidget() {
        const existing = document.getElementById('ds-chatbot-container');
        if (existing) return;

        const container = document.createElement('div');
        container.id = 'ds-chatbot-container';
        container.innerHTML = `
            <!-- Floating Trigger Button -->
            <button id="ds-chat-trigger" 
                    class="btn btn-primary rounded-circle shadow-lg position-fixed bottom-0 end-0 m-3 m-md-4 d-flex align-items-center justify-content-center" 
                    style="width: 58px; height: 58px; z-index: 1080; border: 2px solid rgba(56, 189, 248, 0.7); box-shadow: 0 8px 24px rgba(56, 189, 248, 0.4);"
                    aria-label="Open DriveSphere AI Concierge Chatbot"
                    aria-expanded="false"
                    aria-controls="ds-chat-window"
                    title="Chat with DriveSphere AI Concierge">
                <i class="bi bi-robot fs-4 text-white"></i>
                <span class="position-absolute top-0 start-100 translate-middle p-1 bg-success border border-dark rounded-circle" 
                      style="width: 12px; height: 12px;" 
                      title="AI Concierge Active"></span>
            </button>

            <!-- Chat Drawer Window -->
            <div id="ds-chat-window" 
                 class="card shadow-lg position-fixed bottom-0 end-0 m-3 m-md-4 d-none" 
                 role="dialog"
                 aria-modal="false"
                 aria-label="DriveSphere AI Concierge Chat"
                 style="width: 380px; max-width: calc(100vw - 28px); height: 540px; max-height: calc(100vh - 90px); z-index: 1085; border-radius: 18px; overflow: hidden; border: 1px solid var(--border-color, #1e293b); background: var(--bg-modal, #0b0f17); box-shadow: 0 25px 60px -10px rgba(0,0,0,0.95), 0 0 35px rgba(56, 189, 248, 0.18);">
                
                <!-- Header -->
                <div class="card-header d-flex align-items-center justify-content-between p-3 border-bottom border-secondary border-opacity-25" 
                     style="background: linear-gradient(135deg, rgba(14, 18, 28, 0.98), rgba(22, 28, 44, 0.98));">
                    <div class="d-flex align-items-center">
                        <div class="brand-icon-wrapper p-2 me-2 d-flex align-items-center justify-content-center bg-dark border border-secondary border-opacity-50" 
                             style="width: 38px; height: 38px; border-radius: 10px;">
                            <i class="bi bi-robot text-info fs-5"></i>
                        </div>
                        <div>
                            <div class="fw-bold text-white fs-6">DriveSphere Concierge</div>
                            <small class="text-success d-flex align-items-center" style="font-size: 0.75rem;">
                                <span class="badge bg-success rounded-circle p-1 me-1" style="width: 6px; height: 6px;"></span>
                                Mobility Assistant Ready
                            </small>
                        </div>
                    </div>
                    <div class="d-flex align-items-center gap-1">
                        <button id="ds-chat-close" 
                                type="button"
                                class="btn btn-sm btn-outline-secondary border-0 text-white fs-5 p-1 px-2" 
                                aria-label="Close Chat" 
                                title="Close Chat (Esc)">
                            <i class="bi bi-x-lg"></i>
                        </button>
                    </div>
                </div>

                <!-- Messages Body -->
                <div id="ds-chat-messages" 
                     class="card-body p-3 overflow-auto d-flex flex-column gap-2" 
                     style="font-size: 0.88rem; height: calc(100% - 135px); background: #050609;"
                     aria-live="polite">
                    <div class="chat-msg ai p-3 rounded-3 shadow-sm border border-secondary border-opacity-25 text-light" 
                         style="max-width: 90%; background: #0f172a;">
                        Hello! 👋 I am your <strong>DriveSphere AI Concierge</strong>. Ask me about vehicle recommendations, verified drivers, route fuel & EV energy costs, or booking policies!
                    </div>
                    <div class="d-flex flex-wrap gap-1 mt-1" id="ds-chat-suggestions">
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">Which car is best for family?</button>
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">How does Driver Trust Score work?</button>
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">Can I rent an EV?</button>
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">Bangalore to Mysore route cost</button>
                    </div>
                </div>

                <!-- Input Footer -->
                <div class="card-footer p-2 border-top border-secondary border-opacity-25" 
                     style="background: rgba(10, 13, 20, 0.98);">
                    <form id="ds-chat-form" class="input-group align-items-end">
                        <textarea id="ds-chat-input" 
                                  rows="1" 
                                  class="form-control form-control-sm border-secondary text-light bg-dark" 
                                  placeholder="Ask AI concierge... (Enter to send, Shift+Enter for line)" 
                                  aria-label="Message to AI Concierge"
                                  style="resize: none; font-size: 0.85rem; max-height: 80px;"
                                  required></textarea>
                        <button class="btn btn-primary btn-sm px-3" 
                                type="submit" 
                                id="ds-chat-send-btn" 
                                aria-label="Send message" 
                                title="Send message (Enter)">
                            <i class="bi bi-send-fill"></i>
                        </button>
                    </form>
                </div>
            </div>
        `;
        document.body.appendChild(container);
    },

    attachEvents() {
        const trigger = document.getElementById('ds-chat-trigger');
        const closeBtn = document.getElementById('ds-chat-close');
        const chatWindow = document.getElementById('ds-chat-window');
        const form = document.getElementById('ds-chat-form');
        const input = document.getElementById('ds-chat-input');
        const suggestionsBox = document.getElementById('ds-chat-suggestions');

        const toggleChat = (open) => {
            if (open === undefined) {
                open = chatWindow.classList.contains('d-none');
            }
            if (open) {
                chatWindow.classList.remove('d-none');
                this.isOpen = true;
                trigger.setAttribute('aria-expanded', 'true');
                if (input) {
                    setTimeout(() => input.focus(), 100);
                }
            } else {
                chatWindow.classList.add('d-none');
                this.isOpen = false;
                trigger.setAttribute('aria-expanded', 'false');
                trigger.focus();
            }
        };

        if (trigger && chatWindow) {
            trigger.addEventListener('click', () => toggleChat());
        }

        if (closeBtn && chatWindow) {
            closeBtn.addEventListener('click', () => toggleChat(false));
        }

        // Close on Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isOpen) {
                toggleChat(false);
            }
        });

        // Auto-expand textarea & Enter to send / Shift+Enter for newline
        if (input) {
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    if (!this.isSending) {
                        form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit', { cancelable: true }));
                    }
                }
            });

            input.addEventListener('input', () => {
                input.style.height = 'auto';
                input.style.height = Math.min(input.scrollHeight, 80) + 'px';
            });
        }

        // Delegated click on suggestion pills
        if (suggestionsBox) {
            suggestionsBox.addEventListener('click', (e) => {
                const btn = e.target.closest('.suggestion-btn');
                if (btn && !this.isSending) {
                    const text = btn.innerText.trim();
                    this.sendMessage(text);
                }
            });
        }

        if (form) {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                if (this.isSending) return;
                const text = input.value.trim();
                if (!text) return;
                input.value = '';
                input.style.height = 'auto';
                this.sendMessage(text);
            });
        }
    },

    async sendMessage(text) {
        if (!text || this.isSending) return;
        const messages = document.getElementById('ds-chat-messages');
        const input = document.getElementById('ds-chat-input');
        const sendBtn = document.getElementById('ds-chat-send-btn');
        if (!messages) return;

        this.isSending = true;
        if (sendBtn) {
            sendBtn.disabled = true;
            sendBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';
        }
        if (input) {
            input.disabled = true;
        }

        // 1. Append user message bubble
        const userMsgEl = document.createElement('div');
        userMsgEl.className = 'chat-msg user bg-primary text-white p-2 px-3 rounded-3 align-self-end shadow-sm';
        userMsgEl.style.maxWidth = '85%';
        userMsgEl.innerText = text;
        messages.appendChild(userMsgEl);
        messages.scrollTop = messages.scrollHeight;

        // 2. Append typing indicator
        const typingEl = document.createElement('div');
        typingEl.className = 'chat-msg ai bg-dark text-secondary p-2 px-3 rounded-3 border border-secondary shadow-sm';
        typingEl.style.maxWidth = '85%';
        typingEl.innerHTML = '<span class="spinner-border spinner-border-sm me-2 text-info" role="status"></span>DriveSphere AI thinking...';
        messages.appendChild(typingEl);
        messages.scrollTop = messages.scrollHeight;

        try {
            let replyText = '';
            let suggestions = [];
            let modelProvider = 'Heuristic Mobility Engine [Offline Mode]';
            let fallbackMode = true;

            // Attempt backend call
            try {
                const isUserAuth = (typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false;
                const res = await API.post('/ai/chat', { message: text, authenticated: isUserAuth });
                const chatData = (res && res.data) ? res.data : res;
                if (chatData && chatData.reply) {
                    replyText = chatData.reply;
                    suggestions = chatData.suggestedQuestions || [];
                    modelProvider = chatData.modelProvider || (chatData.fallbackMode ? 'Heuristic Mobility Engine' : 'Google Gemini 3.5 Flash Lite');
                    fallbackMode = Boolean(chatData.fallbackMode);
                }
            } catch (backendErr) {
                console.warn('Backend chat standby, utilizing intelligent local concierge engine:', backendErr);
                replyText = this.getLocalFallback(text);
                modelProvider = 'Browser Local Heuristics [Offline Standby]';
                fallbackMode = true;
                suggestions = [
                    "Which car is best for family?",
                    "How does Driver Trust Score work?",
                    "Can I rent an EV?",
                    "What documents are needed for rental?"
                ];
            }

            if (!replyText) {
                replyText = this.getLocalFallback(text);
            }

            // Render formatted reply with honest attribution
            typingEl.className = 'chat-msg ai bg-dark text-light p-3 rounded-3 border border-secondary shadow-sm';
            typingEl.innerHTML = this.formatReply(replyText, modelProvider, fallbackMode);

            // Update suggestions
            if (suggestions && suggestions.length > 0) {
                this.updateSuggestions(suggestions);
            }
        } catch (err) {
            console.error('Chat error:', err);
            typingEl.className = 'chat-msg ai bg-dark text-light p-3 rounded-3 border border-secondary';
            typingEl.innerHTML = this.formatReply(this.getLocalFallback(text), 'Browser Local Heuristics [Offline Standby]', true);
        } finally {
            this.isSending = false;
            if (sendBtn) {
                sendBtn.disabled = false;
                sendBtn.innerHTML = '<i class="bi bi-send-fill"></i>';
            }
            if (input) {
                input.disabled = false;
                input.focus();
            }
            messages.scrollTop = messages.scrollHeight;
        }
    },

    formatReply(text, modelProvider, fallbackMode) {
        if (!text) return '';
        // Escape HTML
        let escaped = text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");

        // Convert links [Text](url) to <a href="url">Text</a>
        escaped = escaped.replace(/\[(.*?)\]\((.*?)\)/g, '<a href="$2" class="text-info text-decoration-underline">$1</a>');
        // Convert bold **text** to <strong>text</strong>
        escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        // Convert *text* to <em>text</em>
        escaped = escaped.replace(/\*(.*?)\*/g, '<em>$1</em>');
        // Convert newlines to <br>
        escaped = escaped.replace(/\n/g, '<br>');

        if (modelProvider) {
            const isGemini = !fallbackMode && modelProvider.includes('Gemini');
            const badgeIcon = isGemini ? 'bi-stars text-info' : 'bi-cpu text-secondary';
            const badgeClass = isGemini ? 'text-info' : 'text-secondary';
            escaped += `<div class="mt-2 pt-1 border-top border-secondary border-opacity-25 d-flex align-items-center justify-content-between ${badgeClass}" style="font-size: 0.7rem;">
                <span><i class="bi ${badgeIcon} me-1"></i>${modelProvider}</span>
            </div>`;
        }

        return escaped;
    },

    updateSuggestions(questions) {
        const box = document.getElementById('ds-chat-suggestions');
        if (!box) return;
        box.innerHTML = questions.map(q => `
            <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">
                ${q}
            </button>
        `).join('');
    },

    getLocalFallback(query) {
        const lower = (query || '').toLowerCase();
        if (lower.indexOf('family') !== -1 || lower.indexOf('group') !== -1 || lower.indexOf('seat') !== -1 || lower.indexOf('passenger') !== -1) {
            return "For family roadtrips (5 to 7 passengers), our top recommendation is the **Mahindra XUV700 AX7 Luxury** (7 seats, Rs. 3,600/day) or the **Toyota Fortuner Legender 4x4** (7 seats, Rs. 4,800/day). Both feature 3-row comfort, ADAS safety, and expansive luggage space. For groups larger than 7, our AI Fleet Matcher schedules a multi-vehicle convoy!";
        }
        if (lower.indexOf('driver') !== -1 || lower.indexOf('trust score') !== -1 || lower.indexOf('chauffeur') !== -1) {
            return "DriveSphere drivers are evaluated through our **Driver Trust Score (0-100)**: Document Verification (up to 25 pts), Professional Experience (up to 20 pts), Customer Ratings (up to 25 pts), Trip Completion (up to 20 pts), and Cancellation Deductions (up to -10 pts). Only verified drivers with approved status can be assigned.";
        }
        if (lower.indexOf('ev') !== -1 || lower.indexOf('electric') !== -1 || lower.indexOf('charging') !== -1) {
            return "Yes! Our eco-fleet includes the **Tata Nexon EV Empowered** (312 km range) and **Hyundai Ioniq 5 EV** (400+ km range). We display energy consumption strictly in **kWh** (at Rs. 12/kWh) and highlight highway fast chargers on your route.";
        }
        if (lower.indexOf('book') !== -1 || lower.indexOf('reserve') !== -1 || lower.indexOf('rent') !== -1) {
            const isUserAuth = (typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false;
            if (!isUserAuth) {
                return "To book a vehicle or access personal reservations, you must sign in to your DriveSphere customer account.\n\n👉 [Please sign in here](/login.html?returnTo=/customer/index.html&action=book) to continue with your booking.";
            }
            return "Booking is instantaneous: Select your vehicle, pick rental dates, optionally add an approved driver, and checkout in our test environment. You receive real-time booking confirmation and GPS telemetry.";
        }
        if (lower.indexOf('document') !== -1 || lower.indexOf('license') !== -1 || lower.indexOf('requirement') !== -1) {
            return "To rent a self-drive vehicle, you need: (1) A valid Government Driving License held for at least 1 year, (2) Government ID proof (Aadhaar or Passport), and (3) Minimum age of 21.";
        }
        if (lower.indexOf('cancel') !== -1 || lower.indexOf('refund') !== -1 || lower.indexOf('policy') !== -1) {
            return "Cancellations made 24 hours prior to the scheduled trip start time receive a 100% full refund to the original payment method.";
        }
        return "Hello! I am your **DriveSphere AI Concierge**. I can assist with personalized vehicle recommendations, hiring certified drivers with Driver Trust Scores, trip routing with fuel/kWh estimates, and reservation policies. How may I help you?";
    }
};

document.addEventListener('DOMContentLoaded', () => Chatbot.init());
