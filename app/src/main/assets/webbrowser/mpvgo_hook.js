// mpvGo Go browser hook. Injected at document start into every frame.
// Talks to the app through window.mpvgoBridge (androidx.webkit WebMessageListener).
(function () {
  'use strict';
  if (window.__mpvgo) return;
  var bridge = window.mpvgoBridge;
  if (!bridge) return;
  var api = (window.__mpvgo = {});
  var sent = Object.create(null);

  function post(obj) {
    try { obj.frame = location.href; bridge.postMessage(JSON.stringify(obj)); } catch (e) {}
  }

  var MEDIA_CT = /mpegurl|dash\+xml|^video\/(?!mp2t|iso\.segment)|^audio\/(?!mpegurl|x-mpegurl|aac$)/i;
  var MEDIA_URL = /\.(m3u8|mpd|mp4|webm|mkv|m4v|mov)(\?|#|$)/i;

  var lastUserTapTime = 0;
  function noteUserTap() { lastUserTapTime = Date.now(); }
  window.addEventListener('pointerdown', noteUserTap, true);
  window.addEventListener('touchstart', noteUserTap, true);
  window.addEventListener('click', noteUserTap, true);

  function hasUserGesture() {
    var now = Date.now();
    var recentTap = (now - lastUserTapTime) < 1500;
    var navActive = !!(navigator.userActivation && (navigator.userActivation.isActive || navigator.userActivation.hasBeenActive));
    return recentTap || navActive;
  }

  function reportMedia(url, mime, len, via) {
    if (!url || typeof url !== 'string') return;
    if (url.indexOf('http') !== 0) return;
    var key = url + '|' + (mime || '');
    if (sent[key]) return;
    sent[key] = 1;
    post({ t: 'media', url: url, mime: mime || '', len: len || -1, via: via });
  }

  function absolute(u) {
    try { return new URL(u, location.href).href; } catch (e) { return u; }
  }

  // fetch(): read the real Content-Type from the response the page already got, and sniff manifests.
  if (window.fetch) {
    var origFetch = window.fetch;
    window.fetch = function () {
      var p = origFetch.apply(this, arguments);
      try {
        p.then(function (r) {
          try {
            var ct = (r.headers.get('content-type') || '').toLowerCase();
            var cl = parseInt(r.headers.get('content-length') || '-1', 10);
            if (MEDIA_CT.test(ct) || MEDIA_URL.test(r.url)) {
              reportMedia(r.url, ct, cl, 'fetch');
            } else if (cl === -1 || cl <= 65536) {
              if (!ct || ct.indexOf('text') !== -1 || ct.indexOf('octet-stream') !== -1 || ct.indexOf('xml') !== -1) {
                var clone = r.clone();
                clone.text().then(function (text) {
                  if (!text) return;
                  var head = text.slice(0, 500);
                  if (head.indexOf('#EXTM3U') === 0) {
                    reportMedia(r.url, 'application/vnd.apple.mpegurl', text.length, 'fetch_sniff');
                  } else if (head.indexOf('<MPD') !== -1) {
                    reportMedia(r.url, 'application/dash+xml', text.length, 'fetch_sniff');
                  }
                }, function () {});
              }
            }
          } catch (e) {}
        }, function () {});
      } catch (e) {}
      return p;
    };
  }

  // XMLHttpRequest: check the headers as soon as they arrive and sniff responseText.
  var XHR = window.XMLHttpRequest;
  if (XHR) {
    var origOpen = XHR.prototype.open;
    XHR.prototype.open = function (method, url) {
      try { this.__mpvgoUrl = absolute(url); } catch (e) {}
      return origOpen.apply(this, arguments);
    };
    var origSend = XHR.prototype.send;
    XHR.prototype.send = function () {
      var xhr = this;
      try {
        xhr.addEventListener('readystatechange', function () {
          if (xhr.readyState === 2) {
            try {
              var ct = (xhr.getResponseHeader('content-type') || '').toLowerCase();
              var url = xhr.responseURL || xhr.__mpvgoUrl;
              var cl = parseInt(xhr.getResponseHeader('content-length') || '-1', 10);
              if (MEDIA_CT.test(ct) || MEDIA_URL.test(url || '')) {
                reportMedia(url, ct, cl, 'xhr');
              }
            } catch (e) {}
          } else if (xhr.readyState === 4) {
            try {
              var url4 = xhr.responseURL || xhr.__mpvgoUrl;
              if (url4 && (xhr.responseType === '' || xhr.responseType === 'text')) {
                var text = xhr.responseText;
                if (text && text.length <= 65536) {
                  var head4 = text.slice(0, 500);
                  if (head4.indexOf('#EXTM3U') === 0) {
                    reportMedia(url4, 'application/vnd.apple.mpegurl', text.length, 'xhr_sniff');
                  } else if (head4.indexOf('<MPD') !== -1) {
                    reportMedia(url4, 'application/dash+xml', text.length, 'xhr_sniff');
                  }
                }
              }
            } catch (e) {}
          }
        });
      } catch (e) {}
      return origSend.apply(this, arguments);
    };
  }

  // MediaSource / MSE detection.
  if (window.MediaSource) {
    var origAddSourceBuffer = MediaSource.prototype.addSourceBuffer;
    MediaSource.prototype.addSourceBuffer = function (mime) {
      post({ t: 'mse', mime: String(mime || '') });
      return origAddSourceBuffer.apply(this, arguments);
    };
  }

  // DRM: any EME request marks the page as protected.
  if (navigator.requestMediaKeySystemAccess) {
    var origEme = navigator.requestMediaKeySystemAccess.bind(navigator);
    navigator.requestMediaKeySystemAccess = function (keySystem) {
      post({ t: 'drm', keySystem: String(keySystem) });
      return origEme.apply(null, arguments);
    };
  }

  function getMediaDetails(el) {
    var src = el.currentSrc || el.src || '';
    if (!src) {
      var s = el.querySelector && el.querySelector('source[src]');
      if (s) src = absolute(s.getAttribute('src'));
    }
    var rect = (el.getBoundingClientRect && el.getBoundingClientRect()) || {};
    var w = Math.round(rect.width || el.clientWidth || el.offsetWidth || el.videoWidth || 0);
    var h = Math.round(rect.height || el.clientHeight || el.offsetHeight || el.videoHeight || 0);
    var isControls = !!(el.controls || el.hasAttribute('controls'));
    var isMuted = !!(el.muted || el.defaultMuted);
    var isLoop = !!(el.loop || el.hasAttribute('loop'));
    var isAutoplay = !!(el.autoplay || el.hasAttribute('autoplay'));
    var gesture = hasUserGesture();
    return {
      t: 'play',
      src: src,
      video: el.tagName === 'VIDEO',
      title: document.title || '',
      poster: el.poster || '',
      duration: isFinite(el.duration) ? el.duration : -1,
      drm: !!el.mediaKeys,
      gesture: gesture,
      muted: isMuted,
      loop: isLoop,
      autoplay: isAutoplay,
      controls: isControls,
      width: w,
      height: h
    };
  }

  // Media elements: src changes and play().
  var ME = window.HTMLMediaElement;
  if (ME) {
    var srcDesc = Object.getOwnPropertyDescriptor(ME.prototype, 'src');
    if (srcDesc && srcDesc.set) {
      Object.defineProperty(ME.prototype, 'src', {
        configurable: true,
        enumerable: srcDesc.enumerable,
        get: srcDesc.get,
        set: function (v) {
          try { if (typeof v === 'string' && v.indexOf('http') === 0) reportMedia(absolute(v), '', -1, 'src'); } catch (e) {}
          return srcDesc.set.call(this, v);
        },
      });
    }
    var origPlay = ME.prototype.play;
    ME.prototype.play = function () {
      try {
        if (!this.__mpvgoAllowed) {
          var details = getMediaDetails(this);
          post(details);
        }
      } catch (e) {}
      return origPlay.apply(this, arguments);
    };
  }

  // Element.setAttribute hook for src attributes
  var origSetAttr = Element.prototype.setAttribute;
  Element.prototype.setAttribute = function (name, val) {
    try {
      if (name && typeof name === 'string' && name.toLowerCase() === 'src') {
        var tag = this.tagName;
        if (tag === 'VIDEO' || tag === 'AUDIO' || tag === 'SOURCE') {
          if (typeof val === 'string' && val.indexOf('http') === 0) {
            reportMedia(absolute(val), '', -1, 'setAttribute');
          }
        }
      }
    } catch (e) {}
    return origSetAttr.apply(this, arguments);
  };

  // Capture-phase document listeners for loadedmetadata, play, playing
  document.addEventListener('loadedmetadata', function (e) {
    var el = e.target;
    if (!el || (el.tagName !== 'VIDEO' && el.tagName !== 'AUDIO')) return;
    try {
      var src = el.currentSrc || el.src;
      if (!src) {
        var s = el.querySelector && el.querySelector('source[src]');
        if (s) src = absolute(s.getAttribute('src'));
      }
      if (src) {
        if (src.indexOf('blob:') === 0) {
          post({ t: 'blob_mse', src: src });
        } else if (src.indexOf('http') === 0) {
          reportMedia(src, '', -1, 'loadedmetadata');
        }
      }
    } catch (e) {}
  }, true);

  document.addEventListener('play', function (e) {
    var el = e.target;
    if (!el || el.tagName !== 'VIDEO') return;
    try {
      if (!el.__mpvgoAllowed) {
        var details = getMediaDetails(el);
        if (details.src && details.src.indexOf('http') === 0) {
          reportMedia(details.src, '', -1, 'event_play');
        }
        post(details);
      }
    } catch (e) {}
  }, true);

  document.addEventListener('playing', function (e) {
    var el = e.target;
    if (!el || (el.tagName !== 'VIDEO' && el.tagName !== 'AUDIO')) return;
    try {
      var src = el.currentSrc || el.src;
      if (src && src.indexOf('http') === 0) {
        reportMedia(src, '', -1, 'event_playing');
      }
    } catch (e) {}
  }, true);
      } catch (e) {}
      return origPlay.apply(this, arguments);
    };
  }

  function allMedia() {
    return Array.prototype.slice.call(document.querySelectorAll('video,audio'));
  }

  api.pauseAll = function () {
    allMedia().forEach(function (m) { try { m.pause(); } catch (e) {} });
  };

  // Cosmetic filtering.
  var styleEl = null;
  var genericHide = false;
  var exceptions = '';
  var seenClasses = Object.create(null);
  var seenIds = Object.create(null);

  function addCss(selectors) {
    if (!selectors) return;
    try {
      if (!styleEl) {
        styleEl = document.createElement('style');
        styleEl.setAttribute('data-mpvgo', '');
        (document.head || document.documentElement).appendChild(styleEl);
      }
      var parts = selectors.split(',');
      var css = '';
      // Chunk so one bad selector can't void the whole rule.
      for (var i = 0; i < parts.length; i += 200) {
        css += parts.slice(i, i + 200).join(',') + '{display:none!important}\n';
      }
      styleEl.appendChild(document.createTextNode(css));
    } catch (e) {}
  }

  var pending = null;
  function collect(root) {
    if (genericHide) return;
    var classes = [];
    var ids = [];
    var nodes = root.querySelectorAll ? root.querySelectorAll('[class],[id]') : [];
    var all = [root].concat(Array.prototype.slice.call(nodes, 0, 3000));
    all.forEach(function (n) {
      if (!n || n.nodeType !== 1) return;
      if (n.id && !seenIds[n.id]) { seenIds[n.id] = 1; ids.push(n.id); }
      var cl = n.classList;
      if (cl) for (var j = 0; j < cl.length; j++) {
        var c = cl[j];
        if (!seenClasses[c]) { seenClasses[c] = 1; classes.push(c); }
      }
    });
    if (classes.length || ids.length) post({ t: 'classids', classes: classes, ids: ids, exceptions: exceptions });
  }

  function startObserver() {
    if (genericHide || !window.MutationObserver) return;
    collect(document.documentElement);
    new MutationObserver(function (records) {
      if (pending) return;
      pending = setTimeout(function () {
        pending = null;
        records = null;
        collect(document.documentElement);
      }, 400);
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
  }

  bridge.addEventListener('message', function (event) {
    var msg;
    try { msg = JSON.parse(event.data); } catch (e) { return; }
    switch (msg.cmd) {
      case 'pause':
        api.pauseAll();
        break;
      case 'mute':
        allMedia().forEach(function (m) { try { m.muted = true; } catch (e) {} });
        break;
      case 'unmute':
        allMedia().forEach(function (m) { try { m.muted = false; } catch (e) {} });
        break;
      case 'allowPlay':
        allMedia().forEach(function (m) { m.__mpvgoAllowed = true; try { m.muted = false; m.play(); } catch (e) {} });
        break;
      case 'cosmetic':
        genericHide = !!msg.genericHide;
        exceptions = msg.exceptions || '';
        addCss(msg.css);
        if (msg.script) {
          try { var s = document.createElement('script'); s.textContent = msg.script; (document.head || document.documentElement).appendChild(s); s.remove(); } catch (e) {}
        }
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', startObserver);
        else startObserver();
        break;
      case 'css':
        addCss(msg.css);
        break;
    }
  });

  post({ t: 'hello', top: window === window.top });
})();
