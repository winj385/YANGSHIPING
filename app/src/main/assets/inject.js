/*
 * inject.js  — injected into the yangshipin (and cctv) live page.
 *
 * Design rules (per requirements):
 *  - NEVER hard-code class names. Everything is detected by POSITION + SIZE,
 *    because the site's markup changes often.
 *  - Pin the player fullscreen and cover the rest with a black backdrop.
 *  - Remove login popups / download banners / right-side program list by geometry.
 *  - switchTo(name): drive the page's OWN channel control (no full reload).
 *  - getNowPlaying(): best-effort scrape of the current program title.
 *
 * Exposed as window.YSP.
 */
(function () {
    if (window.__yspInjected) return;
    window.__yspInjected = true;

    var CHANNEL_NAMES = [];      // array of alias-arrays, one per channel
    var channelEls = {};         // normalized alias -> DOM element
    var playerEl = null;
    var backdrop = null;
    var scanTick = 0;

    function norm(s) {
        return (s || '').replace(/\s+/g, '').toLowerCase();
    }

    function inView(el) {
        var r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0 &&
            r.right > 0 && r.bottom > 0 &&
            r.left < window.innerWidth && r.top < window.innerHeight;
    }

    function isFixedOrAbsolute(el) {
        if (!el || el === document.body || el === document.documentElement) return false;
        var p = getComputedStyle(el).position;
        return p === 'fixed' || p === 'absolute';
    }

    // ---------------------------------------------------------------- player
    function findPlayer() {
        // 1) a visible <video>
        var vids = document.querySelectorAll('video');
        for (var i = 0; i < vids.length; i++) {
            if (inView(vids[i])) return vids[i];
        }
        // 2) largest, most-central element (covers HLS/iframe players)
        var candidates = document.querySelectorAll('div,section,iframe,object,embed');
        var best = null, bestScore = -1;
        var cx = window.innerWidth / 2, cy = window.innerHeight / 2;
        for (var j = 0; j < candidates.length; j++) {
            var el = candidates[j];
            if (!inView(el)) continue;
            var r = el.getBoundingClientRect();
            var area = r.width * r.height;
            var d = Math.abs((r.left + r.width / 2) - cx) + Math.abs((r.top + r.height / 2) - cy);
            var score = area - d * 2;
            if (score > bestScore) { bestScore = score; best = el; }
        }
        return best;
    }

    function ensureBackdrop() {
        if (backdrop && backdrop.parentNode) return;
        backdrop = document.createElement('div');
        backdrop.id = '__yspBackdrop';
        backdrop.style.cssText =
            'position:fixed;left:0;top:0;width:100vw;height:100vh;' +
            'background:#000;z-index:9998;';
        document.documentElement.appendChild(backdrop);
    }

    function pinPlayer() {
        var p = findPlayer();
        if (!p) return;
        if (p === playerEl) return;
        playerEl = p;
        ensureBackdrop();
        p.style.position = 'fixed';
        p.style.left = '0';
        p.style.top = '0';
        p.style.width = '100vw';
        p.style.height = '100vh';
        p.style.zIndex = '9999';
        p.style.margin = '0';
        p.style.maxWidth = 'none';
        p.style.maxHeight = 'none';
        p.style.background = '#000';
        var v = (p.tagName === 'VIDEO') ? p : p.querySelector('video');
        if (v) {
            v.style.width = '100%';
            v.style.height = '100%';
            v.style.objectFit = 'contain';
        }
    }

    // --------------------------------------------------------------- overlays
    function hideEl(el) {
        // display:none keeps the node in the DOM and still clickable via .click(),
        // which we rely on for channel switching.
        el.style.display = 'none';
    }

    function removeOverlays() {
        var all = document.querySelectorAll('*');
        var vw = window.innerWidth, vh = window.innerHeight;
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            if (el === backdrop || el === playerEl) continue;
            // never hide the player or anything inside / wrapping it
            if (playerEl && (playerEl.contains(el) || el.contains(playerEl))) continue;
            if (!isFixedOrAbsolute(el)) continue;
            if (!inView(el)) continue;

            var r = el.getBoundingClientRect();
            var pos = getComputedStyle(el).position;
            var txt = (el.innerText || el.textContent || '').replace(/\s+/g, '');

            // 1) full-screen modal (login / "download app" interstitial)
            if (pos === 'fixed' && (r.width / vw > 0.6) && (r.height / vh > 0.6)) {
                hideEl(el); continue;
            }
            // 2) top / bottom banner strip (download banner)
            var topStrip = (r.top <= vh * 0.05) && (r.height <= vh * 0.20) && (r.width >= vw * 0.4);
            var botStrip = (r.bottom >= vh * 0.95) && (r.height <= vh * 0.20) && (r.width >= vw * 0.4);
            if (topStrip || botStrip) { hideEl(el); continue; }
            // 3) right-side panel (节目单) : right edge at screen edge, tall, moderate width
            var rightPanel = (r.right >= vw * 0.95) &&
                (r.width >= vw * 0.12) && (r.width <= vw * 0.5) && (r.height >= vh * 0.4);
            if (rightPanel) { hideEl(el); continue; }
            // 4) anything whose visible text screams "download / login"
            if (pos === 'fixed' && /(下载|立即下载|打开App|前往应用|扫码下载|登录后|登录即可|下载客户端)/.test(txt)) {
                hideEl(el); continue;
            }
        }
    }

    // ----------------------------------------------------------- channel map
    function scanChannels() {
        if (!CHANNEL_NAMES.length) return;
        var nodes = document.querySelectorAll('a,button,[role=button],li,div,span');
        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            var t = norm(el.textContent);
            if (!t) continue;
            for (var k = 0; k < CHANNEL_NAMES.length; k++) {
                var aliases = CHANNEL_NAMES[k];
                for (var a = 0; a < aliases.length; a++) {
                    var key = norm(aliases[a]);
                    if (!key) continue;
                    if (t.indexOf(key) >= 0 || (key.indexOf(t) >= 0 && t.length >= 2)) {
                        channelEls[key] = el;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- public API
    window.YSP = {
        setChannels: function (json) {
            try { CHANNEL_NAMES = JSON.parse(json); } catch (e) { CHANNEL_NAMES = []; }
            scanChannels();
        },

        // Returns true if a matching control was found and clicked, else false.
        switchTo: function (aliasesJson) {
            var aliases;
            try { aliases = JSON.parse(aliasesJson); } catch (e) { aliases = []; }
            scanChannels();
            for (var a = 0; a < aliases.length; a++) {
                var el = channelEls[norm(aliases[a])];
                if (el) {
                    try {
                        el.click();
                        el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
                    } catch (e) { /* ignore */ }
                    return true;
                }
            }
            return false;
        },

        // Best-effort current program title.
        getNowPlaying: function () {
            var best = null, bestScore = -1e9;
            var nodes = document.querySelectorAll('h1,h2,h3,div,span,p,a,li');
            for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                if (!inView(el)) continue;
                if (playerEl && playerEl.contains(el)) continue;
                var t = (el.textContent || '').trim();
                if (t.length < 2 || t.length > 40) continue;
                if (/^(登录|下载|首页|直播|节目|更多|分享|高清|超清|标清|播放|暂停|全屏|cctv|卫视|频道)/i.test(t)) continue;
                var r = el.getBoundingClientRect();
                var fs = parseInt(getComputedStyle(el).fontSize, 10) || 12;
                var score = -r.top - r.left * 0.1 + fs;
                if (score > bestScore) { bestScore = score; best = t; }
            }
            return best || '';
        },

        isPlayerReady: function () {
            var v = document.querySelector('video');
            if (v) return (v.readyState >= 3 || (!v.paused && v.currentTime > 0)) ? 'true' : 'false';
            return playerEl ? 'true' : 'false';
        },

        refresh: function () { pinPlayer(); removeOverlays(); scanChannels(); }
    };

    // Keep pinning & stripping alive (the SPA may rebuild parts of the DOM).
    setInterval(function () {
        pinPlayer();
        removeOverlays();
        // re-scan the channel map periodically so late-loaded controls are found
        if ((++scanTick % 4) === 0) scanChannels();
    }, 1500);

    setTimeout(function () { pinPlayer(); removeOverlays(); scanChannels(); }, 800);

    // Push now-playing to the app every few seconds.
    setInterval(function () {
        if (window.AndroidBridge) {
            try { window.AndroidBridge.reportNowPlaying(window.YSP.getNowPlaying()); } catch (e) {}
        }
    }, 3000);
})();
