/* Shared web-layer code: page router, Java bridge calls and small drawing helpers. */
var App = {
  role: 'student',
  pages: {},          // filled by student.js and admin.js: name -> function(params)
  menu: {},           // page -> Swing menu item to highlight

  boot: function (role, page) {
    App.role = role;
    window.onerror = function (msg, src, line) { app.log(msg + ' @' + line); };
    App.go(page);
  },

  /** Shows a page. Every page is a function that returns HTML (or fills the view itself). */
  go: function (page, params) {
    var view = document.getElementById('view');
    view.classList.remove('enter');
    void view.offsetWidth;                       // restart the entrance animation
    view.classList.add('enter');
    view.innerHTML = '<p class="muted loading">Loading…</p>';
    window.scrollTo(0, 0);
    clearInterval(App.timer);                    // stop the previous page's live refresh
    var item = App.role === 'admin' ? App.adminMenu[page] : App.menu[page];
    if (item) app.navigated(item);
    setTimeout(function () { App.pages[page](params || {}); }, 0);
  },

  render: function (html) { document.getElementById('view').innerHTML = html; },

  /** A page that could not load: shows why (App.call already shows "Database unavailable" when MySQL is down). */
  problem: function (res) { if (!res.db) App.render(App.empty('Something went wrong', res.error || 'Please try again.')); },

  /** Calls a public method of WebBridge.java and returns its data, or shows the error and returns null. */
  call: function (method) {
    var args = Array.prototype.slice.call(arguments, 1).map(String);
    var res = JSON.parse(app[method].apply(app, args));
    if (!res.ok && res.db) App.render(App.empty('Database unavailable', res.error));
    return res;
  },

  empty: function (title, text, action) {
    return '<section class="empty"><svg viewBox="0 0 120 24" class="empty-line"><line x1="6" y1="12" x2="114" y2="12"/>' +
      '<circle cx="12" cy="12" r="6"/><circle cx="108" cy="12" r="6" class="end"/></svg>' +
      '<h2>' + esc(title) + '</h2><p>' + esc(text) + '</p>' + (action || '') + '</section>';
  },

  /**
   * Route line as SVG with every stop's name and time. A long route wraps into rows like a metro map
   * (left to right, then right to left, turning outside the labels), so no stop is ever left out.
   * opts: labels, width, step (gap between stops), mark (the rider's stop), progress (0..1, where's my bus).
   */
  routeLine: function (stops, opts) {
    opts = opts || {};
    var w = opts.width || 640, n = stops.length, gap = opts.step || 124, pad = gap / 2 + 14;   // centred end labels fit, and the turn passes outside them
    var per = Math.max(2, Math.min(n, Math.floor((w - 2 * pad) / gap) + 1)), rows = Math.ceil(n / per);
    var step = (w - 2 * pad) / (per - 1), rowH = opts.labels ? 96 : 40, chars = Math.max(8, Math.floor((step - 8) / 7));
    var pts = stops.map(function (s, i) {
      var r = Math.floor(i / per), c = i % per;
      return { x: r % 2 ? w - pad - c * step : pad + c * step, y: 18 + r * rowH, r: r };
    });
    var d = 'M' + pts[0].x + ' ' + pts[0].y;
    for (var i = 1; i < n; i++) {
      var a = pts[i - 1], b = pts[i];
      if (a.r === b.r) d += ' L' + b.x + ' ' + b.y;
      else {                                                    // turn outside the row, clear of the labels
        var out = (a.x > w / 2 ? 1 : -1) * (gap / 2 + 6) * 1.35;   // the curve's widest point is about gap/2 + 6 from the stop
        d += ' C' + (a.x + out) + ' ' + a.y + ' ' + (b.x + out) + ' ' + b.y + ' ' + b.x + ' ' + b.y;
      }
    }
    var h = (rows - 1) * rowH + (opts.labels ? 92 : 36);
    var svg = '<svg class="route" viewBox="0 0 ' + w + ' ' + h + '" width="100%"><path class="line draw" d="' + d + '"/>';
    var at = null;
    if (opts.progress != null) {                                // the bus marker, between two stops
      var idx = opts.progress * (n - 1), k = Math.min(n - 2, Math.floor(idx)), f = idx - k;
      at = { x: pts[k].x + (pts[k + 1].x - pts[k].x) * f, y: pts[k].y + (pts[k + 1].y - pts[k].y) * f };
      var done = 'M' + pts[0].x + ' ' + pts[0].y;
      for (var j = 1; j <= k; j++) done += ' L' + pts[j].x + ' ' + pts[j].y;
      svg += '<path class="line done" d="' + done + ' L' + at.x + ' ' + at.y + '"/>';
    }
    stops.forEach(function (s, i) {
      var p = pts[i], end = i === n - 1, mine = s.name === opts.mark;
      svg += '<circle cx="' + p.x + '" cy="' + p.y + '" r="' + (end ? 9 : mine ? 8 : 6) + '" class="' + (end ? 'end' : mine ? 'mine' : '') + '">' +
        '<title>' + esc(s.name) + ' ' + App.when(s) + '</title></circle>';
      if (!opts.labels) return;
      var lines = App.wrap(s.name, chars), y = p.y + 28;
      lines.forEach(function (l, k2) {
        svg += '<text x="' + p.x + '" y="' + (y + k2 * 14) + '" text-anchor="middle"' + (mine ? ' class="mine"' : '') + '>' + esc(l) + '</text>';
      });
      svg += '<text class="t' + (s.kind && s.kind !== 'published' ? ' est' : '') + '" x="' + p.x + '" y="' + (y + lines.length * 14 + 4) +
        '" text-anchor="middle">' + App.when(s) + '</text>';   // centred: a label never reaches past half a gap
    });
    if (at) svg += '<g class="bus-marker" transform="translate(' + at.x + ',' + at.y + ')"><rect x="-13" y="-30" width="26" height="16" rx="4"/>' +
      '<path d="M-5 -14 L0 -8 L5 -14 Z"/></g>';
    return svg + '</svg>';
  },

  /** A route line's drawing width: the page's real width (at most {@code max}), so labels keep their size on small windows. */
  fit: function (max) { return Math.max(520, Math.min(max, document.getElementById('view').clientWidth - 140)); },

  /** Splits a stop name into at most two lines of about {@code chars} characters (whole words; a third line is cut with …). */
  wrap: function (text, chars) {
    var lines = [''];
    text.split(' ').forEach(function (word) {
      if (word.length > chars) word = word.slice(0, chars - 1) + '…';   // one very long word: cut (full name in the tooltip)
      var cur = lines[lines.length - 1];
      if (!cur || (cur + ' ' + word).length <= chars) lines[lines.length - 1] = cur ? cur + ' ' + word : word;
      else lines.push(word);
    });
    if (lines.length > 2) lines = [lines[0], lines.slice(1).join(' ').slice(0, chars - 1) + '…'];
    return lines;
  },

  /** One line under a route line: what bold, ≈ and "by" mean. */
  timeKey: '<p class="muted small stopkey">Bold: printed in the schedule. ≈ estimated between printed stops. "by": the campus arrival target.</p>',

  /** A stop's time the way it should be read: as printed, "≈" when estimated, "by" for the campus arrival target. */
  when: function (s) { return !s.kind || s.kind === 'published' ? s.time : s.kind === 'target' ? 'by ' + s.time : '≈ ' + s.time; },

  /** A trip's arrival: a printed time, or "by 08:40" when it is the office's campus target. */
  arrives: function (b) { return b.arrivalTarget ? 'by ' + b.arrival : b.direction === 'RETURN' ? '≈ ' + b.arrival : b.arrival; },   // evening: derived

  /** "Where's my bus": the timetable estimate from Bus.estimatePosition(), as one line of text - never stated as fact. */
  liveText: function (t, r) {
    if (t.state === 'NOT_RUNNING') return 'This bus is not running today.';
    if (t.state === 'ARRIVED') return 'Should have reached ' + esc(t.lastStop) + (r.bus.arrivalTarget ? ' (campus target ' + r.bus.arrival + ').' : '.');
    if (t.state === 'WAITING') return t.minutesToNext > 90 ? 'Leaves ' + esc(t.nextStop) + ' at ' + r.bus.departure + ' on ' + fmtDate(r.date).long + '.'
      : 'Leaves ' + esc(t.nextStop) + ' in ' + t.minutesToNext + ' min.';
    return 'Should have passed ' + esc(t.lastStop) + '. Next: ' + esc(t.nextStop) + ' around ' + t.nextTime + ' (' + t.minutesToNext + ' min). ' +
      'About ' + t.minutesToEnd + ' min to ' + esc(r.bus.destination) + '.';
  },

  /** Live block for a reservation; refreshes itself every 30 seconds while the page is open. */
  live: function (r, width) {
    if (r.status === 'CANCELLED') return '';
    var html = function (x) {
      return '<h2 class="label">Where&#39;s my bus <span class="pulse"></span></h2><p class="live-text">' + App.liveText(x.live, x) + '</p>' +
        App.routeLine(x.stops, { labels: true, width: width, progress: x.live.progress, mark: x.stop }) +
        '<p class="muted small">Estimated from the published timetable, updated ' + x.live.updated + '. Not live GPS: buses do not report their position.</p>';
    };
    clearInterval(App.timer);
    App.timer = setInterval(function () {
      var box = document.getElementById('live'), res = JSON.parse(app.reservation(String(r.id)));
      if (box && res.ok) box.innerHTML = html(res.data);
    }, 30000);
    return '<section class="live" id="live">' + html(r) + '</section>';
  },

  /** Animates numbers inside [data-count] from 0 to their value. */
  countUp: function () {
    var els = document.querySelectorAll('[data-count]'), start = Date.now();
    (function tick() {
      var p = Math.min(1, (Date.now() - start) / 700), e = 1 - Math.pow(1 - p, 3);
      for (var i = 0; i < els.length; i++) els[i].textContent = Math.round(els[i].getAttribute('data-count') * e);
      if (p < 1) requestAnimationFrame(tick);
    })();
  }
};

function esc(s) {
  return String(s).replace(/[&<>"']/g, function (c) { return '&#' + c.charCodeAt(0) + ';'; });
}

/** "2026-10-09" -> {day:"Fri", date:"9", month:"Oct", long:"Fri 9 Oct"} */
function fmtDate(iso) {
  var p = iso.split('-'), d = new Date(+p[0], p[1] - 1, +p[2]);
  var day = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getDay()];
  var month = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getMonth()];
  return { day: day, date: String(d.getDate()), month: month, long: day + ' ' + d.getDate() + ' ' + month };
}

function isoDate(offsetDays) {
  var d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
}

function pad2(n) { return ('0' + n).slice(-2); }
