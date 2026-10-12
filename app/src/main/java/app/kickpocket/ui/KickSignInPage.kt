package app.kickpocket.ui

/** Only opens and sizes Kick's own form. Never reads field values, cookies, or tokens. */
internal val KickSignInPageScript = """
    (() => {
      let modal = document.querySelector('[data-testid="auth-modal"]');
      if (!modal && !window.__puntSignInFormSeen &&
          (!window.__puntSignInRequestedAt || Date.now() - window.__puntSignInRequestedAt >= 5000)) {
        const login = Array.from(document.querySelectorAll('button[data-testid="login"]')).find(e => {
          const r = e.getBoundingClientRect();
          return r.width > 0 && r.height > 0 && r.left >= 0 && r.right <= innerWidth;
        });
        if (login) {
          // A click on the server-rendered header can arrive before Kick attaches its handlers.
          window.__puntSignInRequestedAt = Date.now();
          login.click();
        }
        return false;
      }
      if (!modal) return false;
      window.__puntSignInFormSeen = true;
      const r = modal.getBoundingClientRect();
      // The site's dynamic viewport units can leave just the logo visible in embedded browsers.
      // Percentage dimensions on a fixed element use the actual WebView viewport instead.
      if (r.height < Math.min(320, innerHeight * 0.6) || r.left < -1 || r.right > innerWidth + 1 ||
          r.top < -1 || r.bottom > innerHeight + 1) {
        if (!document.getElementById('punt-sign-in-layout')) {
          const style = document.createElement('style');
          style.id = 'punt-sign-in-layout';
          style.textContent = '[data-testid="auth-modal"] { position: fixed !important; inset: 0 !important; width: 100% !important; max-width: 100% !important; height: 100% !important; max-height: 100% !important; min-height: 0 !important; transform: none !important; animation: none !important; overflow-y: auto !important; box-sizing: border-box !important; }';
          document.head.appendChild(style);
        }
      }
      return ['input[name="emailOrUsername"]', 'input[type="password"]'].every(selector => {
        const field = modal.querySelector(selector);
        if (!field) return false;
        const b = field.getBoundingClientRect();
        const x = b.left + b.width / 2, y = b.top + b.height / 2;
        return b.width > 0 && b.height > 0 && b.left >= 0 && b.right <= innerWidth + 1 &&
          b.top >= 0 && b.bottom <= innerHeight + 1 && document.elementFromPoint(x, y) === field;
      });
    })()
""".trimIndent()
