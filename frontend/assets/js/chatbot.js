/**
 * DriveSphere AI Concierge Chatbot Widget
 * Supports Google Gemini AI live responses, instant suggestion pills,
 * rich message formatting, and resilient fallback handling.
 */
const Chatbot = {
    isOpen: false,

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
            <button id="ds-chat-trigger" class="btn btn-primary rounded-circle shadow-lg position-fixed bottom-0 end-0 m-4 d-flex align-items-center justify-content-center" 
                    style="width: 60px; height: 60px; z-index: 1080; border: 2px solid rgba(0,242,254,0.6); box-shadow: 0 8px 24px rgba(0, 242, 254, 0.35);"
                    title="Chat with AI Mobility Concierge">
                <i class="bi bi-robot fs-4 text-white"></i>
                <span class="position-absolute top-0 start-100 translate-middle p-1 bg-success border border-dark rounded-circle" style="width: 12px; height: 12px;"></span>
            </button>

            <!-- Chat Drawer Window -->
            <div id="ds-chat-window" class="card shadow-lg position-fixed bottom-0 end-0 m-4 d-none" 
                 style="width: 380px; max-width: calc(100vw - 32px); height: 520px; max-height: calc(100vh - 100px); z-index: 1085; border-radius: 16px; overflow: hidden; border: 1px solid rgba(0,242,254,0.3); background: #0b1120; box-shadow: 0 16px 40px rgba(0,0,0,0.8);">
                
                <!-- Header -->
                <div class="card-header bg-dark d-flex align-items-center justify-content-between p-3 border-bottom border-secondary" style="background: linear-gradient(135deg, #0f172a, #1e293b);">
                    <div class="d-flex align-items-center">
                        <div class="bg-primary rounded-circle p-2 me-2 d-flex align-items-center justify-content-center shadow-sm" style="width: 38px; height: 38px;">
                            <i class="bi bi-robot text-white fs-5"></i>
                        </div>
                        <div>
                            <div class="fw-bold text-white fs-6">DriveSphere Concierge</div>
                            <small class="text-success d-flex align-items-center" style="font-size: 0.75rem;">
                                <span class="badge bg-success p-1 rounded-circle me-1" style="width:6px;height:6px;"></span>
                                Mobility Intelligence Ready
                            </small>
                        </div>
                    </div>
                    <button id="ds-chat-close" class="btn btn-sm btn-outline-secondary border-0 text-white fs-5 p-1" title="Close Chat"><i class="bi bi-x-lg"></i></button>
                </div>

                <!-- Messages Body -->
                <div id="ds-chat-messages" class="card-body p-3 overflow-auto d-flex flex-column gap-2" style="font-size: 0.88rem; height: calc(100% - 130px); background: #080d18;">
                    <div class="chat-msg ai bg-dark text-light p-3 rounded-3 border border-secondary shadow-sm" style="max-width: 90%;">
                        Hello! 👋 I am the <strong>DriveSphere AI Concierge</strong>. Ask me about vehicle recommendations, verified drivers, fuel & EV electricity costs, or booking policies!
                    </div>
                    <div class="d-flex flex-wrap gap-1 mt-1" id="ds-chat-suggestions">
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">Which car is best for family?</button>
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">How does Driver Trust Score work?</button>
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">Can I rent an EV?</button>
                        <button type="button" class="btn btn-outline-info btn-sm py-1 px-2 text-start rounded-pill suggestion-btn" style="font-size: 0.75rem;">Bangalore to Mysore route cost</button>
                    </div>
                </div>

                <!-- Input Footer -->
                <div class="card-footer bg-dark p-2 border-top border-secondary" style="background: #0f172a;">
                    <form id="ds-chat-form" class="input-group">
                        <input id="ds-chat-input" type="text" class="form-control form-control-sm bg-black text-white border-secondary" placeholder="Ask AI mobility concierge..." required autocomplete="off">
                        <button class="btn btn-primary btn-sm px-3" type="submit" id="ds-chat-send-btn" title="Send message">
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

        if (trigger && chatWindow) {
            trigger.addEventListener('click', () => {
                chatWindow.classList.toggle('d-none');
                this.isOpen = !chatWindow.classList.contains('d-none');
                if (this.isOpen && input) {
                    setTimeout(() => input.focus(), 100);
                }
            });
        }

        if (closeBtn && chatWindow) {
            closeBtn.addEventListener('click', () => {
                chatWindow.classList.add('d-none');
                this.isOpen = false;
            });
        }

        // Delegated click on suggestion pills
        if (suggestionsBox) {
            suggestionsBox.addEventListener('click', (e) => {
                const btn = e.target.closest('.suggestion-btn');
                if (btn) {
                    const text = btn.innerText.trim();
                    this.sendMessage(text);
                }
            });
        }

        if (form) {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                const text = input.value.trim();
                if (!text) return;
                input.value = '';
                this.sendMessage(text);
            });
        }
    },

    async sendMessage(text) {
        if (!text) return;
        const messages = document.getElementById('ds-chat-messages');
        const input = document.getElementById('ds-chat-input');
        if (!messages) return;

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

            // Attempt backend call
            try {
                const isUserAuth = (typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false;
                const res = await API.post('/ai/chat', { message: text, authenticated: isUserAuth });
                if (res && res.data && res.data.reply) {
                    replyText = res.data.reply;
                    suggestions = res.data.suggestedQuestions || [];
                } else if (res && res.reply) {
                    replyText = res.reply;
                    suggestions = res.suggestedQuestions || [];
                }
            } catch (backendErr) {
                console.warn('Backend chat endpoint standby, utilizing intelligent local concierge engine:', backendErr);
                replyText = this.getLocalFallback(text);
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

            // Render formatted reply
            typingEl.className = 'chat-msg ai bg-dark text-light p-3 rounded-3 border border-secondary shadow-sm';
            typingEl.innerHTML = this.formatReply(replyText);

            // Update suggestions
            if (suggestions && suggestions.length > 0) {
                this.updateSuggestions(suggestions);
            }
        } catch (err) {
            console.error('Chat error:', err);
            typingEl.className = 'chat-msg ai bg-dark text-light p-3 rounded-3 border border-secondary';
            typingEl.innerHTML = this.formatReply(this.getLocalFallback(text));
        }

        messages.scrollTop = messages.scrollHeight;
        if (input) input.focus();
    },

    formatReply(text) {
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
            return "DriveSphere drivers are vetted through our 4-pillar **Driver Trust Score (0-100)**: Government License & Identity Verification (25%), Commercial Driving Skill Assessment (25%), Customer Ratings (25%), and On-Time Trip Records (25%). Only verified drivers with score &ge; 75 are dispatchable.";
        }
        if (lower.indexOf('ev') !== -1 || lower.indexOf('electric') !== -1 || lower.indexOf('charging') !== -1) {
            return "Yes! Our eco-fleet includes the **Tata Nexon EV Empowered** (312 km range) and **Hyundai Ioniq 5 EV** (400+ km range). We display energy consumption strictly in **kWh** (at Rs. 12/kWh) and highlight highway fast chargers on your route.";
        }
        if (lower.indexOf('book') !== -1 || lower.indexOf('reserve') !== -1 || lower.indexOf('rent') !== -1) {
            const isUserAuth = (typeof Auth !== 'undefined' && Auth.isAuthenticated) ? Auth.isAuthenticated() : false;
            if (!isUserAuth) {
                return "To book a vehicle or access personal reservations, you must sign in to your DriveSphere customer account.\n\n👉 [Please sign in here](/login.html?returnTo=/customer/index.html&action=book) to continue with your booking.";
            }
            return "Booking is instantaneous: Select your vehicle, pick rental dates, optionally add an approved driver, and checkout in our Razorpay sandbox test environment. You receive real-time booking confirmation and GPS telemetry.";
        }
        if (lower.indexOf('document') !== -1 || lower.indexOf('license') !== -1 || lower.indexOf('requirement') !== -1) {
            return "To rent a self-drive vehicle, you need: (1) A valid Government Driving License held for at least 1 year, (2) Government ID proof (Aadhaar or Passport), and (3) Minimum age of 21.";
        }
        if (lower.indexOf('cancel') !== -1 || lower.indexOf('refund') !== -1 || lower.indexOf('policy') !== -1) {
            return "Cancellations made 24 hours prior to the scheduled trip start time receive a 100% full refund to the original payment method.";
        }
        return "Hello! I am your **DriveSphere AI Concierge**. I can assist with personalized vehicle recommendations, hiring certified drivers with Driver Trust Scores, trip routing with fuel/kWh estimates, and reservation policies. How may I help you?";
};

document.addEventListener('DOMContentLoaded', () => Chatbot.init());
