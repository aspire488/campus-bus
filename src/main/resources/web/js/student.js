/* Student pages: home, search, seat map, travel pass, my reservations. All data comes from Java (WebBridge). */
App.menu = { home: 'Home', search: 'Find a bus', seats: 'Find a bus', trips: 'My reservations', pass: 'My reservations', overview: 'Overview' };
App.q = { from: '', to: '', date: isoDate(0), after: '', busNo: '', sort: 'time' };

function stopOptions(stops, value, any) {
  return '<option value="">' + any + '</option>' + stops.map(function (s) {
    return '<option' + (s === value ? ' selected' : '') + '>' + esc(s) + '</option>';
  }).join('');
}

function dayOptions(chosen) {
  var out = '';
  chosen = chosen || App.q.date;
  for (var i = 0; i < 8; i++) {
    var iso = isoDate(i), f = fmtDate(iso);
    out += '<option value="' + iso + '"' + (iso === chosen ? ' selected' : '') + '>' + (i === 0 ? 'Today' : i === 1 ? 'Tomorrow' : f.long) + '</option>';
  }
  return out;
}

function readSearch() {
  ['from', 'to', 'date', 'after', 'busNo', 'sort'].forEach(function (k) {
    var el = document.getElementById('q-' + k);
    if (el) App.q[k] = el.value;
  });
}

/* ---------- Home ---------- */
App.pages.home = function () {
  var res = App.call('home');
  if (!res.ok) return;
  var d = res.data, hour = new Date().getHours(), stops = App.call('stops').data;
  var hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  App.render(
    '<header class="page-head"><h1>' + hello + ', ' + esc(d.me.firstName) + '</h1><p class="lead">Where do you need to go?</p></header>' +
    '<form class="search-strip" onsubmit="readSearch(); App.go(\'search\'); return false;">' +
      '<label>From<select id="q-from">' + stopOptions(stops, App.q.from, 'Any stop') + '</select></label>' +
      '<label>To<select id="q-to">' + stopOptions(stops, App.q.to, 'Any stop') + '</select></label>' +
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
  if (!stops.ok) return;
  var times = ['', '07:00', '08:00', '09:00', '12:00', '16:00'];
  App.render('<header class="page-head"><h1>Find a bus</h1><p class="lead" id="count"></p></header>' +
    '<form class="filters" onchange="readSearch(); showResults();" onsubmit="return false;">' +
      '<label>From<select id="q-from">' + stopOptions(stops.data, App.q.from, 'Any stop') + '</select></label>' +
      '<button type="button" class="swap" title="Swap From and To (return trip)" onclick="swapStops()">⇄</button>' +
      '<label>To<select id="q-to">' + stopOptions(stops.data, App.q.to, 'Any stop') + '</select></label>' +
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

/* Return trip in one click: home -> campus becomes campus -> home. */
function swapStops() {
  readSearch();
  var from = App.q.from;
  App.q.from = App.q.to;
  App.q.to = from;
  App.pages.search();
}

/* Every stop has a fixed daily time; this is the bus's time at one stop ('' = its first stop / last stop). */
function timeAt(b, stop, last) { return stopAt(b, stop, last).time; }

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

function showResults() {
  var res = App.call('search', App.q.from, App.q.to, App.q.date, App.q.after, App.q.busNo.trim());
  if (!res.ok) return;
  var buses = res.data, now = new Date(), today = App.q.date === isoDate(0);
  var nowHm = pad2(now.getHours()) + ':' + pad2(now.getMinutes());
  buses.sort(function (a, b) {
    var ta = timeAt(a, App.q.from), tb = timeAt(b, App.q.from);
    return App.q.sort === 'free' ? b.available - a.available : App.q.sort === 'quick' ? a.duration - b.duration : ta < tb ? -1 : ta > tb ? 1 : 0;
  });
  document.getElementById('count').textContent = buses.length + (buses.length === 1 ? ' trip' : ' trips') + ' for ' + fmtDate(App.q.date).long;
  document.getElementById('results').innerHTML = buses.length ? buses.map(function (b) {
    var on = stopAt(b, App.q.from), off2 = stopAt(b, App.q.to, true), board = on.time, alight = off2.time, where = App.q.from || b.source;
    var approx = on.kind !== 'published' || off2.kind !== 'published' ? '≈ ' : '';
    var gone = today && board <= nowHm, off = b.status !== 'ACTIVE', full = b.available === 0;
    var action = off ? '<span class="tag amber">Not running</span>' : gone ? '<span class="tag">Left ' + esc(where) + ' at ' + board + '</span>' :
      full ? '<span class="tag red">Full</span>' : '<button class="btn" onclick="App.go(\'seats\', {id: ' + b.id + ', dir: \'' + b.direction + '\', date: \'' + App.q.date + '\'})">View seats</button>';
    return '<article class="bus-row' + (gone || off ? ' dim' : '') + '"><div class="dep' + (on.kind !== 'published' ? ' est' : '') + '">' + (on.kind === 'published' ? '' : '<i class="approx">≈</i>') + on.time + '<small>at ' + esc(where) + '</small></div>' +
      '<div class="info"><span class="busno">' + b.busNo + '</span> <b>' + esc(b.route) + '</b>' + tripTag(b) +
      '<small>via ' + esc(b.stops.slice(1, -1).join(', ')) + '</small></div>' +
      '<div class="mini"><span>' + App.when(on) + '</span><i></i><span>' + App.when(off2) + '</span><small>' + approx + minutesBetween(board, alight) + ' min to ' +
        esc(App.q.to || b.destination) + '</small></div>' +
      '<div class="free"><b>' + b.available + '</b> of ' + b.capacity + ' free<div class="bar"><i style="width:' + b.occupancy + '%"></i></div></div>' +
      '<div class="act">' + action + '</div></article>';
  }).join('') : App.empty('No buses match', 'Try another stop, an earlier time or a different date.',
    '<button class="btn" onclick="App.q = {from:\'\', to:\'\', date: isoDate(0), after:\'\', busNo:\'\', sort:\'time\'}; App.go(\'search\')">Clear filters</button>');
}

/* ---------- Seat map ---------- */
App.pages.seats = function (p) {
  var res = App.call('bus', p.id, p.date, p.dir || 'OUT');
  if (!res.ok) return res.db || App.render(App.empty('Bus not found', res.error));
  var b = App.bus = res.data;
  App.seat = 0;
  App.stop = App.stop && b.stops.some(function (s) { return s.name === App.stop; }) ? App.stop : '';
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
    '<section class="strip">' + App.routeLine(b.stops, { labels: true, width: App.fit(1000), mark: onRoute(b, App.q.from) }) +
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
    (b.passengers.length ? '<ol class="manifest">' + b.passengers.map(function (p) {
      return '<li><b>' + pad2(p.seat) + '</b>' + esc(p.name) + '<small>' + esc(p.regNo) + (p.stop ? ' · ' + esc(p.stop) : '') + '</small></li>'; }).join('') + '</ol>'
      : '<p class="muted">Nobody has booked this trip yet.</p>');
  else if (b.mine) html = '<h2>You have seat ' + pad2(b.mine) + '</h2><p>One seat per student each way, each day.</p>' +
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
  document.getElementById('summary').innerHTML = (error ? '<p class="error">' + esc(error) + '</p>' : '') + html;
}

/* Where the rider gets on (to campus) or off (going back): every stop except the campus. */
function stopPicker(b) {
  var back = b.direction === 'RETURN', list = b.stops.slice(back ? 1 : 0, back ? b.stops.length : b.stops.length - 1);
  var chosen = App.stop || onRoute(b, back ? App.q.to : App.q.from);
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
  setTimeout(function () {                     // let the button repaint before the database call
    var res = App.call('book', App.bus.id, App.bus.date, App.bus.direction, App.seat, stop);
    App.stop = '';
    if (res.ok) return App.go('pass', { id: res.data.id, fresh: true });
    var keep = { id: App.bus.id, date: App.bus.date, dir: App.bus.direction };
    App.pages.seats(keep);                     // reload: someone else may have taken seats meanwhile
    summary(res.error);
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
    '<div class="pass-actions"><button class="btn primary" onclick="var m = app.print(); if (m) alert(m);">Print pass</button>' +
    '<button class="btn" onclick="App.go(\'trips\')">My reservations</button></div>');
};

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
  if (!res.ok) return;
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
    '</b><small>' + f.month + '</small></div><div class="info"><span class="busno">' + b.busNo + '</span> <b>' + esc(b.route) + '</b>' + tripTag(b) +
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
  var res = App.call('cancel', id);
  if (!res.ok) return alert(res.error);
  App.tab = 'CANCELLED';
  App.pages.trips();
}
