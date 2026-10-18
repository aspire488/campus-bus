/* Student pages: home, search, seat map, travel pass, my reservations. All data comes from Java (WebBridge). */
App.menu = { home: 'Home', search: 'Find a bus', seats: 'Find a bus', trips: 'My reservations', pass: 'My reservations', overview: 'Overview' };
/* Every trip has the campus at one end: dir 'OUT' = from your stop to campus (morning), 'RETURN' = from campus to your stop. */
App.q = { dir: 'OUT', stop: '', date: isoDate(0), after: '', busNo: '', sort: 'time' };

function stopOptions(stops, value, any) {
  if (value && stops.indexOf(value) < 0) { value = ''; App.q.stop = ''; }     // a stop no longer offered: never filter by it unseen
  return '<option value="">' + any + '</option>' + stops.map(function (s) {
    return '<option value="' + esc(s) + '"' + (s === value ? ' selected' : '') + '>' + esc(s) + '</option>';
  }).join('');
}

function dirOptions(dir) {
  return [['OUT', 'To campus'], ['RETURN', 'From campus']].map(function (d) {
    return '<option value="' + d[0] + '"' + (d[0] === dir ? ' selected' : '') + '>' + d[1] + '</option>';
  }).join('');
}

/* "from Vytilla to campus" / "from campus to Vytilla" - plain text: callers escape it (or use textContent). */
function journey(dir, stop) {
  var s = stop || 'any stop';
  return dir === 'RETURN' ? 'from campus to ' + s : 'from ' + s + ' to campus';
}

function dayOptions(chosen) {
  var out = '';
  if (App.q.date < isoDate(0)) App.q.date = isoDate(0);                  // the window stayed open past midnight
  chosen = chosen || App.q.date;
  for (var i = 0; i < 8; i++) {
    var iso = isoDate(i), f = fmtDate(iso);
    out += '<option value="' + iso + '"' + (iso === chosen ? ' selected' : '') + '>' + (i === 0 ? 'Today' : i === 1 ? 'Tomorrow' : f.long) + '</option>';
  }
  return out;
}

function readSearch() {
  ['dir', 'stop', 'date', 'after', 'busNo', 'sort'].forEach(function (k) {
    var el = document.getElementById('q-' + k);
    if (el) App.q[k] = el.value;
  });
}

/* ---------- Home ---------- */
App.pages.home = function () {
  var res = App.call('home');
  if (!res.ok) return App.problem(res);
  var all = App.call('stops');
  if (!all.ok) return App.problem(all);
  var d = res.data, hour = new Date().getHours(), stops = all.data;
  var hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  App.render(
    '<header class="page-head"><h1>' + hello + ', ' + esc(d.me.firstName) + '</h1><p class="lead">Where do you need to go?</p></header>' +
    '<form class="search-strip" onsubmit="readSearch(); App.go(\'search\'); return false;">' +
      '<label>Trip<select id="q-dir">' + dirOptions(App.q.dir) + '</select></label>' +
      '<label>Your stop<select id="q-stop">' + stopOptions(stops, App.q.stop, 'Any stop') + '</select></label>' +
      '<label>Date<select id="q-date">' + dayOptions() + '</select></label>' +
      '<button class="btn primary">Find a bus</button></form>' +
    '<div class="home-grid"><section class="next">' + nextTrip(d.next) + '</section>' +
    '<aside class="today"><h2 class="label">Today</h2>' +
      '<p class="stat"><b data-count="' + d.running + '">0</b> buses running</p>' +
      '<p class="stat"><b data-count="' + d.free + '">0</b> seats open</p>' +
      '<p class="stat"><b data-count="' + d.upcoming + '">0</b> upcoming ' + (d.upcoming === 1 ? 'reservation' : 'reservations') + '</p>' +
      '<h2 class="label">Recent activity</h2><ul class="activity">' + (d.recent.length ? d.recent.map(function (r) {
        return '<li><i class="dot ' + r.status.toLowerCase() + '"></i>' + (r.status === 'BOOKED' ? 'Booked' : 'Cancelled') +
          ' seat ' + r.seat + ' on ' + r.bus.busNo + '<small>' + fmtDate(r.date).long + ', ' + esc(r.bus.route) + '</small></li>';
      }).join('') : '<li class="muted">Nothing yet.</li>') + '</ul></aside></div>');
  App.countUp();
};

function nextTrip(r) {
  if (!r) return '<h2 class="label">Your next bus</h2>' + App.empty('No upcoming reservations', 'Find a bus and pick a seat. It shows up here.',
    '<button class="btn primary" onclick="App.go(\'search\')">Find a bus</button>');
  return '<h2 class="label">Your next bus</h2><div class="next-top"><span class="busno">' + r.bus.busNo + '</span><span>' +
    (r.phase === 'CURRENT' ? 'On the road now' : fmtDate(r.date).long) + '</span></div>' +
    '<h3 class="route-name">' + esc(r.bus.route) + '</h3>' + App.live(r, 620) +
    '<div class="facts"><div><small>Seat</small><b>' + pad2(r.seat) + '</b></div><div><small>Departs</small><b>' + r.bus.departure +
    '</b></div><div><small>' + (r.bus.arrivalTarget ? 'Campus target' : 'Arrives') + '</small><b>' + App.arrives(r.bus) + '</b></div><div><small>Reservation</small><b>' + r.code + '</b></div></div>' +
    '<button class="btn primary" onclick="App.go(\'pass\', {id: ' + r.id + '})">View pass</button>';
}

/* ---------- Find a bus ---------- */
App.pages.search = function () {
  var stops = App.call('stops');
  if (!stops.ok) return App.problem(stops);
  var times = ['', '07:00', '08:00', '09:00', '12:00', '16:00'];
  App.render('<header class="page-head"><h1>Find a bus</h1><p class="lead" id="count"></p></header>' +
    '<form class="filters" onchange="readSearch(); showResults();" onsubmit="return false;">' +
      '<label>Trip<select id="q-dir">' + dirOptions(App.q.dir) + '</select></label>' +
      '<button type="button" class="swap" title="Switch between the trip to campus and the trip back" onclick="swapStops()">⇄</button>' +
      '<label>Your stop<select id="q-stop">' + stopOptions(stops.data, App.q.stop, 'Any stop') + '</select></label>' +
      '<label>Date<select id="q-date">' + dayOptions() + '</select></label>' +
      '<label>Leaving after<select id="q-after">' + times.map(function (t) {
        return '<option value="' + t + '"' + (t === App.q.after ? ' selected' : '') + '>' + (t || 'Any time') + '</option>'; }).join('') + '</select></label>' +
      '<label>Bus<input id="q-busNo" placeholder="e.g. CB-03" value="' + esc(App.q.busNo) + '"></label>' +
      '<label>Sort by<select id="q-sort"><option value="time">Departure</option><option value="free">Most seats free</option>' +
      '<option value="quick">Shortest trip</option></select></label></form><div id="results"></div>');
  document.getElementById('q-sort').value = App.q.sort;
  showResults();
};

/* The same bus drives its route both ways; the trip back is labelled so the two are never confused. */
function tripTag(b) { return b.direction === 'RETURN' ? ' <span class="trip-tag">Return trip</span>' : ''; }

/* Return trip in one click: your stop -> campus becomes campus -> your stop (the stop stays the same). */
function swapStops() {
  readSearch();
  App.q.dir = App.q.dir === 'RETURN' ? 'OUT' : 'RETURN';
  App.pages.search();
}


/* The stop itself (name, time, kind: published / estimated / target). */
function stopAt(b, stop, last) {
  var list = b.stopTimes, hit = list.filter(function (s) { return s.name === stop; })[0];
  return hit || list[last ? list.length - 1 : 0];
}

/* The searched stop if this trip serves it, else '' (then the first / last stop is used). */
function onRoute(b, stop) { return b.stopTimes.some(function (s) { return s.name === stop; }) ? stop : ''; }

function minutesBetween(a, b) {
  return (+b.slice(0, 2) * 60 + +b.slice(3)) - (+a.slice(0, 2) * 60 + +a.slice(3));
}

/* Where this trip picks the rider up and drops them: the searched stop at one end, the campus at the other. */
function ends(b) {
  var out = b.direction === 'OUT';
  return { on: stopAt(b, out ? b.match : ''), off: stopAt(b, out ? '' : b.match, true) };
}

function showResults() {
  var res = App.call('search', App.q.dir, App.q.stop, App.q.date, App.q.after, App.q.busNo.trim());
  if (!res.ok) { if (!res.db) document.getElementById('results').innerHTML = App.empty('Something went wrong', res.error); return; }
  var buses = res.data, now = new Date(), today = App.q.date === isoDate(0);
  var nowHm = pad2(now.getHours()) + ':' + pad2(now.getMinutes());
  buses.sort(function (a, b) {
    var ta = ends(a).on.time, tb = ends(b).on.time;
    return App.q.sort === 'free' ? b.available - a.available : App.q.sort === 'quick' ? a.duration - b.duration : ta < tb ? -1 : ta > tb ? 1 : 0;
  });
  document.getElementById('count').textContent = buses.length + (buses.length === 1 ? ' trip ' : ' trips ') + journey(App.q.dir, App.q.stop) + ', ' + fmtDate(App.q.date).long;
  document.getElementById('results').innerHTML = buses.length ? buses.map(function (b) {
    var e = ends(b), on = e.on, off2 = e.off, board = on.time, alight = off2.time, where = on.name;
    var approx = on.kind !== 'published' || off2.kind !== 'published' ? '≈ ' : '';
    // bookable until the bus leaves its first stop (the same rule the booking itself checks)
    var gone = today && b.departure <= nowHm, off = b.status !== 'ACTIVE', full = b.available === 0;
    var action = off ? '<span class="tag amber">Not running</span>' : gone ? '<span class="tag">Left ' + esc(b.source) + ' at ' + b.departure + '</span>' :
      full ? '<span class="tag red">Full</span>' : '<button class="btn" onclick="App.go(\'seats\', {id: ' + b.id + ', dir: \'' + b.direction + '\', date: \'' + App.q.date +
        '\', stop: ' + esc(JSON.stringify(b.match)) + '})">View seats</button>';
    return '<article class="bus-row' + (gone || off ? ' dim' : '') + '"><div class="dep' + (on.kind !== 'published' ? ' est' : '') + '">' + (on.kind === 'published' ? '' : '<i class="approx">≈</i>') + on.time + '<small>at ' + esc(where) + '</small></div>' +
      '<div class="info"><span class="busno">' + esc(b.busNo) + '</span> <b>' + esc(b.route) + '</b>' + tripTag(b) +
      '<small>via ' + esc(b.stops.slice(1, -1).join(', ')) + '</small></div>' +
      '<div class="mini"><span>' + App.when(on) + '</span><i></i><span>' + App.when(off2) + '</span><small>' + approx + minutesBetween(board, alight) + ' min to ' +
        esc(off2.name) + '</small></div>' +
      '<div class="free"><b>' + b.available + '</b> of ' + b.capacity + ' free<div class="bar"><i style="width:' + b.occupancy + '%"></i></div></div>' +
      '<div class="act">' + action + '</div></article>';
  }).join('') : App.empty('No bus ' + journey(App.q.dir, App.q.stop),
    App.q.stop ? 'No bus serves this stop ' + (App.q.dir === 'RETURN' ? 'on the way back' : 'on the way to campus') +
      ' with these filters. Try the other direction, another date or "Any time".' : 'Try another date or "Any time".',
    '<button class="btn" onclick="App.q = {dir: App.q.dir, stop: \'\', date: isoDate(0), after: \'\', busNo: \'\', sort: \'time\'}; App.go(\'search\')">Clear filters</button>');
}

/* ---------- Seat map ---------- */
App.pages.seats = function (p) {
  var res = App.call('bus', p.id, p.date, p.dir || 'OUT');
  if (!res.ok) return res.db || App.render(App.empty('Bus not found', res.error));
  var b = App.bus = res.data;
  App.seat = 0;
  // the stop to preselect comes with the page (the searched stop as this route spells it), never left over from another bus
  App.stop = p.stop && b.stops.some(function (s) { return s.name === p.stop; }) ? p.stop : '';
  var admin = App.role === 'admin', booked = b.taken.length, vacant = b.status === 'ACTIVE' ? b.capacity - booked : 0;
  App.render((admin ? '<a class="back" onclick="app.openMenu(\'Schedules\')">← Schedules</a>' : '<a class="back" onclick="App.go(\'search\')">← All buses</a>') +
    '<header class="bus-head"><div><span class="busno big">' + b.busNo + '</span>' + tripTag(b) + '<h1>' + esc(b.route) + '</h1>' +
    '<p class="lead">' + fmtDate(b.date).long + ', leaves ' + b.departure + ', ' + (b.arrivalTarget ? 'campus target ' + b.window : 'arrives ' + App.arrives(b) + ', ' + b.duration + ' min') + '</p>' +
    (admin ? '<label class="day-pick">Show seats for<select onchange="App.go(\'seats\', {id: ' + b.id + ', dir: \'' + b.direction + '\', date: this.value})">' +
      dayOptions(b.date) + '</select></label>' : '') + '</div>' +
    '<div class="occupancy"><div><b>' + vacant + '</b><small>vacant</small></div><div class="red"><b>' + booked + '</b><small>booked</small></div>' +
    '<div><b>' + b.capacity + '</b><small>seats</small></div></div></header>' +
    '<ul class="legend"><li><i class="seat s-free"></i>Available</li>' + (admin ? '' : '<li><i class="seat s-sel"></i>Selected</li>') +
    '<li><i class="seat s-taken"></i>Booked</li><li><i class="seat s-off"></i>Unavailable</li>' + (b.mine ? '<li><i class="seat s-mine"></i>Yours</li>' : '') + '</ul>' +
    '<section class="strip">' + App.routeLine(b.stops, { labels: true, width: App.fit(1000), mark: App.stop }) +
      (b.stops.some(function (s) { return s.kind !== 'published'; }) ? App.timeKey : '') + '</section>' +
    '<div class="seat-layout"><div class="bus-body"><div class="cab"><span class="wheel"></span>Driver<span class="door">Door</span></div>' +
    '<div class="seat-grid">' + seatGrid(b) + '</div></div><aside class="summary" id="summary"></aside></div>');
  summary();
};

/* Bus seen from above, front on the left: two seats, the aisle, two seats. Seat n = column * 4 + lane + 1. */
function seatGrid(b) {
  var html = '', cols = Math.ceil(b.capacity / 4);
  for (var c = 0; c < cols; c++) {
    [0, 1, -1, 2, 3].forEach(function (lane) {
      var n = c * 4 + lane + 1;
      if (lane < 0) { html += '<span class="aisle"></span>'; return; }
      if (n > b.capacity) { html += '<span></span>'; return; }
      var taken = b.taken.indexOf(n) >= 0, mine = n === b.mine, off = b.status !== 'ACTIVE' || (!taken && (b.departed || b.mine || b.elsewhere));
      var cls = mine ? 's-mine' : taken ? 's-taken' : off ? 's-off' : 's-free', who = holder(b, n);
      var state = mine ? 'your seat' : taken ? 'booked' + (who ? ' by ' + who : '') : off ? 'unavailable' : 'available';
      var readOnly = App.role === 'admin' || cls !== 's-free';
      html += '<button class="seat ' + cls + '" id="seat-' + n + '"' + (readOnly ? ' disabled' : '') +
        ' onclick="pickSeat(' + n + ')" title="Seat ' + n + ', ' + esc(state) + '">' + pad2(n) + '</button>';
    });
  }
  return html;
}

/* Admins only: the student holding a seat ("Name (REG)"); students never receive names. */
function holder(b, n) {
  var p = (b.passengers || []).filter(function (x) { return x.seat === n; })[0];
  return p ? p.name + ' (' + p.regNo + ')' : '';
}

function pickSeat(n) {
  if (App.seat) document.getElementById('seat-' + App.seat).className = 'seat s-free';
  App.seat = App.seat === n ? 0 : n;
  if (App.seat) document.getElementById('seat-' + n).className = 'seat s-sel';
  summary();
}

function summary(error) {
  var b = App.bus, n = App.seat, lane = (n - 1) % 4, html;
  if (App.role === 'admin') html = '<h2>Passengers</h2><p>' + b.taken.length + ' of ' + b.capacity + ' seats booked.</p>' +
    ((b.passengers || []).length ? '<ol class="manifest">' + b.passengers.map(function (p) {
      return '<li><b>' + pad2(p.seat) + '</b>' + esc(p.name) + '<small>' + esc(p.regNo) + (p.stop ? ' · ' + esc(p.stop) : '') + '</small></li>'; }).join('') + '</ol>'
      : '<p class="muted">Nobody has booked this trip yet.</p>');
  else if (b.mine) html = '<h2>You have seat ' + pad2(b.mine) + '</h2>' + (b.status !== 'ACTIVE' ? '<p class="error">' + esc(b.busNo) +
    ' is under maintenance and not running. Contact the transport office or book another bus.</p>' : '') + '<p>One seat per student each way, each day.</p>' +
    '<button class="btn" onclick="App.go(\'trips\')">My reservations</button>';
  else if (b.elsewhere) html = '<h2>You already have a seat</h2><p>You are booked on ' + esc(b.elsewhere) + ' at this time. ' +
    'Cancel that seat first if you want this bus.</p><button class="btn" onclick="App.go(\'trips\')">My reservations</button>';
  else if (b.status !== 'ACTIVE') html = '<h2>Not running</h2><p>' + b.busNo + ' is under maintenance. Pick another bus.</p>';
  else if (b.departed) html = '<h2>This bus has left</h2><p>It departed at ' + b.departure + '. Choose a later bus or date.</p>';
  else if (!b.available) html = '<h2>This trip is full</h2><p>Every seat is booked. Try another bus on the same route.</p>';
  else if (!n) html = '<h2>Pick a seat</h2><p>Green seats are free. Click one to select it, click it again to clear.</p>';
  else html = '<small>Selected</small><div class="big-seat">Seat ' + pad2(n) + '</div><p>' + (lane === 0 || lane === 3 ? 'Window' : 'Aisle') +
    ' seat, ' + (lane < 2 ? 'right' : 'left') + ' side</p>' + stopPicker(b) +
    '<button class="btn primary wide" id="confirm" onclick="confirmSeat()">Confirm seat ' + pad2(n) + '</button>';
  var box = document.getElementById('summary');
  if (box) box.innerHTML = (error ? '<p class="error">' + esc(error) + '</p>' : '') + html;
}

/* Where the rider gets on (to campus) or off (going back): every stop except the campus. */
function stopPicker(b) {
  var back = b.direction === 'RETURN', list = b.stops.slice(back ? 1 : 0, back ? b.stops.length : b.stops.length - 1);
  var chosen = App.stop;
  return '<label class="stop-pick">' + (back ? 'Getting off at' : 'Getting on at') + '<select id="my-stop" onchange="App.stop = this.value">' +
    '<option value="">Choose your stop</option>' + list.map(function (s) {
      return '<option value="' + esc(s.name) + '"' + (s.name === chosen ? ' selected' : '') + '>' + esc(s.name) + ' · ' + App.when(s) + '</option>';
    }).join('') + '</select></label>';
}

function confirmSeat() {
  var stop = document.getElementById('my-stop').value;
  if (!stop) return summary('Choose the stop where you get ' + (App.bus.direction === 'RETURN' ? 'off.' : 'on.'));
  App.stop = stop;
  var btn = document.getElementById('confirm');
  btn.disabled = true;
  btn.textContent = 'Booking…';
  var bus = App.bus, seat = App.seat;          // what the student confirmed - not whatever is clicked in the next 30 ms
  setTimeout(function () {                     // let the button repaint before the database call
    var res = App.call('book', bus.id, bus.date, bus.direction, seat, stop);
    if (res.ok) { App.stop = ''; return App.go('pass', { id: res.data.id, fresh: true }); }
    if (res.db) return;                        // App.call already shows "Database unavailable"
    App.pages.seats({ id: bus.id, date: bus.date, dir: bus.direction, stop: stop });   // reload, keeping the chosen stop
    if (document.getElementById('summary')) summary(res.error);
    else App.render(App.empty('Seat not booked', res.error));              // the seat map itself could not be reloaded
  }, 30);
}

/* ---------- Travel pass ---------- */
App.pages.pass = function (p) {
  var res = App.call('reservation', p.id);
  if (!res.ok) return res.db || App.render(App.empty('Pass not available', res.error,
    '<button class="btn" onclick="App.go(\'trips\')">My reservations</button>'));
  var r = res.data, b = r.bus, f = fmtDate(r.date), cancelled = r.status === 'CANCELLED';
  App.render('<header class="page-head"><h1>' + (p.fresh ? 'Seat booked' : 'Your pass') + '</h1><p class="lead">' +
    (p.fresh ? 'Seat ' + r.seat + ' on ' + b.busNo + ' is reserved for you.' : 'Reservation ' + r.code) + '</p></header>' +
    '<div class="pass' + (p.fresh ? ' fresh' : '') + (cancelled ? ' void' : '') + '"><div class="pass-main">' +
      '<div class="pass-brand"><svg viewBox="0 0 40 40" width="22"><path d="M31 9A15 15 0 1 0 31 31" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round"/>' +
      '<circle cx="31" cy="9" r="6" fill="#E8431B"/></svg>CAMPUS BUS<span>' + esc(r.institution) + '</span></div>' +
      '<div class="pass-bus">' + b.busNo + '</div><div class="pass-route">' + esc(b.route) + '</div>' + App.routeLine(r.stops, { labels: true, width: 460, step: 92, mark: r.stop }) +
      '<div class="facts"><div><small>Date</small><b>' + f.long + '</b></div><div><small>Departs</small><b>' + b.departure + '</b></div>' +
      '<div><small>' + (b.arrivalTarget ? 'Campus target' : 'Arrives') + '</small><b>' + App.arrives(b) + '</b></div></div>' +
      '<div class="pass-name">' + esc(r.student) + (r.stop ? '<small>' + (b.direction === 'RETURN' ? 'Gets off at ' : 'Gets on at ') + esc(r.stop) +
        ' · ' + (onRoute(b, r.stop) ? App.when(stopAt(b, r.stop)) : 'no longer on this route') + '</small>' : '') + '</div></div>' +
    '<div class="pass-stub"><small>Seat</small><div class="pass-seat">' + pad2(r.seat) + '</div>' + barcode(r.code) +
      '<small>Reference ' + r.code + '</small></div><div class="stamp">' + (cancelled ? 'Cancelled' : 'Confirmed') + '</div></div>' +
    (cancelled ? '' : App.live(r, 760)) +
    // only for a trip still to come or on the road: the bus's status today says nothing about a finished trip
    ((r.phase === 'UPCOMING' || r.phase === 'CURRENT') && b.status !== 'ACTIVE' ? '<p class="error">' + esc(b.busNo) +
      ' is under maintenance and not running on this date. Contact the transport office or book another bus.</p>' : '') +
    (r.phase === 'UPCOMING' ? notices(r, p.fresh) : '') +
    '<div class="pass-actions"><button class="btn primary" onclick="var m = app.print(); if (m) alert(m);">Print pass</button>' +
    '<button class="btn" onclick="savePass(' + r.id + ')">Save pass (PDF)</button>' +
    '<button class="btn" onclick="App.go(\'trips\')">My reservations</button></div><p id="pass-msg" class="muted"></p>');
  App.passId = r.id;
  if (r.phase === 'UPCOMING' && (p.fresh || r.emailStatus === 'PENDING')) watchEmail(r.id, 0);   // "sending…" until it settles
};

/* Re-reads the email status every 2 s (at most 30 s) while this very pass is on screen, until it is no longer being sent. */
function watchEmail(id, tries) {
  clearTimeout(App.emailTimer);
  App.emailTimer = setTimeout(function () {
    var el = document.querySelector('.notices');
    if (App.passId !== id || !el) return;                                 // the student moved on: stop quietly
    var res;
    try { res = JSON.parse(app.reservation(String(id))); } catch (e) { return; }
    if (!res.ok || !document.querySelector('.notices')) return;
    var s = res.data.emailStatus, waiting = (s === '' || s === 'PENDING') && tries < 15;
    document.querySelector('.notices').outerHTML = notices(res.data, waiting);
    if (waiting) watchEmail(id, tries + 1);
  }, 2000);
}

/* The confirmation email's status - kept apart from the booking, which stands whatever it says.
   Right after booking ('fresh') the email is still on its way; on a later visit an empty status means it was never sent. */
function notices(r, fresh) {
  var s = r.emailStatus || (fresh ? 'PENDING' : '');
  var words = { SENT: 'sent', PENDING: 'sending…', FAILED: 'not sent', NOT_CONFIGURED: 'not set up', '': 'not sent' };
  // failed, never sent, or left pending by a closed app: it can be sent again (an email already sent is never re-sent)
  var again = s === 'FAILED' || s === '' || (s === 'PENDING' && !fresh);
  return '<p class="muted notices">Confirmation email: ' + words[s] +
    (again ? ' <button class="btn ghost" id="resend" onclick="resend(' + r.id + ')">Send again</button>' : '') +
    '<br><small>Your seat is booked either way; you can always print or save this pass.</small></p>';
}

/* A message under the pass, if the student is still on the pass page. */
function passMessage(text) {
  var el = document.getElementById('pass-msg');
  if (el) el.textContent = text;
}

function savePass(id) {
  var res = App.call('savePass', id);
  if (res.db) return;
  passMessage(res.ok ? (res.data ? 'Saving ' + res.data + '… a message confirms when it is done.' : '') : res.error);
}

function resend(id) {
  var btn = document.getElementById('resend');
  if (btn) btn.disabled = true;                                          // one click, one email
  var res = App.call('resend', id);
  if (res.db) return;
  if (!res.ok) { passMessage(res.error); if (btn) btn.disabled = false; return; }
  passMessage('Sending the confirmation again…');
  watchEmail(id, 0);                                                      // shows the new status once the sender has finished
}

/* Decorative bars generated from the reservation code (not a scannable code). */
function barcode(code) {
  var x = 0, bars = '';
  for (var i = 0; i < code.length * 3; i++) {
    var v = (code.charCodeAt(i % code.length) * (i + 7)) % 5 + 1;
    if (i % 2 === 0) bars += '<rect x="' + x + '" y="0" width="' + v + '" height="44"/>';
    x += v + 1;
  }
  return '<svg class="bars" viewBox="0 0 ' + x + ' 44" preserveAspectRatio="none">' + bars + '</svg>';
}

/* ---------- My reservations ---------- */
App.tab = 'UPCOMING';
App.pages.trips = function () {
  var res = App.call('reservations');
  if (!res.ok) return App.problem(res);
  var all = App.mine = res.data, tabs = [['UPCOMING', 'Upcoming'], ['CURRENT', 'On the road'], ['COMPLETED', 'Completed'], ['CANCELLED', 'Cancelled']];
  var list = all.filter(function (r) { return r.phase === App.tab; });
  if (App.tab === 'UPCOMING') list.reverse();
  App.render('<header class="page-head"><h1>My reservations</h1><p class="lead">' + all.length + ' in total</p></header>' +
    '<nav class="tabs">' + tabs.map(function (t) {
      var n = all.filter(function (r) { return r.phase === t[0]; }).length;
      return '<button class="' + (t[0] === App.tab ? 'on' : '') + '" onclick="App.tab=\'' + t[0] + '\'; App.pages.trips()">' + t[1] + ' <span>' + n + '</span></button>';
    }).join('') + '</nav>' +
    (list.length ? '<div class="trips">' + list.map(tripRow).join('') + '</div>' :
      App.empty('Nothing here', App.tab === 'UPCOMING' ? 'Book a seat and it will appear here.' : 'No reservations in this list.',
        App.tab === 'UPCOMING' ? '<button class="btn primary" onclick="App.go(\'search\')">Find a bus</button>' : '')));
}

function tripRow(r) {
  var f = fmtDate(r.date), b = r.bus;
  return '<article class="trip ' + r.phase.toLowerCase() + '" id="trip-' + r.id + '"><div class="cal"><small>' + f.day + '</small><b>' + f.date +
    '</b><small>' + f.month + '</small></div><div class="info"><span class="busno">' + esc(b.busNo) + '</span> <b>' + esc(b.route) + '</b>' + tripTag(b) +
    (r.phase === 'UPCOMING' && b.status !== 'ACTIVE' ? ' <span class="tag amber">Not running</span>' : '') +
    '<small>' + (r.stop ? (b.direction === 'RETURN' ? 'off at ' : 'on at ') + esc(r.stop) + (onRoute(b, r.stop) ? '' : ' (no longer on this route)') + ' · ' : '') + b.departure + ' to ' + App.arrives(b) +
    ', seat ' + r.seat + ', ' + r.code + '</small></div>' +
    '<div class="act"><button class="btn" onclick="App.go(\'pass\', {id: ' + r.id + '})">View pass</button>' +
    (r.phase === 'UPCOMING' ? '<button class="btn ghost" onclick="askCancel(' + r.id + ')">Cancel</button>' : '') + '</div></article>';
}

/* Inline confirmation instead of a pop-up: the row itself asks. */
function askCancel(id) {
  var r = App.mine.filter(function (x) { return x.id === id; })[0], row = document.getElementById('trip-' + id);
  row.classList.add('asking');
  row.querySelector('.act').innerHTML = '<span>Cancel seat ' + r.seat + ' on ' + r.bus.busNo + '?</span>' +
    '<button class="btn" onclick="App.pages.trips()">Keep it</button><button class="btn primary" onclick="doCancel(' + id + ')">Cancel reservation</button>';
}

function doCancel(id) {
  var row = document.getElementById('trip-' + id);
  row.querySelectorAll('button').forEach(function (b) { b.disabled = true; });     // one click, one cancel
  var res = App.call('cancel', id);
  if (res.db) return;                                                             // "Database unavailable" is already shown
  if (!res.ok) { row.querySelector('.act').innerHTML = '<span class="error">' + esc(res.error) + '</span>' +
    '<button class="btn" onclick="App.pages.trips()">OK</button>'; return; }
  App.tab = 'CANCELLED';
  App.pages.trips();
}
