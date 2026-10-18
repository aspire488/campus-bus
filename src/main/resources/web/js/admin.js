/* Admin overview: live numbers, today's departure board and bookings per day. Data: CollegeBusSystem.displayDetails(). */
App.adminMenu = { overview: 'Overview', seats: 'Schedules' };

App.pages.overview = function () {
  var res = App.call('overview');
  if (!res.ok) return App.problem(res);
  var d = res.data, now = new Date(), hm = pad2(now.getHours()) + ':' + pad2(now.getMinutes());
  var figures = [['Buses running', d.activeBuses, 'of ' + d.totalBuses], ['Seats booked today', d.bookedToday, ''],
    ['Seats open today', d.freeToday, ''], ['Occupancy today', d.occupancy, '%'], ['Cancelled today', d.cancelledToday, ''], ['Students', d.students, '']];
  App.render('<header class="page-head"><h1>' + fmtDate(d.date).long + '</h1><p class="lead">' + d.totalBuses + ' buses on ' + d.routes + ' routes. Live from the reservations database.</p></header>' +
    '<section class="figures">' + figures.map(function (f) {
      return '<div><b data-count="' + f[1] + '">0</b><span>' + f[2] + '</span><small>' + f[0] + '</small></div>';
    }).join('') + '</section>' +
    '<div class="ops-grid"><section class="board"><h2>Departures today</h2>' + d.board.map(function (b) {
      var state = b.status !== 'ACTIVE' ? ['Maintenance', 'amber'] : b.arrival <= hm ? ['Arrived', 'done'] :
        b.departure <= hm ? ['On the road', 'go'] : ['Scheduled', ''];
      return '<div class="board-row ' + state[1] + '"><span class="t">' + b.departure + '</span><span class="n">' + b.busNo + '</span>' +
        '<span class="r">' + esc(b.route) + '</span><span class="o"><i style="width:' + b.occupancy + '%"></i></span>' +
        '<span class="c">' + b.booked + '/' + b.capacity + '</span><span class="s">' + state[0] + '</span></div>';
    }).join('') + '</section>' +
    '<section class="chart"><h2>Seats booked by travel date</h2><p class="muted">Past week and the week ahead. Today is in red.</p>' +
    dayChart(d.days, d.date) + '<ul class="legend"><li><i class="key booked"></i>Booked</li><li><i class="key cancelled"></i>Cancelled</li></ul></section></div>');
  App.countUp();
};

function dayChart(days, today) {
  var max = 1, w = 18, h = 150, svg = '';
  days.forEach(function (d) { max = Math.max(max, d.booked + d.cancelled); });
  days.forEach(function (d, i) {
    var x = i * (w + 6), hb = d.booked / max * h, hc = d.cancelled / max * h, f = fmtDate(d.date);
    svg += '<rect class="' + (d.date === today ? 'today' : 'bk') + '" x="' + x + '" y="' + (h - hb) + '" width="' + w + '" height="' + hb + '"/>' +
      '<rect class="cn" x="' + x + '" y="' + (h - hb - hc) + '" width="' + w + '" height="' + hc + '"/>' +
      '<text x="' + (x + w / 2) + '" y="' + (h - hb - hc - 6) + '" text-anchor="middle" class="v">' + d.booked + '</text>' +
      '<text x="' + (x + w / 2) + '" y="' + (h + 16) + '" text-anchor="middle">' + f.date + '</text>' +
      '<text x="' + (x + w / 2) + '" y="' + (h + 30) + '" text-anchor="middle" class="d">' + f.day.charAt(0) + '</text>';
  });
  return days.length ? '<svg class="days" viewBox="0 -14 ' + days.length * (w + 6) + ' ' + (h + 50) + '" width="100%">' + svg + '</svg>'
    : App.empty('No bookings yet', 'Bookings for the last and next 7 days appear here.');
}
