/* ============================================================
   Comp Off module - client side behaviour
   - Client-side validation (mirrors the server-side checks in CompOffAction / DAOs)
   - Date picker (dd Mon yyyy display, e.g. 01 Oct 2026; yyyy-mm-dd posted)
   - Modals: confirm, cancel reason, history; loader; notice
   Page data comes from data-* attributes rendered by the JSPs.
   ============================================================ */
(function () {
  'use strict';

  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  var BODY = document.body;
  // page data written by layout/menu.jsp (the login page keeps it on <body>)
  var DATA = document.getElementById('co-data') || BODY;
  var TODAY = DATA.getAttribute('data-today');
  var MAXLEN = parseInt(DATA.getAttribute('data-maxlength'), 10) || 500;

  // A page restored by Back / Forward from the browser's back-forward cache is reloaded, so the server checks the
  // login again (pages are also sent with Cache-Control: no-store - see NoCacheFilter). Server checks stay authoritative.
  window.addEventListener('pageshow', function (e) { if (e.persisted) window.location.reload(); });

  function fd(iso) { if (!iso) return ''; var p = iso.split('-'); return p[2] + ' ' + MON[+p[1] - 1] + ' ' + p[0]; }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function list(el, attr) { var v = el.getAttribute(attr); return v ? v.split(',').filter(Boolean) : []; }
  function plural(n, word) { return n + ' ' + word + (n === 1 ? '' : 's'); }

  /* ---------------- Field errors ---------------- */
  function setErr(root, name, msg) {
    var f = $('#f_' + name, root);
    if (!f) return;
    f.classList.toggle('bad', !!msg);
    var e = $('.err', f);
    if (e) e.textContent = msg || '';
  }
  // Editing a field clears its own error (errors are shown on submit only)
  ['input', 'change'].forEach(function (ev) {
    document.addEventListener(ev, function (e) {
      var f = e.target.closest && e.target.closest('.f.bad');
      if (f) { f.classList.remove('bad'); var x = $('.err', f); if (x) x.textContent = ''; }
    }, true);
  });

  /* ---------------- Character counters ---------------- */
  function count(t) { var c = t.parentElement.querySelector('.cnt'); if (c) c.textContent = t.value.length + ' / ' + (t.maxLength > 0 ? t.maxLength : MAXLEN); }
  document.addEventListener('input', function (e) { if (e.target.matches && e.target.matches('textarea[data-count]')) count(e.target); });
  $$('textarea[data-count]').forEach(count);

  /* ---------------- Overlays ---------------- */
  function overlay(id) {
    var o = document.getElementById(id);
    if (!o) { o = document.createElement('div'); o.id = id; o.className = 'overlay'; BODY.appendChild(o); }
    return o;
  }
  function loading(html) {
    var o = overlay('co-loader');
    o.innerHTML = '<div class="lbox"><div class="spin"></div><p>' + html + '</p></div>';
    o.classList.add('on');
  }
  function notice(msg, type) {
    var o = overlay('co-notice'), ok = type !== 'er';
    o.innerHTML = '<div class="nbox ' + (ok ? 'ok' : 'er') + '"><div class="nic">' + (ok ? '&#10003;' : '!') + '</div><p>' + esc(msg)
      + '</p><button type="button" class="p" data-act="close-notice">OK</button></div>';
    o.classList.add('on');
    $('button', o).focus();
  }
  var modalEl = $('#co-modal'), modalBox = $('#co-modal-box');
  function modal(html) {
    modalBox.innerHTML = '<button type="button" class="x" title="Close" data-act="close-modal">&times;</button>' + html;
    modalEl.classList.add('on');
  }
  function closeModal() { if (modalEl) modalEl.classList.remove('on'); }
  /** Buttons of a popup: the dismiss button ("No", "Close") on the left, the action ("Yes", "Approve") on the right. */
  function modalBtns(noLabel, yesHtml, noAct) {
    return '<div class="btns mbtns"><button type="button" data-act="' + (noAct || 'close-modal') + '">' + noLabel + '</button>' + yesHtml + '</div>';
  }

  /* ---------------- Date picker ---------------- */
  var CAL = '<img src="' + (DATA.getAttribute('data-ctx') || '') + '/asserts/svg/calendar.svg" width="17" height="17" alt="">';
  var pop = document.createElement('div'); pop.className = 'dpop'; BODY.appendChild(pop);
  var dp = { input: null, y: 0, m: 0 };
  /** Dates of a multi-date box (hidden value 'yyyy-MM-dd,yyyy-MM-dd'), sorted, without repeats. */
  function isoList(v) {
    return (v || '').split(',').map(function (x) { return x.trim(); }).filter(function (x, i, a) { return x && a.indexOf(x) === i; }).sort();
  }
  /** Text shown in a date box: one date, or "3 dates: 20 Oct 2026, 22 Oct 2026, 25 Oct 2026". */
  function dpText(hidden) {
    if (!hidden.closest('.dpw').classList.contains('multi')) return fd(hidden.value);
    var l = isoList(hidden.value);
    return l.length > 1 ? l.length + ' dates: ' + l.map(fd).join(', ') : l.length ? fd(l[0]) : '';
  }
  $$('.dpw').forEach(function (w) {
    var hidden = $('input[type=hidden]', w), text = $('.dpt', w);
    // an empty date box stays empty (its placeholder shows); a date is set only when the user picks one
    text.value = dpText(hidden); text.title = text.value;
    var icon = document.createElement('span'); icon.className = 'dpi'; icon.innerHTML = CAL; w.appendChild(icon);
    var open = function () { dpOpen(hidden, text); };
    text.addEventListener('click', open);
    text.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
    icon.addEventListener('click', open);
  });
  function dpOpen(hidden, text) {
    var multi = hidden.closest('.dpw').classList.contains('multi');
    var p = ((multi ? isoList(hidden.value)[0] : hidden.value) || TODAY).split('-');
    // dp.draft = the selected day(s); every pick is written to the box at once, so closing the picker
    // (Apply, Escape or a click outside) always keeps what is selected
    dp = { input: hidden, text: text, y: +p[0], m: +p[1] - 1, multi: multi, draft: multi ? isoList(hidden.value) : (hidden.value ? [hidden.value] : []) };
    dpDraw();
    var r = text.getBoundingClientRect();
    pop.style.display = 'block';
    pop.style.left = Math.max(8, Math.min(r.left + window.scrollX, window.innerWidth - 282)) + 'px';
    pop.style.top = (r.bottom + window.scrollY + 4) + 'px';
  }
  function dpDraw() {
    var y = dp.y, m = dp.m, sel = dp.draft, min = dp.input.getAttribute('data-min') || '';
    var first = new Date(Date.UTC(y, m, 1)).getUTCDay(), n = new Date(Date.UTC(y, m + 1, 0)).getUTCDate(), c = '';
    for (var i = 0; i < first; i++) c += '<span></span>';
    for (var d = 1; d <= n; d++) {
      var iso = y + '-' + String(m + 1).padStart(2, '0') + '-' + String(d).padStart(2, '0');
      var off = min && iso < min;   // e.g. days of a past month on Apply Comp Off
      c += '<button type="button" class="dd' + (sel.indexOf(iso) >= 0 ? ' sel' : '') + (iso === TODAY ? ' td' : '') + '" data-dp="pick" data-v="' + iso + '"'
        + (off ? ' disabled title="Not available"' : '') + '>' + d + '</button>';
    }
    pop.innerHTML = '<div class="dph"><span><button type="button" title="Previous year" data-dp="y" data-v="-1">&laquo;</button>'
      + '<button type="button" title="Previous month" data-dp="m" data-v="-1">&lsaquo;</button></span><b>' + MON[m] + ' ' + y + '</b>'
      + '<span><button type="button" title="Next month" data-dp="m" data-v="1">&rsaquo;</button>'
      + '<button type="button" title="Next year" data-dp="y" data-v="1">&raquo;</button></span></div>'
      + '<div class="dpg">' + ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map(function (x) { return '<i>' + x + '</i>'; }).join('') + c + '</div>'
      + '<div class="dpf"><button type="button" class="dpclear" data-dp="clear">Clear</button>'
      + (TODAY ? '<button type="button" class="dptoday" data-dp="today" title="Go to today">Today</button>' : '')
      + '<button type="button" class="dpapply" data-dp="apply">Apply</button></div>';
  }
  function dpClose() { pop.style.display = 'none'; }
  pop.addEventListener('click', function (e) {
    e.stopPropagation();
    var b = e.target.closest('button[data-dp]'); if (!b) return;
    var k = b.getAttribute('data-dp'), v = b.getAttribute('data-v');
    if (k === 'm') { dp.m += +v; if (dp.m < 0) { dp.m = 11; dp.y--; } if (dp.m > 11) { dp.m = 0; dp.y++; } dpDraw(); }
    else if (k === 'y') { dp.y += +v; dpDraw(); }
    else if (k === 'today') {
      // like the Windows calendar: show today's month (today is highlighted) without selecting it
      var t = TODAY.split('-'); dp.y = +t[0]; dp.m = +t[1] - 1; dpDraw();
    }
    else if (k === 'pick') {
      // multi-date box: a day toggles in / out of the draft; single-date box: the day becomes the draft
      var i = dp.draft.indexOf(v);
      if (dp.multi) { if (i >= 0) dp.draft.splice(i, 1); else dp.draft.push(v); dp.draft = isoList(dp.draft.join(',')); }
      else dp.draft = [v];
      dpCommit(false);
      dpDraw();
    }
    else if (k === 'clear') { dp.draft = []; dpCommit(false); dpDraw(); }
    else if (k === 'apply') { dpCommit(true); }
  });
  /** Writes the selection to the date box (and closes the picker when close is true). */
  function dpCommit(close) {
    var v = dp.draft.join(','), changed = v !== dp.input.value;
    dp.input.value = v; dp.text.value = dpText(dp.input); dp.text.title = dp.text.value;
    if (close) dpClose();
    if (changed) dp.input.dispatchEvent(new Event('change', { bubbles: true }));
  }
  document.addEventListener('click', function (e) { if (!e.target.closest('.dpw')) dpClose(); });
  function setDate(id, iso) { var h = document.getElementById(id), t = h.closest('.dpw').querySelector('.dpt'); h.value = iso; t.value = dpText(h); t.title = t.value; }

  /* ---------------- Submitting a validated form ---------------- */
  function send(form, loaderHtml) {
    $$('button', form).forEach(function (b) { b.disabled = true; });
    loading(loaderHtml);
    form.submit();
  }

  /* ---------------- Holiday Intimation ---------------- */
  var hi = $('#hiForm');
  if (hi) {
    hi.addEventListener('submit', function (e) {
      e.preventDefault();
      var d = hi.workedDate.value, s = hi.shiftType.value, r = hi.reason.value.trim();
      var ed = '', es = '', er = '';
      if (!d) ed = 'Worked date is required.';
      else if (d > TODAY) ed = 'Worked date cannot be a future date.';
      else if (list(hi, 'data-taken').indexOf(d) >= 0) ed = 'An intimation already exists for this date.';
      if (!s) es = 'Shift type is required.';
      if (!r) er = 'Notes are required.';
      else if (r.length > MAXLEN) er = 'Notes can be at most ' + MAXLEN + ' characters.';
      setErr(hi, 'workedDate', ed); setErr(hi, 'shiftType', es); setErr(hi, 'reason', er);
      if (ed || es || er) return;
      if (!hi.getAttribute('data-approver')) { notice('No Holiday Intimation approver is configured for you. Please contact the admin.', 'er'); return; }
      send(hi, 'Your Holiday Intimation request is being sent to <b>' + esc(hi.getAttribute('data-approver')) + '</b>. Please wait&hellip;');
    });
    hi.addEventListener('reset', function () {
      setTimeout(function () {
        setDate('workedDate', '');
        ['workedDate', 'shiftType', 'reason'].forEach(function (n) { setErr(hi, n, ''); });
        $$('textarea[data-count]', hi).forEach(count);
      }, 0);
    });
  }

  /* ---------------- Apply Comp Off ---------------- */
  // Rules (same as the server): at least one Compensatory Off date, none in a past month (any day of the current
  // month or later is fine), at most data-maxdays dates, no worked date of an approved Holiday Intimation, no date
  // already applied (Pending / Approved / Completed), and one Full day (1.0) of balance per date.
  // Each date becomes its own request and uses the oldest Available intimation (FIFO).
  var co = $('#coForm'), coSubmitted = false;
  function days1(n) { return String(Math.round(n * 10) / 10) + ' day' + (n === 1 ? '' : 's'); }
  /** Days that can still be applied: the balance, never more than the Available intimations behind it. */
  function coAvailable() {
    var bal = parseFloat(co.getAttribute('data-balance')) || 0, av = list(co, 'data-available');
    return { days: Math.min(bal, av.length), dates: av };
  }
  function coCalc() {
    var days = isoList(co.leaveDates.value);
    return { days: days, n: days.length, maxDays: parseInt(co.getAttribute('data-maxdays'), 10) || 31, av: coAvailable() };
  }
  /** First selected date that is the worked date of an approved Holiday Intimation (not allowed as Comp Off). */
  function workedDay(days) {
    var worked = list(co, 'data-worked');
    return days.filter(function (d) { return worked.indexOf(d) >= 0; })[0];
  }
  /** Error for the selected dates, '' when they can be applied. Each date is checked on its own. */
  function coErr(c) {
    var min = co.leaveDates.getAttribute('data-min') || '';
    if (!c.n) return 'Compensatory Off date is required.';
    var past = c.days.filter(function (d) { return min && d < min; })[0];
    if (past) return 'Comp Off cannot be applied for a past month (' + fd(past) + '). Choose dates from ' + fd(min) + ' onwards.';
    if (c.n > c.maxDays) return 'You can apply for at most ' + c.maxDays + ' days at a time.';
    if (workedDay(c.days)) return 'You worked on ' + fd(workedDay(c.days)) + ' (Holiday Intimation), so Comp Off cannot be applied for this date.';
    var applied = list(co, 'data-applied');
    var dup = c.days.filter(function (d) { return applied.indexOf(d) >= 0; })[0];
    if (dup) return 'Comp Off is already applied for ' + fd(dup) + '.';
    return c.n > c.av.days ? 'You have selected (' + plural(c.n, 'day') + '), but your available Comp Off balance is only (' + days1(c.av.days) + ').' : '';
  }
  function coUpdate() {
    var c = coCalc(), hint = $('#fifo'), min = co.leaveDates.getAttribute('data-min') || '';
    $('#dur').textContent = c.n ? plural(c.n, 'day') : '\u2014';
    if (min && c.days.some(function (d) { return d < min; })) {
      hint.textContent = 'Comp Off can be applied for dates from ' + fd(min) + ' onwards.';
    } else if (!c.n) {
      hint.textContent = 'Select one or more Compensatory Off dates to see which Holiday Intimation is used.';
    } else if (c.n > c.av.days) {
      hint.textContent = 'You have ' + days1(c.av.days) + ' of Comp Off available, but you have selected ' + plural(c.n, 'day')
        + '. Please select dates within your available Comp Off balance.';
    } else if (c.n > c.maxDays) {
      hint.textContent = 'You can apply for at most ' + c.maxDays + ' days at a time.';
    } else {
      var left = days1(c.av.days - c.n);
      if (c.n > 1) {
        hint.innerHTML = c.n + ' dates = ' + c.n + ' separate requests (one per date). FIFO uses: '
          + c.days.map(function (d, i) { return fd(d) + ' \u2190 ' + fd(c.av.dates[i]); }).join(', ') + '. Balance after these requests: ' + left + '.';
      } else {
        hint.innerHTML = 'FIFO: this request uses the Holiday Intimation of <b>' + fd(c.av.dates[0]) + '</b>. Balance after this request: ' + left + '.';
      }
    }
    // after a submit attempt keep the date message in line with what is selected now
    if (coSubmitted) setErr(co, 'leaveDates', coErr(c));
  }
  if (co) {
    // the server shows a date error after a failed submit: keep it in sync with later changes
    coSubmitted = !!$('#f_leaveDates.bad', co);
    co.addEventListener('change', function (e) { if (e.target.name === 'leaveDates') coUpdate(); });
    coUpdate();
    co.addEventListener('submit', function (ev) {
      ev.preventDefault();
      coSubmitted = true;
      var el = coErr(coCalc()), nt = co.notes.value.trim();
      var ed = co.dayType.value ? '' : 'Day type is required.';
      var es = co.shiftType.value ? '' : 'Shift type is required.';
      var en = !nt ? 'Notes are required.' : (nt.length > MAXLEN ? 'Notes can be at most ' + MAXLEN + ' characters.' : '');
      setErr(co, 'dayType', ed); setErr(co, 'shiftType', es); setErr(co, 'leaveDates', el); setErr(co, 'notes', en);
      if (ed || es || el || en) return;
      if (!co.getAttribute('data-approver')) { notice('No Comp Off approver is configured for you. Please contact the admin.', 'er'); return; }
      send(co, 'Your Comp Off request is being sent to <b>' + esc(co.getAttribute('data-approver')) + '</b>. Please wait&hellip;');
    });
    co.addEventListener('reset', function () {
      setTimeout(function () {
        coSubmitted = false;
        setDate('leaveDates', '');
        ['dayType', 'shiftType', 'leaveDates', 'notes'].forEach(function (n) { setErr(co, n, ''); });
        $$('textarea[data-count]', co).forEach(count);
        coUpdate();
      }, 0);
    });
  }

  /* ---------------- Cancel flows (confirm -> reason -> POST) ---------------- */
  var CANCEL = {
    hi: {
      confirm: function (d) { return '<h3>Cancel Holiday Intimation</h3><p>Are you sure you want to cancel your Holiday Intimation request for <b>' + esc(d.date) + '</b>?</p>'; },
      reason: function (d) {
        return '<h3>Cancel intimation of ' + esc(d.date) + '</h3><p class="mut">'
          + (d.status === 'Available' ? 'The Comp Off credited for this date will be removed from your balance.' : 'The pending approval will be withdrawn.') + '</p>';
      },
      loading: 'Cancelling your Holiday Intimation. Please wait&hellip;'
    },
    coa: {   // Admin: Comp Off Cancel
      confirm: function (d) { return '<h3>Cancel Comp Off</h3><p>Are you sure you want to cancel the Comp Off of <b>' + esc(d.staff) + '</b> for <b>' + esc(d.date) + '</b>?</p>'; },
      reason: function (d) { return '<h3>Cancel Comp Off &ndash; ' + esc(d.staff) + '</h3><p class="mut">Comp Off for ' + esc(d.date) + '. The Holiday Intimation becomes Approved again and the balance is restored.</p>'; },
      loading: 'Cancelling the Comp Off. Please wait&hellip;'
    },
    co: {
      confirm: function (d) { return '<h3>Cancel Comp Off</h3><p>Are you sure you want to cancel the Comp Off for <b>' + esc(d.date) + '</b>?</p>'; },
      reason: function (d) { return '<h3>Cancel Comp Off leave</h3><p class="mut">Request for ' + esc(d.date) + '. The balance will be restored.</p>'; },
      loading: 'Cancelling your Comp Off request. Please wait&hellip;'
    }
  };
  var pending = null;
  function cancelConfirm(kind, d) {
    pending = { kind: kind, d: d };
    modal(CANCEL[kind].confirm(d) + modalBtns('No', '<button type="button" class="d" data-act="cancel-reason">Yes</button>'));
  }
  function cancelReason() {
    var k = pending.kind, d = pending.d;
    modal(CANCEL[k].reason(d)
      + '<div class="f" id="f_cancelReason"><label for="cancelReasonInput">Cancel Notes <span class="req">*</span></label>'
      + '<textarea id="cancelReasonInput" maxlength="' + MAXLEN + '" data-count autocomplete="off"></textarea><div class="cntline"><div class="err"></div><div class="cnt">0 / ' + MAXLEN + '</div></div></div>'
      + modalBtns('Close', '<button type="button" class="' + (k === 'hi' ? 'd' : 'p') + '" data-act="cancel-submit">Confirm</button>'));
    $('#cancelReasonInput').focus();
  }
  function cancelSubmit() {
    var rs = $('#cancelReasonInput').value.trim();
    var msg = !rs ? 'Cancel Notes are required.' : (rs.length > MAXLEN ? 'Cancel Notes can be at most ' + MAXLEN + ' characters.' : '');
    setErr(modalBox, 'cancelReason', msg);
    if (msg) return;
    var form = $('#cancelForm');
    form.querySelector('[name=cancelReason]').value = rs;
    form.querySelector('[data-id]').value = pending.d.id;
    closeModal();
    send(form, CANCEL[pending.kind].loading);
  }
  /* ---------------- Admin: Apply Comp Off for Staff ---------------- */
  var CODES = /^[A-Za-z0-9]{1,45}(,[A-Za-z0-9]{1,45})*$/;
  var findForm = $('#adminFindForm');
  if (findForm) findForm.addEventListener('submit', function (e) {
    var v = findForm.employeeCodes.value;
    var m = !v ? 'Employee codes are required.'
      : (CODES.test(v) ? '' : 'Enter employee codes separated by commas only: no spaces or other characters (e.g. EMP101,EMP103).');
    setErr(findForm, 'employeeCodes', m);
    if (m) e.preventDefault();
  });
  var adminCo = $('#adminCoForm');
  if (adminCo) adminCo.addEventListener('submit', function (e) {
    e.preventDefault();
    var dt = adminCo.compOffDate.value, nt = adminCo.notes.value.trim();
    var ed = dt ? '' : 'Compensatory Off Date is required.';
    var en = !nt ? 'Notes are required.' : (nt.length > MAXLEN ? 'Notes can be at most ' + MAXLEN + ' characters.' : '');
    setErr(adminCo, 'compOffDate', ed); setErr(adminCo, 'notes', en);
    if (ed || en) return;
    // one line per staff member: the intimation used and the balance before -> after
    var rows = $$('#pickTable tbody tr').map(function (tr) {
      var bal = parseFloat(tr.getAttribute('data-balance'));
      return '<tr><th>' + esc(tr.getAttribute('data-code')) + '</th><td>' + esc(tr.getAttribute('data-name')) + ' &middot; uses '
        + esc(tr.getAttribute('data-worked')) + ' &middot; balance ' + esc(String(bal)) + ' &rarr; ' + esc(String(Math.max(0, bal - 1))) + '</td></tr>';
    });
    modal('<h3>Apply Comp Off as Approved?</h3><p style="margin:4px 0 8px">' + esc($('#compOffDate_t').value) + ' for '
      + plural(rows.length, 'staff member') + ':</p><div class="scroll"><table class="kv" style="margin:0 0 10px">' + rows.join('') + '</table></div>'
      + '<p class="mut" style="margin:0 0 12px">Saved as Approved, approved by ' + esc(adminCo.getAttribute('data-admin')) + '. No approver is asked.</p>'
      + modalBtns('No', '<button type="button" class="p" data-act="admin-co-yes">Yes, apply as Approved</button>'));
  });
  /* ---------------- Approver pages: tick rows, then Approve / Reject ---------------- */
  var selAll = $('#selAll');
  function selected() { return $$('#approvalTable .sel').filter(function (c) { return c.checked; }).map(function (c) { return c.value; }); }
  // the floating bar (bottom of the screen) shows only while the bar above the table is scrolled out of view
  var topBar = $('.apphead [data-bulk]'), topBarSeen = true;
  if (topBar && window.IntersectionObserver) {
    new IntersectionObserver(function (en) { topBarSeen = en[0].isIntersecting; selSync(); }).observe(topBar);
  }
  function selSync() {
    var boxes = $$('#approvalTable .sel'), n = selected().length;
    if (selAll) { selAll.checked = boxes.length > 0 && n === boxes.length; selAll.indeterminate = n > 0 && n < boxes.length; }
    $$('[data-bulk]').forEach(function (bx) {
      var show = n > 0 && (!bx.classList.contains('floatbar') || !topBarSeen);
      bx.style.visibility = show ? 'visible' : 'hidden'; $('.bc', bx).textContent = n + ' selected';
    });
  }
  if (selAll) {
    selAll.addEventListener('change', function () { $$('#approvalTable .sel').forEach(function (c) { c.checked = selAll.checked; }); selSync(); });
    document.addEventListener('change', function (e) { if (e.target.classList && e.target.classList.contains('sel')) selSync(); });
    selSync();
  }
  // opened from an email (Approve / Reject): ?open=<encrypted id>&decision=approve|reject ticks that request and
  // opens the decision dialog; the approver still confirms here. The server shows the month the request was applied
  // in; a request no longer waiting shows the picker only, with a message.
  // (run after this script has finished, so the decision dialog's state is set up first)
  if (selAll || $('#periodForm')) {
    var qs = new URLSearchParams(window.location.search), openId = qs.get('open'), dec = qs.get('decision');
    if (openId) setTimeout(function () {
      var box = $$('#approvalTable .sel').filter(function (c) { return c.value === openId; })[0];
      // keep the month shown (a refresh stays on it); drop the one-time link parameters
      var pm = $('#month'), py = $('#year');
      if (window.history.replaceState) window.history.replaceState(null, '', window.location.pathname
        + (pm && pm.value && py && py.value ? '?month=' + pm.value + '&year=' + py.value : ''));
      if (!box) notice('This request is no longer waiting for your approval. It may have been decided, cancelled or moved to another approver.', 'er');
      else {
        box.checked = true; selSync();
        box.closest('tr').scrollIntoView({ block: 'center' });
        if (dec === 'approve' || dec === 'reject') decideOpen(dec);
      }
    }, 0);
  }
  // Approval pages: month and year (applied date) are both required before Go
  /* ---------------- My Comp Off Report ---------------- */
  // Month View: a day with Comp Off opens its Day View (click, or Enter / Space on the focused day)
  $$('#monthCal [data-href]').forEach(function (td) {
    var go = function () { window.location.href = td.getAttribute('data-href'); };
    td.addEventListener('click', go);
    td.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); } });
  });
  // My Team Reports: pointing at (or focusing) a marked day shows Leave date / Leave duration / Status in a small box
  var grid = $('#teamGrid');
  if (grid) {
    var tip = document.createElement('div'); tip.className = 'daytip'; tip.setAttribute('role', 'tooltip'); BODY.appendChild(tip);
    var tipShow = function (td) {
      tip.innerHTML = ''; tip.appendChild($('template.tipdata', td).content.cloneNode(true));
      tip.style.display = 'block';
      var r = td.getBoundingClientRect(), w = tip.offsetWidth, h = tip.offsetHeight;
      tip.style.left = Math.max(8, Math.min(r.left + r.width / 2 - w / 2, window.innerWidth - w - 8)) + 'px';
      tip.style.top = (r.bottom + 6 + h > window.innerHeight ? r.top - h - 6 : r.bottom + 6) + 'px';
    };
    var tipHide = function () { tip.style.display = 'none'; };
    $$('td.mk', grid).forEach(function (td) {
      td.addEventListener('mouseenter', function () { tipShow(td); });
      td.addEventListener('focus', function () { tipShow(td); });
      td.addEventListener('mouseleave', tipHide);
      td.addEventListener('blur', tipHide);
    });
    window.addEventListener('scroll', tipHide, true);
  }
  var teamForm = $('#teamForm');
  if (teamForm) teamForm.addEventListener('submit', function (e) {
    var es = teamForm.staffFilter.value ? '' : 'Select a staff member.', em = teamForm.month.value ? '' : 'Select a month.', ey = teamForm.year.value ? '' : 'Select a year.';
    setErr(teamForm, 'staffFilter', es); setErr(teamForm, 'month', em); setErr(teamForm, 'year', ey);
    if (es || em || ey) e.preventDefault();
  });
  var dayForm = $('#dayForm');
  if (dayForm) dayForm.addEventListener('submit', function (e) {
    var ed = dayForm.day.value ? '' : 'Select a date.';
    setErr(dayForm, 'day', ed);
    if (ed) e.preventDefault();
  });

  var pf = $('#periodForm');
  if (pf) pf.addEventListener('submit', function (e) {
    var em = pf.month.value ? '' : 'Select a month.', ey = pf.year.value ? '' : 'Select a year.';
    setErr(pf, 'month', em); setErr(pf, 'year', ey);
    if (em || ey) e.preventDefault();
  });
  // Admin, Comp Off Processing: confirm before processing (one request or all of the month)
  var procForm = $('#processForm');
  if (procForm) procForm.addEventListener('submit', function (e) {
    if (procForm.getAttribute('data-ok')) return;
    e.preventDefault();
    var all = procForm.querySelector('[name=mode]:checked').value === 'all', n = +procForm.getAttribute('data-count');
    modal('<h3>Process Comp Off</h3><p>' + (all ? 'Process all ' + plural(n, 'request') : 'Process the oldest request') + ' waiting for '
      + esc(procForm.getAttribute('data-period')) + '? Approvals become Approved; rejections become Rejected and the day goes back to the balance.</p>'
      + modalBtns('No', '<button type="button" class="p" data-act="process-yes">Yes, process</button>'));
  });
  // General Settings > Desktop App Settings: Comp Off teams drop-down (search, Select all / Clear, tags, saved value)
  var picker = $('#teamPicker');
  if (picker) (function () {
    var btn = $('.msbtn', picker), panel = $('.mspanel', picker), search = $('.mssearch', picker), field = $('#desktopTeamField');
    var labels = Array.prototype.slice.call(picker.querySelectorAll('.mslist label'));
    var chips = $('.chips', field), ids = $('.msids', field), info = $('.msinfo', picker), all = $('.msall', picker);
    var names = labels.map(function (l) { return l.querySelector('.tname').textContent; });
    var saved = (picker.getAttribute('data-order') || '').split(',').filter(Boolean);
    function boxes() { return labels.map(function (l) { return l.querySelector('input'); }); }
    function chosen() { return labels.filter(function (l) { return l.querySelector('input').checked; }); }
    function render() {
      var c = chosen(), shown = labels.filter(function (l) { return !l.hidden; });
      $('.mssum', picker).textContent = c.length ? plural(c.length, 'team') + ' selected' : 'Select teams';
      info.textContent = (search.value.trim() ? shown.length + ' of ' + plural(labels.length, 'team') : plural(labels.length, 'team')) + ' \u00b7 ' + c.length + ' selected';
      all.textContent = search.value.trim() ? 'Select all shown' : 'Select all';
      chips.innerHTML = c.map(function (l) {
        var n = l.getAttribute('data-name');
        return '<span class="chip2' + (l.querySelector('.inact') ? ' inact' : '') + '">' + esc(n) + ' <button type="button" title="Remove" aria-label="Remove '
          + esc(n) + '" data-team="' + esc(l.getAttribute('data-id')) + '">&times;</button></span>';
      }).join('');
      // the value saved: teams already saved keep their place (other companies' teams, not listed here, always stay),
      // newly ticked ones are added at the end
      var listed = labels.map(function (l) { return l.getAttribute('data-id'); });
      var order = saved.filter(function (id) { return listed.indexOf(id) < 0 || c.some(function (l) { return l.getAttribute('data-id') === id; }); });
      c.forEach(function (l) { if (order.indexOf(l.getAttribute('data-id')) < 0) order.push(l.getAttribute('data-id')); });
      ids.textContent = order.join(',') || '(none)';
      if (c.length) field.classList.remove('bad');
    }
    function filter() {
      var q = search.value.trim().toLowerCase(), any = false;
      labels.forEach(function (l, i) {
        var hit = !q || names[i].toLowerCase().indexOf(q) >= 0 || l.getAttribute('data-id').indexOf(q) >= 0;
        l.hidden = !hit; any = any || hit;
        var at = q ? names[i].toLowerCase().indexOf(q) : -1, span = l.querySelector('.tname');
        span.innerHTML = at < 0 ? esc(names[i]) : esc(names[i].slice(0, at)) + '<mark>' + esc(names[i].slice(at, at + q.length)) + '</mark>' + esc(names[i].slice(at + q.length));
      });
      $('.msempty', picker).hidden = any;
      render();
    }
    function open(on) {
      panel.hidden = !on; picker.classList.toggle('open', on); btn.setAttribute('aria-expanded', on ? 'true' : 'false');
      if (on) search.focus();
    }
    btn.addEventListener('click', function () { open(panel.hidden); });
    document.addEventListener('click', function (e) { if (!picker.contains(e.target)) open(false); });
    picker.addEventListener('keydown', function (e) { if (e.key === 'Escape') { open(false); btn.focus(); } });
    search.addEventListener('input', filter);
    search.addEventListener('keydown', function (e) { if (e.key === 'Enter') e.preventDefault(); });
    picker.addEventListener('change', render);
    all.addEventListener('click', function (e) {
      e.preventDefault();
      labels.forEach(function (l) { if (!l.hidden) l.querySelector('input').checked = true; });
      render();
    });
    $('.msnone', picker).addEventListener('click', function (e) {
      e.preventDefault();
      labels.forEach(function (l) { if (!l.hidden) l.querySelector('input').checked = false; });
      render();
    });
    chips.addEventListener('click', function (e) {
      var id = e.target.getAttribute && e.target.getAttribute('data-team');
      if (!id) return;
      boxes().forEach(function (b) { if (b.value === id) b.checked = false; });
      render();
    });
    $('#desktopForm').addEventListener('submit', function (e) {
      if (!chosen().length) { e.preventDefault(); field.classList.add('bad'); open(false); btn.focus(); }
    });
    filter();
  })();
  // Phones: list tables show each row as a card; each cell gets its column name (CSS table.rtable). Tables for selecting
  // rows (approval pages, Approver Management) and reports with column filters keep their columns and scroll sideways.
  function cardTables(root) {
    Array.prototype.forEach.call((root || document).querySelectorAll('#co-page table, #co-modal table'), function (t) {
      if (t.matches('.colfilter, .tgrid, .kv, #approvalTable, #amTable') || !t.tHead) return;
      var heads = Array.prototype.map.call(t.tHead.rows[t.tHead.rows.length - 1].cells, function (c) { return c.textContent.trim(); });
      Array.prototype.forEach.call(t.querySelectorAll('tbody tr, tfoot tr'), function (tr) {
        var i = 0;
        Array.prototype.forEach.call(tr.cells, function (td) {
          td.setAttribute('data-label', td.colSpan > 1 ? '' : heads[i] || '');
          i += td.colSpan || 1;
        });
      });
      t.classList.add('rtable');
    });
  }
  cardTables();
  // tables added later (history popups, paged rows) get their labels too
  if (window.MutationObserver) new MutationObserver(function (list) {
    for (var k = 0; k < list.length; k++) if (list[k].addedNodes.length) { cardTables(); break; }
  }).observe(document.body, { childList: true, subtree: true });

  // General Settings: Enable / Disable act as one switch (ticking one unticks the other; one is always ticked)
  document.addEventListener('change', function (e) {
    var t = e.target;
    if (!t.getAttribute || !t.getAttribute('data-onoff')) return;
    var other = t.closest('.onoff').querySelector('[data-onoff="' + (t.getAttribute('data-onoff') === 'on' ? 'off' : 'on') + '"]');
    other.checked = !t.checked;
  });
  var decidePending = null;
  function decideOpen(kind) {
    var ids = selected(), n = ids.length, approve = kind === 'approve';
    if (!n) return;
    var form = $('#decideForm'), noun = form.getAttribute('data-noun') || 'request';
    var maxIds = parseInt(form.getAttribute('data-max-ids'), 10) || 1000;
    if (n > maxIds) {
      notice(n + ' requests are selected. You can approve or reject at most ' + maxIds
        + ' requests at a time. Please select fewer and repeat for the rest.', 'er');
      return;
    }
    decidePending = { kind: kind, ids: ids };
    modal('<h3>' + (approve ? 'Approve' : 'Reject') + ' ' + plural(n, noun) + '</h3>'
      + '<div class="f" id="f_remarks"><label for="remarksInput">Notes' + (approve ? '' : ' <span class="req">*</span>') + '</label>'
      + '<textarea id="remarksInput" maxlength="' + MAXLEN + '" data-count autocomplete="off" placeholder="' + (approve ? 'Optional' : 'Mandatory when rejecting') + '"></textarea>'
      + '<div class="cntline"><div class="err"></div><div class="cnt">0 / ' + MAXLEN + '</div></div></div>'
      + modalBtns('Close', '<button type="button" class="' + (approve ? 'p' : 'd') + '" data-act="decide-submit">' + (approve ? 'Approve' : 'Reject') + '</button>'));
    $('#remarksInput').focus();
  }
  function decideSubmit() {
    var rm = $('#remarksInput').value.trim(), approve = decidePending.kind === 'approve';
    var msg = rm.length > MAXLEN ? 'Notes can be at most ' + MAXLEN + ' characters.' : (!approve && !rm ? 'Notes are mandatory when rejecting.' : '');
    setErr(modalBox, 'remarks', msg);
    if (msg) return;
    var form = $('#decideForm'), n = decidePending.ids.length;
    form.querySelector('[name=ids]').value = decidePending.ids.join(',');
    form.querySelector('[name=decision]').value = decidePending.kind;
    form.querySelector('[name=remarks]').value = rm;
    closeModal();
    send(form, (approve ? 'Approving ' : 'Rejecting ') + plural(n, 'request') + '. Please wait&hellip;');
  }

  function historyModal(id) {
    var tpl = document.getElementById('hist-' + id);
    if (tpl) modal(tpl.innerHTML);
  }

  /* ---------------- Click handling ---------------- */
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-act]');
    if (!b) return;
    var act = b.getAttribute('data-act');
    var d = { id: b.getAttribute('data-id'), date: b.getAttribute('data-date'), status: b.getAttribute('data-status'), staff: b.getAttribute('data-staff') };
    if (act === 'close-modal') closeModal();
    else if (act === 'close-notice') overlay('co-notice').classList.remove('on');
    else if (act === 'cancel-hi') cancelConfirm('hi', d);
    else if (act === 'cancel-co') cancelConfirm('co', d);
    else if (act === 'cancel-coa') cancelConfirm('coa', d);
    else if (act === 'admin-co-yes') { closeModal(); send($('#adminCoForm'), 'Saving the Comp Off as Approved. Please wait&hellip;'); }
    else if (act === 'cancel-reason') cancelReason();
    else if (act === 'cancel-submit') cancelSubmit();
    else if (act === 'history') { e.preventDefault(); historyModal(d.id); }
    else if (act === 'decide') decideOpen(b.getAttribute('data-decision'));
    else if (act === 'decide-submit') decideSubmit();
    else if (act === 'process-yes') { closeModal(); procForm.setAttribute('data-ok', '1'); send(procForm, 'Processing. Please wait&hellip;'); }
    else if (act === 'sel-clear') { $$('#approvalTable .sel').forEach(function (c) { c.checked = false; }); selSync(); }
    else return;
    if (b.tagName === 'A') e.preventDefault();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') { closeModal(); dpClose(); overlay('co-notice').classList.remove('on'); }
  });

  /* ---------------- Messages from the server ---------------- */
  var flash = DATA.getAttribute('data-flash');
  var serverErr = $('#co-server-errors');
  if (serverErr && serverErr.textContent.trim()) notice(serverErr.textContent.trim(), 'er');
  else if (flash) notice(flash, DATA.getAttribute('data-flash-type'));

  /* ---------------- Paged tables (data-page-size) ---------------- */
  // Shows data-page-size rows at a time with Previous / page / Next buttons under the table. Rows a filter takes out
  // carry data-out and are skipped; refreshPager(table, true) goes back to page 1 after the filter changes.
  function pagerRows(t) { return $$('tbody tr', t).filter(function (tr) { return !tr.classList.contains('nofilt') && !tr.hasAttribute('data-out'); }); }
  function refreshPager(t, first) {
    var size = parseInt(t.getAttribute('data-page-size'), 10) || 10, rows = pagerRows(t), pages = Math.max(1, Math.ceil(rows.length / size));
    var page = first ? 0 : Math.min(+(t.getAttribute('data-page') || 0), pages - 1);
    t.setAttribute('data-page', page);
    $$('tbody tr', t).forEach(function (tr) { if (!tr.classList.contains('nofilt')) tr.hidden = true; });
    rows.slice(page * size, page * size + size).forEach(function (tr) { tr.hidden = false; });
    var pg = t._pager;
    pg.hidden = rows.length <= size;
    if (pg.hidden) return;
    var from = page * size + 1, to = Math.min(rows.length, from + size - 1), b = '';
    b += '<button type="button" class="sm" data-pg="' + (page - 1) + '"' + (page === 0 ? ' disabled' : '') + '>&lsaquo; Previous</button>';
    for (var p = 0; p < pages; p++) {
      if (p === 0 || p === pages - 1 || Math.abs(p - page) <= 2) {
        b += '<button type="button" class="sm' + (p === page ? ' cur' : '') + '" data-pg="' + p + '"' + (p === page ? ' aria-current="page"' : '') + '>' + (p + 1) + '</button>';
      } else if (Math.abs(p - page) === 3) b += '<span>&hellip;</span>';
    }
    b += '<button type="button" class="sm" data-pg="' + (page + 1) + '"' + (page >= pages - 1 ? ' disabled' : '') + '>Next &rsaquo;</button>';
    pg.innerHTML = '<span>Showing ' + from + '&ndash;' + to + ' of ' + rows.length + '</span><div class="pg">' + b + '</div>';
  }
  $$('table[data-page-size]').forEach(function (t) {
    var pg = document.createElement('div'); pg.className = 'pager'; pg.hidden = true;
    (t.closest('.scroll') || t).insertAdjacentElement('afterend', pg);
    t._pager = pg;
    pg.addEventListener('click', function (e) {
      var b = e.target.closest('button[data-pg]'); if (!b || b.disabled) return;
      t.setAttribute('data-page', b.getAttribute('data-pg')); refreshPager(t, false);
    });
    refreshPager(t, true);
  });

  /* ---------------- My Comp Off Balance: a count filters the table ---------------- */
  // Each count's data-filter lists the statuses it counts; clicking the active count again, or Show all, resets.
  var balStats = $('#balStats');
  if (balStats) (function () {
    var btns = $$('.statn', balStats), rows = $$('#balTable tbody tr[data-status]'), none = $('#balTable tr.nofilt');
    var bar = $('#balFilter'), active = null;
    function apply(btn) {
      active = btn;
      var want = btn ? btn.getAttribute('data-filter').split(',') : null, shown = 0;
      rows.forEach(function (tr) {
        var ok = !want || want.indexOf(tr.getAttribute('data-status')) >= 0;
        if (ok) { tr.removeAttribute('data-out'); shown++; } else tr.setAttribute('data-out', '');
      });
      var bt = $('#balTable');
      if (bt && bt._pager) refreshPager(bt, true); else rows.forEach(function (tr) { tr.hidden = tr.hasAttribute('data-out'); });
      if (none) none.hidden = !want || shown > 0;
      btns.forEach(function (b) { b.setAttribute('aria-pressed', b === btn ? 'true' : 'false'); b.parentNode.classList.toggle('on', b === btn); });
      if (bar) {
        bar.hidden = !want;
        if (want) $('b', bar).textContent = btn.getAttribute('data-label') + ' (' + shown + ' record' + (shown === 1 ? '' : 's') + ')';
      }
    }
    btns.forEach(function (b) { b.addEventListener('click', function () { apply(active === b ? null : b); }); });
    var reset = $('#balReset');
    if (reset) reset.addEventListener('click', function () { apply(null); });
  })();

  /* ---------------- Admin reports: a filter box under each column (table.colfilter) ---------------- */
  // Each th has data-filter: text (part of the value), date (part of the date as shown), list (drop-down of the
  // values in the column) or num (4 = exactly 4, >2, <3, >=2, <=3, 2-4). A row is shown when it matches every filter.
  // Only filters the rows already on the page (the server query, Go and Export to Excel stay the same).
  function cellText(td) { return td ? td.textContent.replace(/\s+/g, ' ').trim() : ''; }
  function numTest(q) {
    var m = /^(<=|>=|<|>|=)?\s*(\d+(?:\.\d+)?)$/.exec(q), r = /^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)$/.exec(q);
    if (r) { var lo = Math.min(+r[1], +r[2]), hi = Math.max(+r[1], +r[2]); return function (v) { return v >= lo && v <= hi; }; }
    if (!m) return null;
    var n = +m[2], op = m[1] || '=';
    return function (v) { return op === '>' ? v > n : op === '<' ? v < n : op === '>=' ? v >= n : op === '<=' ? v <= n : v === n; };
  }
  $$('table.colfilter').forEach(function (t) {
    var heads = $$('thead tr:first-child th', t), rows = $$('tbody tr', t).filter(function (tr) { return !tr.classList.contains('nofilt'); });
    if (!rows.length || !heads.some(function (h) { return h.hasAttribute('data-filter'); })) return;
    var noun = t.getAttribute('data-noun') || 'records', boxes = [];
    var fr = document.createElement('tr'); fr.className = 'fltrow';
    heads.forEach(function (h, i) {
      var th = document.createElement('th'), kind = h.getAttribute('data-filter'), name = h.textContent.trim(), el = null;
      if (kind === 'list') {
        el = document.createElement('select');
        var all = document.createElement('option'); all.value = ''; all.textContent = 'All'; el.appendChild(all);
        var vals = [];
        rows.forEach(function (tr) { var v = cellText(tr.cells[i]); if (v && vals.indexOf(v) < 0) vals.push(v); });
        vals.sort(function (a, b) { return a.localeCompare(b); }).forEach(function (v) {
          var o = document.createElement('option'); o.value = v; o.textContent = v; el.appendChild(o);
        });
        el.addEventListener('change', apply);
      } else if (kind) {
        el = document.createElement('input'); el.type = 'text'; el.autocomplete = 'off'; el.maxLength = 60;
        el.placeholder = kind === 'num' ? '4  or  >2' : kind === 'date' ? '2021 or Mar' : name;
        el.addEventListener('input', apply);
        el.addEventListener('keydown', function (e) { if (e.key === 'Escape' && el.value) { el.value = ''; apply(); } });
      }
      if (el) { el.setAttribute('aria-label', 'Filter ' + name); boxes.push({ el: el, col: i, kind: kind, name: name }); th.appendChild(el); }
      fr.appendChild(th);
    });
    t.tHead.appendChild(fr);
    var bar = document.createElement('div'); bar.className = 'fltbar';
    var info = document.createElement('span'), clear = document.createElement('button');
    clear.type = 'button'; clear.className = 'fltclear'; clear.textContent = '\u2715 Clear filters';
    bar.appendChild(info); bar.appendChild(clear);
    (t.closest('.scroll') || t).insertAdjacentElement('beforebegin', bar);
    var none = document.createElement('div'); none.className = 'empty fltnone'; none.hidden = true; none.textContent = 'No matching records';
    (t.closest('.scroll') || t).insertAdjacentElement('afterend', none);
    clear.addEventListener('click', function () { boxes.forEach(function (b) { b.el.value = ''; }); apply(); });
    function apply() {
      var tests = [];
      boxes.forEach(function (b) {
        var q = b.el.value.replace(/\s+/g, ' ').trim();
        b.el.classList.toggle('on', q !== '');
        if (q === '') return;
        var nt = b.kind === 'num' ? numTest(q) : null, lq = q.toLowerCase();
        tests.push({ b: b, fn: b.kind === 'list' ? function (v) { return v === q; }
          : nt ? function (v) { return /^-?\d+(\.\d+)?$/.test(v) && nt(+v); }
          : function (v) { return v.toLowerCase().indexOf(lq) >= 0; } });
      });
      var shown = 0;
      rows.forEach(function (tr) {
        var ok = tests.every(function (x) { return x.fn(cellText(tr.cells[x.b.col])); });
        tr.hidden = !ok; if (ok) shown++;
      });
      none.hidden = shown > 0;
      info.innerHTML = 'Showing <b>' + shown + '</b> of <b>' + rows.length + '</b> ' + esc(noun)
        + (tests.length ? ' &middot; filtered by ' + esc(tests.map(function (x) { return x.b.name; }).join(', ')) : '');
      clear.disabled = !tests.length;
    }
    apply();
  });

  /* ---------------- Admin reports ---------------- */
  // Holiday View / Comp Off View (#reportForm) and Holiday Intimation Usage (#usageForm): staff list follows the team.
  var rf = $('#reportForm');
  if (rf || $('#usageForm')) {
    var team = $('#teamId'), staff = $('#staffFilter');
    var filterStaff = function () {
      $$('option', staff).forEach(function (o) {
        var show = !o.value || !team.value || o.getAttribute('data-team') === team.value;
        o.hidden = !show; o.disabled = !show;
        if (!show && o.selected) staff.value = '';
      });
    };
    team.addEventListener('change', filterStaff);
    filterStaff();
  }
  if (rf) {
    rf.addEventListener('submit', function (e) {
      var f = rf.fromDate.value, t = rf.toDate.value;
      var ef = f ? '' : 'From date is required.', et = !t ? 'To date is required.' : (f && t < f ? 'To date cannot be before From date.' : '');
      setErr(rf, 'fromDate', ef); setErr(rf, 'toDate', et);
      if (ef || et) e.preventDefault();
    });
  }
  var hwSel = $('#hwId');
  if (hwSel) hwSel.addEventListener('change', function () { hwSel.form.submit(); });

  /* ---------------- Approver Management (Admin) ---------------- */
  // One chain per staff member (up to data-max-levels levels), used for Holiday Intimation and Comp Off.
  // The table is paged here (25 rows); the selection is kept across pages. The server checks everything again.
  var am = $('#amCard');
  if (am) (function () {
    var MAXL = parseInt(am.getAttribute('data-max-levels'), 10) || 3, PAGE = 25;
    var AM_MAXLEN = parseInt(am.getAttribute('data-maxlength'), 10) || MAXLEN;
    var rows = $$('#amTable tbody tr'), byId = {}, sel = {}, page = 0, ed = null;
    var opts = $$('#amOptions option').map(function (o) { return { id: o.value, label: o.textContent }; });
    rows.forEach(function (tr) { byId[tr.getAttribute('data-id')] = tr; });

    function rd(tr) {
      return { id: tr.getAttribute('data-id'), code: tr.getAttribute('data-code'), name: tr.getAttribute('data-name'),
        team: tr.getAttribute('data-team'), userType: tr.getAttribute('data-usertype'), chain: list(tr, 'data-chain'),
        chainText: tr.getAttribute('data-chain-text') };
    }
    function label(id) { for (var i = 0; i < opts.length; i++) if (opts[i].id === String(id)) return opts[i].label; return '#' + id; }
    function shortName(id) { return label(id).replace(/ \(.*$/, ''); }
    function chips(names) {
      return names.map(function (n, i) { return '<span class="lv"><b>' + (i + 1) + '</b>' + esc(n) + '</span>'; }).join('');
    }
    function selIds() { return Object.keys(sel); }
    /** Opens the shared modal; wide for the history and assign-to-several popups. */
    function amModal(html, wide) { modal(html); $('#co-modal-box').classList.toggle('wide', !!wide); }
    function staffText(s) { return s.name + ' (' + s.code + ')'; }

    /* ---- paging and selection ---- */
    function pages() { return Math.max(1, Math.ceil(rows.length / PAGE)); }
    function onPage() { return rows.slice(page * PAGE, page * PAGE + PAGE); }
    function show() {
      rows.forEach(function (tr, i) { tr.hidden = Math.floor(i / PAGE) !== page; });
      rows.forEach(function (tr) { $('.amSel', tr).checked = !!sel[tr.getAttribute('data-id')]; });
      var vis = onPage(), n = vis.filter(function (tr) { return sel[tr.getAttribute('data-id')]; }).length, all = $('#amAll');
      if (all) { all.checked = vis.length > 0 && n === vis.length; all.indeterminate = n > 0 && n < vis.length; }
      var pg = $('#amPager');
      if (pg) {
        pg.hidden = rows.length <= PAGE;
        var from = page * PAGE + 1, to = Math.min(rows.length, from + PAGE - 1), b = '';
        b += '<button type="button" class="sm" data-act="am-page" data-page="' + (page - 1) + '"' + (page === 0 ? ' disabled' : '') + '>&lsaquo; Previous</button>';
        for (var p = 0; p < pages(); p++) {
          if (p === 0 || p === pages() - 1 || Math.abs(p - page) <= 2) {
            b += '<button type="button" class="sm' + (p === page ? ' cur' : '') + '" data-act="am-page" data-page="' + p + '">' + (p + 1) + '</button>';
          } else if (Math.abs(p - page) === 3) b += '<span>&hellip;</span>';
        }
        b += '<button type="button" class="sm" data-act="am-page" data-page="' + (page + 1) + '"' + (page >= pages() - 1 ? ' disabled' : '') + '>Next &rsaquo;</button>';
        pg.innerHTML = '<span>Showing ' + from + '&ndash;' + to + ' of ' + rows.length + '</span><div class="pg">' + b + '</div>';
      }
      var count = selIds().length, note = $('#amSelNote');
      if (rows.length > PAGE && count === rows.length) {
        note.innerHTML = 'All ' + count + ' staff that match the filter are selected. <a data-act="am-clear">Clear selection</a>'; note.hidden = false;
      } else if (rows.length > PAGE && vis.length && n === vis.length) {
        note.innerHTML = (count === vis.length ? 'All ' + n + ' staff on this page are selected. ' : count + ' staff selected. ')
          + '<a data-act="am-selall">Select all ' + rows.length + ' staff that match the filter</a>'; note.hidden = false;
      } else note.hidden = true;
      var bar = $('#amSelBar'), none = selIds().filter(function (id) { return !rd(byId[id]).chain.length; }).length;
      bar.hidden = count === 0;
      if (count) {
        bar.innerHTML = '<div><b>' + count + (count === 1 ? ' staff member' : ' staff') + ' selected</b> <span class="bc">&middot; ' + none
          + ' without approvers &middot; ' + (count - none) + ' already have approvers</span></div>'
          + '<div style="display:flex;gap:10px;align-items:center"><button type="button" class="lnk" data-act="am-clear">Clear</button>'
          + '<button type="button" class="p" data-act="am-bulk">Assign approvers to selected</button></div>';
      }
    }
    document.addEventListener('change', function (e) {
      if (e.target.classList && e.target.classList.contains('amSel')) {
        if (e.target.checked) sel[e.target.value] = true; else delete sel[e.target.value];
        show();
      } else if (e.target.id === 'amAll') {
        onPage().forEach(function (tr) { var id = tr.getAttribute('data-id'); if (e.target.checked) sel[id] = true; else delete sel[id]; });
        show();
      }
    });

    /* ---- the level editor (single edit and assign to several) ---- */
    function dupOf(i) { for (var k = 0; k < i; k++) if (ed.levels[k] && ed.levels[k] === ed.levels[i]) return k; return -1; }
    function chainValid() {
      if (!ed.levels.length) return false;
      for (var i = 0; i < ed.levels.length; i++) if (!ed.levels[i] || dupOf(i) >= 0) return false;
      return true;
    }
    function renderLevels() {
      var box = $('#amLevels'), h = '';
      ed.levels.forEach(function (v, i) {
        var o = '<option value="">Select approver</option>';
        opts.forEach(function (op) {
          if (ed.self && op.id === ed.self) return;          // a staff member cannot approve themselves
          o += '<option value="' + esc(op.id) + '"' + (op.id === v ? ' selected' : '') + '>' + esc(op.label) + '</option>';
        });
        var d = v ? dupOf(i) : -1;
        var msg = d >= 0 ? '<div class="err">' + esc(shortName(v)) + ' is already the level ' + (d + 1) + ' approver.</div>'
          : '<div class="msg">' + (!v ? 'Select an approver or remove this level.' : '') + '</div>';   // the line always keeps its space
        h += '<div class="levrow"><div class="ln">Level ' + (i + 1) + '</div>'
          + '<div class="f' + (d >= 0 ? ' bad' : '') + '"><select class="amLv" data-i="' + i + '" aria-label="Level ' + (i + 1) + ' approver">' + o + '</select>' + msg + '</div>'
          + '<div class="tools"><button type="button" class="sm" data-act="am-up" data-i="' + i + '" title="Move up"' + (i === 0 ? ' disabled' : '') + '>&uarr;</button>'
          + '<button type="button" class="sm" data-act="am-down" data-i="' + i + '" title="Move down"' + (i === ed.levels.length - 1 ? ' disabled' : '') + '>&darr;</button>'
          + '<button type="button" class="sm d" data-act="am-del" data-i="' + i + '" title="Remove level">&times;</button></div></div>';
      });
      var n = ed.levels.length, full = n >= MAXL, waiting = n > 0 && !ed.levels[n - 1];
      h += '<button type="button" class="addlv" data-act="am-add"' + (full || waiting ? ' disabled' : '') + '>+ Add level' + (full ? '' : ' ' + (n + 1)) + '</button>'
        + '<div class="addhelp">' + (waiting ? 'Select the level ' + n + ' approver first.'
          : full ? 'Maximum of ' + MAXL + ' approval levels reached.' : n === 0 ? 'Add at least one approver.' : '') + '</div>';
      box.innerHTML = h;
      if (ed.mode === 'bulk') renderBulk(); else updateSave();
    }
    function updateSave() {
      var btn = $('#amSaveBtn'); if (!btn) return;
      var ok = chainValid();
      if (ed.mode === 'bulk' && replacedList().length) ok = ok && $('#amConfirm').checked;
      btn.disabled = !ok;
    }
    function reasonBox() {
      return '<div class="f"><label for="amReason">Reason for change <span class="mut">(optional)</span></label>'
        + '<textarea id="amReason" rows="2" maxlength="' + AM_MAXLEN + '"></textarea></div>';
    }

    /* single staff member */
    function openEdit(s, keep) {
      if (!keep) ed = { mode: 'one', staff: s, self: s.id, levels: s.chain.length ? s.chain.slice() : [''], reason: '' };
      amModal('<h3>Approvers &ndash; ' + esc(staffText(s)) + '</h3>'
        + '<div class="ro2"><div><span>Team</span>' + esc(s.team || '\u2014') + '</div><div><span>User type</span>' + esc(s.userType || '\u2014')
        + '</div><div><span>Current approvers</span>' + esc(s.chainText || 'None') + '</div></div>'
        + '<div class="applies"><span class="badge b-Approved">Holiday Intimation</span><span class="badge b-Approved">Comp Off</span>'
        + 'This chain is used for both. Up to ' + MAXL + ' levels.</div>'
        + '<div class="levels" id="amLevels"></div>'
        + '<p class="mut sub" style="margin:2px 0 10px">Approvers: active staff with the Approver role (' + esc(s.name) + ' is not listed).</p>'
        + reasonBox()
        + '<div class="hint">Applies to new requests. Requests waiting with an approver who is no longer set for that level will have you (Admin) as approver.</div>'
        + modalBtns('Cancel', '<button type="button" class="p" id="amSaveBtn" data-act="am-save">Save approvers</button>'));
      $('#amReason').value = ed.reason;
      renderLevels();
    }
    function confirmReplace() {
      var s = ed.staff;
      ed.reason = $('#amReason').value;
      amModal('<h3>Replace approvers for ' + esc(staffText(s)) + '?</h3>'
        + '<p class="mut" style="margin-top:-4px">' + esc(s.name) + ' already has approvers. Saving replaces them for both Holiday Intimation and Comp Off.</p>'
        + '<div class="cmp"><div><h4>Current</h4><div class="chain">' + chips((s.chainText || '').split(' \u2192 ')) + '</div></div>'
        + '<div class="new"><h4>New</h4><div class="chain">' + chips(ed.levels.map(shortName)) + '</div></div></div>'
        + '<div class="hint">Requests waiting with an approver who is no longer set for that level will have you (Admin) as approver.</div>'
        + modalBtns('Go back', '<button type="button" class="p" data-act="am-replace">Replace approvers</button>', 'am-goback'));
    }

    /* several staff */
    function targets() { return ed.targets; }
    function replacedList() { return targets().filter(function (s) { return s.chain.length; }); }
    function openBulk() {
      var t = selIds().map(function (id) { return rd(byId[id]); });
      t.sort(function (a, b) { return a.name.localeCompare(b.name); });
      ed = { mode: 'bulk', targets: t, self: null, levels: [''], tab: 'all', search: '' };
      var rep = replacedList().length;
      amModal('<h3>Assign approvers to ' + t.length + (t.length === 1 ? ' staff member' : ' staff') + '</h3>'
        + '<div class="applies"><span class="badge b-Approved">Holiday Intimation</span><span class="badge b-Approved">Comp Off</span>'
        + 'This chain is used for both. Up to ' + MAXL + ' levels.</div>'
        + '<div class="levels" id="amLevels"></div>'
        + '<div id="amSelfWarn"></div>'
        + '<div class="sum"><div><b>' + t.length + '</b><span>staff selected</span></div><div><b>' + (t.length - rep) + '</b><span>no approvers yet &ndash; will be assigned</span></div>'
        + '<div' + (rep ? ' class="w"' : '') + '><b>' + rep + '</b><span>already have approvers &ndash; will be replaced</span></div></div>'
        + '<div class="tabs"><button type="button" data-act="am-tab" data-tab="all">All (' + t.length + ')</button>'
        + '<button type="button" data-act="am-tab" data-tab="rep">Will be replaced (' + rep + ')</button>'
        + '<button type="button" data-act="am-tab" data-tab="new">New (' + (t.length - rep) + ')</button>'
        + '<input id="amSearch" placeholder="Search name or code" aria-label="Search the selected staff" autocomplete="off"></div>'
        + '<div class="slist"><table><thead><tr><th>Emp Code</th><th>Name</th><th>Team</th><th>Current approvers</th></tr></thead><tbody id="amList"></tbody></table></div>'
        + (rep ? '<label class="confirmline"><input type="checkbox" id="amConfirm"> <span id="amConfirmText"></span></label>' : '')
        + reasonBox()
        + '<div class="hint">Applies to new requests. Requests waiting with an approver who is no longer set for that level will have you (Admin) as approver.</div>'
        + modalBtns('Cancel', '<button type="button" class="p" id="amSaveBtn" data-act="am-bulk-save">Assign to ' + t.length + (t.length === 1 ? ' staff member' : ' staff') + '</button>'), true);
      renderLevels();
    }
    function renderBulk() {
      var rep = replacedList().length;
      $$('.tabs button').forEach(function (b) { b.classList.toggle('on', b.getAttribute('data-tab') === ed.tab); });
      var q = ed.search.toLowerCase(), body = $('#amList');
      body.textContent = '';
      targets().filter(function (s) {
        return (ed.tab === 'all' || (ed.tab === 'rep') === !!s.chain.length)
          && (!q || s.name.toLowerCase().indexOf(q) >= 0 || s.code.toLowerCase().indexOf(q) >= 0);
      }).forEach(function (s) {
        var tr = document.createElement('tr');
        [s.code, s.name, s.team || '\u2014', s.chainText || 'None'].forEach(function (v) {
          var td = document.createElement('td'); td.textContent = v; tr.appendChild(td);
        });
        body.appendChild(tr);
      });
      if (!body.children.length) { var tr = document.createElement('tr'), td = document.createElement('td'); td.colSpan = 4; td.className = 'empty'; td.textContent = 'No staff in this list.'; tr.appendChild(td); body.appendChild(tr); }
      var names = ed.levels.filter(Boolean).map(shortName).join(' \u2192 ');
      if (rep) $('#amConfirmText').innerHTML = 'I confirm the current approvers of these <b>' + rep + ' staff</b> will be replaced'
        + (names ? ' by ' + esc(names) : '') + '.';
      var self = targets().filter(function (s) { return ed.levels.indexOf(s.id) >= 0; });
      $('#amSelfWarn').innerHTML = self.length ? '<div class="hint warn">' + (self.length === 1 ? '1 selected staff member is an approver'
        : self.length + ' selected staff are approvers') + ' in this chain and will be skipped (no one approves their own requests): '
        + esc(self.map(staffText).join(', ')) + '</div>' : '';
      updateSave();
    }

    function submit(staffIds, confirm) {
      var f = $('#amSave');
      f.staffIds.value = staffIds.join(',');
      f.approverIds.value = ed.levels.join(',');
      f.reason.value = ed.reason || '';
      f.confirmReplace.value = confirm ? 'yes' : 'no';
      closeModal();
      loading('Saving approvers&hellip;');
      f.submit();
    }

    document.addEventListener('change', function (e) {
      if (ed && e.target.classList && e.target.classList.contains('amLv')) {
        ed.levels[+e.target.getAttribute('data-i')] = e.target.value;
        renderLevels();
      } else if (ed && e.target.id === 'amConfirm') updateSave();
    });
    document.addEventListener('input', function (e) {
      if (ed && e.target.id === 'amSearch') { ed.search = e.target.value.trim(); renderBulk(); }
    });
    document.addEventListener('click', function (e) {
      var b = e.target.closest && e.target.closest('[data-act^="am-"]');
      if (!b || b.disabled) return;
      var act = b.getAttribute('data-act'), i = +b.getAttribute('data-i');
      if (act === 'am-page') { page = Math.max(0, Math.min(pages() - 1, +b.getAttribute('data-page'))); show(); }
      else if (act === 'am-selall') { rows.forEach(function (tr) { sel[tr.getAttribute('data-id')] = true; }); show(); }
      else if (act === 'am-clear') { sel = {}; show(); }
      else if (act === 'am-edit') openEdit(rd(b.closest('tr')), false);
      else if (act === 'am-history') { var t = $('#am-hist-' + b.closest('tr').getAttribute('data-id')); amModal(t.innerHTML, true); }
      else if (act === 'am-bulk') openBulk();
      else if (act === 'am-add') { if (ed.levels.length < MAXL && (!ed.levels.length || ed.levels[ed.levels.length - 1])) ed.levels.push(''); renderLevels(); }
      else if (act === 'am-del') { ed.levels.splice(i, 1); renderLevels(); }
      else if (act === 'am-up' && i > 0) { var u = ed.levels[i - 1]; ed.levels[i - 1] = ed.levels[i]; ed.levels[i] = u; renderLevels(); }
      else if (act === 'am-down' && i < ed.levels.length - 1) { var w = ed.levels[i + 1]; ed.levels[i + 1] = ed.levels[i]; ed.levels[i] = w; renderLevels(); }
      else if (act === 'am-tab') { ed.tab = b.getAttribute('data-tab'); renderBulk(); }
      else if (act === 'am-save') {
        if (!chainValid()) return;
        ed.reason = $('#amReason').value;
        if (ed.staff.chain.length) confirmReplace(); else submit([ed.staff.id], false);
      }
      else if (act === 'am-goback') openEdit(ed.staff, true);
      else if (act === 'am-replace') submit([ed.staff.id], true);
      else if (act === 'am-bulk-save') {
        if (!chainValid() || (replacedList().length && !$('#amConfirm').checked)) return;
        ed.reason = $('#amReason').value;
        submit(targets().map(function (s) { return s.id; }), replacedList().length > 0);
      }
    });
    show();
  })();

  /* ---------------- Login ---------------- */
  var lf = $('#loginForm');
  if (lf) {
    lf.addEventListener('submit', function (e) {
      var id = lf.loginId.value.trim(), pw = lf.password.value;
      var ei = id ? '' : 'Email or login ID is required.', ep = pw ? '' : 'Password is required.';
      setErr(lf, 'loginId', ei); setErr(lf, 'password', ep);
      if (ei || ep) { e.preventDefault(); return; }
      $$('button', lf).forEach(function (b) { b.disabled = true; });
    });
  }
})();
