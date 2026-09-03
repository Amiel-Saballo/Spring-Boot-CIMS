"use strict";

window.__CIMS_SCRIPT_LOADED__ = true;

function storageGet(key) {
  try {
    return window.sessionStorage ? sessionStorage.getItem(key) : null;
  } catch (_) {
    return null;
  }
}
function storageSet(key, value) {
  try {
    if (window.sessionStorage) sessionStorage.setItem(key, value);
  } catch (_) {
    /* Authentication still works for this tab even when storage is blocked. */
  }
}
function storageRemove(key) {
  try {
    if (window.sessionStorage) sessionStorage.removeItem(key);
  } catch (_) {
    /* Ignore blocked storage. */
  }
}

const app = {
  session: null,
  token: storageGet("cims.basic") || "",
  page: "Dashboard",
  draftReceiving: [],
  draftIssuance: [],
  lastSync: null,
  refreshTimer: null,
  rendering: false,
  databaseHealth: null,
};

const PAGE_DEFS = [
  ["Dashboard", null, "⌂"],
  ["Receiving", "RECEIVING", "⇩"],
  ["Receiving Records", "RECEIVING", "▤"],
  ["Approvals", "APPROVALS", "✓"],
  ["Issuance", "ISSUANCE", "⇧"],
  ["Issuance Records", "ISSUANCE", "▥"],
  ["Batches", "BATCHES", "◫"],
  ["Equipment", "EQUIPMENT", "▣"],
  ["Disposals", "DISPOSAL", "⌫"],
  ["Suppliers", "SUPPLIERS", "◆"],
  ["Reports", "REPORTS", "▦"],
  ["Item Master", "ITEMS", "◧"],
  ["Users", "USERS", "♙"],
  ["Roles", "ROLES", "♜"],
  ["System Settings", "SETTINGS", "⚙"],
  ["Transaction Log", "TRANSACTION_LOG", "≡"],
];

const PERMISSION_DESCRIPTIONS = {
  ITEMS: [
    "Item Master",
    "Create, edit, deactivate, and reactivate inventory items.",
  ],
  RECEIVING: [
    "Receiving",
    "Encode receiving transactions and manage own receiving records.",
  ],
  APPROVALS: ["Approvals", "Review, approve, or return receiving requests."],
  ISSUANCE: [
    "Issuance",
    "Issue medicines and supplies and edit issuance records.",
  ],
  BATCHES: ["Batches", "View batch records."],
  EQUIPMENT: [
    "Equipment",
    "View equipment and update permitted equipment status.",
  ],
  DISPOSAL: [
    "Disposal",
    "Dispose batch stock or equipment and view disposal history.",
  ],
  SUPPLIERS: ["Suppliers", "Maintain the supplier master list."],
  REPORTS: ["Reports", "Generate, preview, and export reports."],
  USERS: ["Users", "Create and maintain user accounts."],
  ROLES: ["Roles", "Create roles and configure permissions."],
  SETTINGS: [
    "System Settings",
    "Manage near-expiry days and units of measure.",
  ],
  LOCATIONS: ["Locations", "Manage clinic locations in System Settings."],
  TRANSACTION_LOG: [
    "Transaction Log",
    "View inventory transaction audit records.",
  ],
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const fmtDate = (value) =>
  value
    ? new Date(
        String(value).length === 10 ? `${value}T00:00:00` : value,
      ).toLocaleDateString("en-PH", {
        year: "numeric",
        month: "short",
        day: "2-digit",
      })
    : "—";
const fmtDateTime = (value) =>
  value
    ? new Date(value).toLocaleString("en-PH", {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
const today = () => new Date().toISOString().slice(0, 10);
const labelize = (value) =>
  String(value ?? "")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (m) => m.toUpperCase());
const hasPerm = (code) => !!app.session?.permissions?.includes(code);
const badge = (value) =>
  `<span class="badge ${esc(value)}">${esc(labelize(value))}</span>`;

function toast(message, kind = "ok") {
  const el = document.createElement("div");
  el.className = `toast ${kind === "error" ? "error" : ""}`;
  el.textContent = message;
  $("#toastHost").appendChild(el);
  setTimeout(() => el.remove(), 3500);
}

function serverError(data, status) {
  if (data?.message) {
    const fields = data.fields
      ? " " +
        Object.entries(data.fields)
          .map(([k, v]) => `${k}: ${v}`)
          .join("; ")
      : "";
    return `${data.message}${fields}`;
  }
  if (status === 403)
    return "You do not have permission to perform this action.";
  if (status === 401) return "Your login is no longer valid.";
  return `Request failed (${status}).`;
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (app.token) headers.set("Authorization", `Basic ${app.token}`);
  if (
    options.body &&
    !(options.body instanceof FormData) &&
    !headers.has("Content-Type")
  )
    headers.set("Content-Type", "application/json");

  const controller = new AbortController();
  const timeoutMs = Number(options.timeoutMs || 12000);
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(path, {
      ...options,
      headers,
      cache: "no-store",
      signal: controller.signal,
    });
    const type = response.headers.get("content-type") || "";
    if (!response.ok) {
      let data = null;
      try {
        data = type.includes("json")
          ? await response.json()
          : { message: await response.text() };
      } catch {}
      if (response.status === 401 && app.session) logout(false);
      throw new Error(serverError(data, response.status));
    }
    if (response.status === 204) return null;
    if (options.raw) return response;
    return type.includes("json") ? response.json() : response.text();
  } catch (e) {
    if (e?.name === "AbortError") {
      throw new Error(
        `The Spring Boot server did not respond within ${Math.round(timeoutMs / 1000)} seconds. Make sure the application and MySQL are running, then open http://localhost:8080/.`,
      );
    }
    if (e instanceof TypeError && /fetch/i.test(String(e.message))) {
      throw new Error(
        "Could not reach the Spring Boot server. Make sure you opened http://localhost:8080/ and that Eclipse shows the application as running.",
      );
    }
    throw e;
  } finally {
    clearTimeout(timeout);
  }
}

async function downloadApi(path, options, fallbackName) {
  const response = await api(path, { ...options, raw: true });
  const blob = await response.blob();
  const cd = response.headers.get("content-disposition") || "";
  const match = cd.match(/filename="?([^";]+)"?/i);
  const name = match?.[1] || fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function fetchAll(path, params = {}) {
  const all = [];
  let page = 0;
  while (true) {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") q.append(k, v);
    });
    q.set("page", page);
    q.set("size", 100);
    const data = await api(`${path}?${q.toString()}`);
    all.push(...(data.content || []));
    if (data.last || page + 1 >= (data.totalPages || 1)) break;
    page++;
    if (page > 1000) throw new Error("Pagination safety limit exceeded.");
  }
  return all;
}

function modal(title, body, { wide = false } = {}) {
  const host = $("#modalHost");
  host.innerHTML = `<div class="modal-backdrop"><section class="modal ${wide ? "wide" : ""}" role="dialog" aria-modal="true"><header class="modal-head"><h2>${esc(title)}</h2><button class="x" aria-label="Close">×</button></header><div class="modal-body">${body}</div></section></div>`;
  $(".x", host).onclick = closeModal;
  $(".modal-backdrop", host).addEventListener("mousedown", (e) => {
    if (e.target === e.currentTarget) closeModal();
  });
  return $(".modal-body", host);
}
function closeModal() {
  $("#modalHost").innerHTML = "";
}
function confirmModal(title, message, onYes) {
  const body = modal(
    title,
    `<p>${esc(message)}</p><div class="modal-actions"><button class="btn" id="mCancel">Cancel</button><button class="btn danger" id="mYes">Confirm</button></div>`,
  );
  $("#mCancel", body).onclick = closeModal;
  $("#mYes", body).onclick = async () => {
    try {
      await onYes();
      closeModal();
    } catch (e) {
      toast(e.message, "error");
    }
  };
}

function showLogin(message = "") {
  app.session = null;
  app.page = "Dashboard";
  clearInterval(app.refreshTimer);
  app.refreshTimer = null;
  $("#app").innerHTML =
    `<div class="login-shell"><div class="login-card"><div class="brand-mark">CIMS</div><h1>Clinic Inventory Management System</h1><p>Sign in to the Spring Boot application. All inventory data is stored in MySQL and shared across browsers.</p><div id="loginDbStatus" class="small muted" style="margin-bottom:12px">Checking MySQL connection…</div>${message ? `<div class="notice error" style="margin-bottom:14px">${esc(message)}</div>` : ""}<form id="loginForm"><div class="field"><label class="req">Email</label><input id="loginEmail" type="email" autocomplete="username" value="nurse@clinic.local" required></div><div class="field" style="margin-top:11px"><label class="req">Password</label><input id="loginPassword" type="password" autocomplete="current-password" value="ChangeMe123!" required></div><button class="btn primary" style="width:100%;margin-top:16px">Sign in</button></form><div class="divider"></div><div class="small muted">Demo accounts</div><div class="actions" style="margin-top:8px"><button class="btn small demo" data-email="nurse@clinic.local">Nurse</button><button class="btn small demo" data-email="supervisor@clinic.local">Supervisor</button><button class="btn small demo" data-email="admin@clinic.local">Administrator</button></div></div></div>`;
  $("#loginForm").onsubmit = async (e) => {
    e.preventDefault();
    await login($("#loginEmail").value.trim(), $("#loginPassword").value);
  };
  $$(".demo").forEach(
    (b) =>
      (b.onclick = () => {
        $("#loginEmail").value = b.dataset.email;
        $("#loginPassword").value = "ChangeMe123!";
      }),
  );
  checkLoginDatabaseStatus();
}
async function checkLoginDatabaseStatus() {
  const el = $("#loginDbStatus");
  if (!el || location.protocol === "file:") return;
  try {
    const health = await api("/api/system/health", { timeoutMs: 5000 });
    if ($("#loginDbStatus")) {
      $("#loginDbStatus").textContent =
        `Database: ${health.database} connected`;
      $("#loginDbStatus").className = "small db-inline-ok";
    }
  } catch (e) {
    if ($("#loginDbStatus")) {
      $("#loginDbStatus").textContent = `Database check failed: ${e.message}`;
      $("#loginDbStatus").className = "small db-inline-down";
    }
  }
}

async function login(email, password) {
  const token = btoa(unescape(encodeURIComponent(`${email}:${password}`)));
  app.token = token;
  try {
    app.session = await api("/api/session/me");
    storageSet("cims.basic", token);
    renderShell();
    await navigate("Dashboard");
  } catch (e) {
    app.token = "";
    storageRemove("cims.basic");
    showLogin(e.message);
  }
}
function logout(show = true) {
  app.token = "";
  app.session = null;
  storageRemove("cims.basic");
  if (show) showLogin();
}
async function refreshSession() {
  app.session = await api("/api/session/me");
  renderNav();
}
async function refreshDatabaseHealth() {
  const el = $("#dbStatus");
  try {
    app.databaseHealth = await api("/api/system/health");
    if (el) {
      el.textContent = `${app.databaseHealth.database} connected`;
      el.classList.remove("down");
      el.classList.add("up");
    }
  } catch (e) {
    app.databaseHealth = { status: "DOWN" };
    if (el) {
      el.textContent = "Database unavailable";
      el.classList.remove("up");
      el.classList.add("down");
    }
  }
}

function allowedPages() {
  return PAGE_DEFS.filter(([name, perm]) => {
    if (!perm) return true;
    if (name === "System Settings") {
      if (!hasPerm("SETTINGS") && !hasPerm("LOCATIONS")) return false;
    } else if (!hasPerm(perm)) return false;
    if (name === "Receiving" && app.session.role === "Supervisor") return false;
    if (name === "Receiving Records" && app.session.role === "Supervisor")
      return true;
    return true;
  });
}
function renderShell() {
  $("#app").innerHTML =
    `<div class="app-shell"><aside class="sidebar"><div class="sidebar-brand"><b>Clinic Inventory</b><span>Management System</span></div><nav class="nav" id="nav"></nav><div class="sidebar-user"><b>${esc(app.session.fullName)}</b><span>${esc(app.session.role)} · ${esc(app.session.email)}</span><button class="btn small" id="logoutBtn">Sign out</button></div></aside><main class="main"><header class="topbar"><h1 id="pageTitle">Dashboard</h1><div class="top-actions"><span class="db-status" id="dbStatus">Checking database…</span><span class="sync" id="syncStatus"></span><button class="btn small" id="refreshBtn">Refresh</button></div></header><div class="content" id="content"></div></main></div>`;
  $("#logoutBtn").onclick = () => logout();
  $("#refreshBtn").onclick = async () => {
    await refreshDatabaseHealth();
    await renderPage(true);
  };
  renderNav();
  refreshDatabaseHealth();
}
function renderNav() {
  const nav = $("#nav");
  if (!nav) return;
  nav.innerHTML = allowedPages()
    .map(
      ([name, , icon]) =>
        `<button data-page="${esc(name)}" class="${app.page === name ? "active" : ""}"><span class="icon">${icon}</span>${esc(name)}</button>`,
    )
    .join("");
  $$("[data-page]", nav).forEach(
    (b) => (b.onclick = () => navigate(b.dataset.page)),
  );
}
async function navigate(page) {
  const available = allowedPages().map((x) => x[0]);
  app.page = available.includes(page) ? page : "Dashboard";
  renderNav();
  await renderPage(true);
}

async function renderPage(showSpinner = true) {
  if (app.rendering || !app.session) return;
  app.rendering = true;
  const content = $("#content");
  $("#pageTitle").textContent = app.page;
  if (showSpinner)
    content.innerHTML =
      '<div class="boot-screen" style="min-height:300px"><div class="spinner"></div><p>Loading current database data…</p></div>';
  try {
    const fn = PAGES[app.page] || pageDashboard;
    await fn(content);
    app.lastSync = new Date();
    $("#syncStatus").textContent =
      `Synced ${app.lastSync.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
    initTables(content);
  } catch (e) {
    content.innerHTML = `<div class="notice error"><b>Could not load this page.</b><br>${esc(e.message)}</div>`;
  } finally {
    app.rendering = false;
  }
}

function tableMarkup({
  id,
  columns,
  rows,
  search = true,
  searchFields = null,
  filters = [],
}) {
  const cols = columns
    .map((c) => `<col style="width:${c.width || 140}px">`)
    .join("");
  const controls =
    search || filters.length
      ? `<div class="table-controls">${search ? `<input class="search-input" data-table-search="${id}" placeholder="Search records">` : ""}${filters.map((f) => `<select data-table-filter="${id}" data-key="${esc(f.key)}"><option value="">${esc(f.allLabel || "All")}</option>${f.options.map((o) => `<option value="${esc(o.value)}" ${String(o.value) === String(f.value ?? "") ? "selected" : ""}>${esc(o.label)}</option>`).join("")}</select>`).join("")}</div>`
      : "";
  return `<div class="table-box" data-table-id="${id}" data-search-fields="${esc((searchFields || []).join(","))}">${controls}<div class="table-wrap"><table class="data-table"><colgroup>${cols}</colgroup><thead><tr>${columns.map((c, i) => `<th class="${c.sortable === false ? "" : "sortable"}" data-col="${i}" data-key="${esc(c.key || "")}" data-type="${esc(c.type || "text")}">${esc(c.label)}${c.sortable === false ? "" : ' <span class="sort-ind">↕</span>'}</th>`).join("")}</tr></thead><tbody>${rows.map((r, ri) => `<tr data-row="${ri}">${columns.map((c) => `<td>${c.render ? c.render(r) : esc(r[c.key])}</td>`).join("")}</tr>`).join("")}</tbody></table></div><div class="pagination"><span class="small muted table-count"></span><div class="pages"></div></div><script type="application/json" class="table-data">${esc(JSON.stringify(rows))}</script></div>`;
}

function initTables(root = document) {
  $$(".table-box", root).forEach((box) => {
    const table = $("table", box),
      body = $("tbody", box),
      originalRows = [...body.rows].map((tr, index) => ({
        tr,
        index,
        data: JSON.parse(
          $$(".table-data", box)[0]
            .textContent.replace(/&quot;/g, '"')
            .replace(/&amp;/g, "&")
            .replace(/&#39;/g, "'")
            .replace(/&lt;/g, "<")
            .replace(/&gt;/g, ">"),
        )[index],
      }));
    const state = { sortCol: null, dir: 1, page: 0, pageSize: 10 };
    const search = $("[data-table-search]", box);
    const filters = $$("[data-table-filter]", box);
    const searchFields = (box.dataset.searchFields || "")
      .split(",")
      .filter(Boolean);
    const apply = () => {
      let rows = [...originalRows];
      const q = (search?.value || "").trim().toLowerCase();
      if (q)
        rows = rows.filter((x) => {
          const d = x.data;
          return searchFields.length
            ? searchFields.some((k) =>
                String(d[k] ?? "")
                  .toLowerCase()
                  .includes(q),
              )
            : x.tr.textContent.toLowerCase().includes(q);
        });
      filters.forEach((f) => {
        if (f.value)
          rows = rows.filter(
            (x) => String(x.data[f.dataset.key] ?? "") === f.value,
          );
      });
      if (state.sortCol !== null) {
        const th = table.tHead.rows[0].cells[state.sortCol],
          key = th.dataset.key,
          type = th.dataset.type;
        rows.sort((a, b) => {
          let av = key ? a.data[key] : a.tr.cells[state.sortCol].textContent,
            bv = key ? b.data[key] : b.tr.cells[state.sortCol].textContent;
          if (type === "number") {
            av = Number(av) || 0;
            bv = Number(bv) || 0;
            return (av - bv) * state.dir;
          }
          if (type === "date") {
            return (new Date(av || 0) - new Date(bv || 0)) * state.dir;
          }
          return (
            String(av ?? "").localeCompare(String(bv ?? ""), undefined, {
              numeric: true,
              sensitivity: "base",
            }) * state.dir
          );
        });
      }
      const pages = Math.max(1, Math.ceil(rows.length / state.pageSize));
      state.page = Math.min(state.page, pages - 1);
      const start = state.page * state.pageSize,
        end = start + state.pageSize;
      body.innerHTML = "";
      rows.slice(start, end).forEach((x) => body.appendChild(x.tr));
      $(".table-count", box).textContent =
        `${rows.length} record${rows.length === 1 ? "" : "s"}`;
      const pg = $(".pages", box);
      pg.innerHTML =
        pages <= 1
          ? ""
          : `<button class="page-btn" data-p="prev">‹</button>${Array.from({ length: pages }, (_, i) => `<button class="page-btn ${i === state.page ? "active" : ""}" data-p="${i}">${i + 1}</button>`).join("")}<button class="page-btn" data-p="next">›</button>`;
      $$("[data-p]", pg).forEach(
        (b) =>
          (b.onclick = () => {
            const p = b.dataset.p;
            state.page =
              p === "prev"
                ? Math.max(0, state.page - 1)
                : p === "next"
                  ? Math.min(pages - 1, state.page + 1)
                  : Number(p);
            apply();
          }),
      );
    };
    $$(".sortable", table.tHead).forEach(
      (th) =>
        (th.onclick = () => {
          const col = Number(th.dataset.col);
          state.dir = state.sortCol === col ? -state.dir : 1;
          state.sortCol = col;
          state.page = 0;
          apply();
        }),
    );
    if (search)
      search.oninput = () => {
        state.page = 0;
        apply();
      };
    filters.forEach(
      (f) =>
        (f.onchange = () => {
          state.page = 0;
          apply();
        }),
    );
    apply();
  });
}

async function loadReferences() {
  const [uoms, locations] = await Promise.all([
    api("/api/settings/units-of-measure"),
    api("/api/settings/locations"),
  ]);
  return { uoms, locations };
}
function optionList(list, value = "id", label = "name", selected = null) {
  return list
    .map(
      (x) =>
        `<option value="${esc(x[value])}" ${String(x[value]) === String(selected) ? "selected" : ""}>${esc(x[label])}</option>`,
    )
    .join("");
}
function statusClass(v) {
  return String(v || "").toUpperCase();
}

const PAGES = {
  Dashboard: pageDashboard,
  Receiving: pageReceiving,
  "Receiving Records": pageReceivingRecords,
  Approvals: pageApprovals,
  Issuance: pageIssuance,
  "Issuance Records": pageIssuanceRecords,
  Batches: pageBatches,
  Equipment: pageEquipment,
  Disposals: pageDisposals,
  Suppliers: pageSuppliers,
  Reports: pageReports,
  "Item Master": pageItems,
  Users: pageUsers,
  Roles: pageRoles,
  "System Settings": pageSettings,
  "Transaction Log": pageLogs,
};

async function pageDashboard(root) {
  const d = await api("/api/dashboard");
  root.innerHTML = `<div class="stack"><div class="grid cols-4"><div class="card kpi shadow"><div class="label">Active item records</div><div class="value">${d.activeItems}</div><div class="hint">Medicines, supplies, and equipment</div></div><div class="card kpi shadow"><div class="label">Near-expiry batches</div><div class="value tone-amber">${d.nearExpiryBatches}</div><div class="hint">Global medicine threshold</div></div><div class="card kpi shadow"><div class="label">Below reorder level</div><div class="value tone-red">${d.lowStockItems}</div><div class="hint">Items needing attention</div></div><div class="card kpi shadow"><div class="label">${app.session.role === "Supervisor" ? "Awaiting approval" : "Equipment in use"}</div><div class="value">${app.session.role === "Supervisor" ? d.pendingReceiving : d.equipmentInUse}</div><div class="hint">${app.session.role === "Supervisor" ? "Receiving requests requiring review" : "Individually tracked assets"}</div></div></div><div class="grid cols-2"><div class="card"><div class="card-head"><h2>Needs attention</h2></div>${tableMarkup(
    {
      id: "needs",
      search: false,
      columns: [
        {
          label: "Signal",
          key: "signal",
          width: 110,
          render: (r) => badge(r.signal),
        },
        {
          label: "Item",
          key: "itemName",
          width: 220,
          render: (r) =>
            `<b>${esc(r.itemName)}</b><div class="small muted mono">${esc(r.itemCode)}</div>`,
        },
        {
          label: "Detail",
          key: "detail",
          width: 360,
          render: (r) =>
            `${esc(r.detail)}${r.reorderLevel != null ? `<div class="small muted">Reorder level: ${r.reorderLevel} · Reorder quantity: ${r.reorderQuantity}</div>` : ""}`,
        },
      ],
      rows: d.needsAttention || [],
    },
  )}</div><div class="card"><div class="card-head"><h2>Account access</h2></div><div class="card-body"><p><b>${esc(app.session.role)}</b></p><div class="actions">${app.session.permissions.map((p) => badge(p)).join(" ")}</div><p class="small muted" style="margin-top:14px">Navigation is generated from the permissions currently stored in MySQL. Role changes take effect on the next authenticated request.</p></div></div></div>${
    hasPerm("TRANSACTION_LOG")
      ? `<div class="card"><div class="card-head"><h2>Recent transactions</h2></div>${tableMarkup(
          {
            id: "recentlogs",
            search: false,
            columns: [
              {
                label: "Date",
                key: "transactionDate",
                type: "date",
                width: 150,
                render: (r) => fmtDateTime(r.transactionDate),
              },
              {
                label: "Type",
                key: "transactionType",
                width: 105,
                render: (r) => badge(r.transactionType),
              },
              { label: "Reference", key: "referenceNumber", width: 150 },
              { label: "Item", key: "itemName", width: 180 },
              { label: "Activity", key: "detail", width: 420 },
            ],
            rows: d.recentTransactions || [],
          },
        )}</div>`
      : ""
  }</div>`;
}

async function pageItems(root) {
  const [items, refs] = await Promise.all([
    fetchAll("/api/items", { sort: "code,asc" }),
    loadReferences(),
  ]);
  root.innerHTML = `<div class="stack"><div class="card"><div class="card-head"><div><h2>Item Master</h2></div><button class="btn primary" id="addItem">Add new item</button></div>${tableMarkup(
    {
      id: "items",
      searchFields: ["code", "name", "category", "status"],
      filters: [
        {
          key: "category",
          allLabel: "All categories",
          options: ["MEDICINE", "SUPPLY", "EQUIPMENT"].map((x) => ({
            value: x,
            label: labelize(x),
          })),
        },
        {
          key: "status",
          value: "ACTIVE",
          allLabel: "All statuses",
          options: ["ACTIVE", "INACTIVE"].map((x) => ({
            value: x,
            label: labelize(x),
          })),
        },
      ],
      columns: [
        { label: "Code", key: "code", width: 135 },
        { label: "Name", key: "name", width: 230 },
        {
          label: "Category",
          key: "category",
          width: 115,
          render: (r) => labelize(r.category),
        },
        { label: "Unit", key: "unitOfMeasure", width: 110 },
        {
          label: "Reorder level",
          key: "reorderLevel",
          type: "number",
          width: 115,
        },
        {
          label: "Reorder quantity",
          key: "reorderQuantity",
          type: "number",
          width: 130,
        },
        {
          label: "Status",
          key: "status",
          width: 105,
          render: (r) => badge(r.status),
        },
        {
          label: "Actions",
          sortable: false,
          width: 180,
          render: (r) =>
            `<div class="actions"><button class="btn small edit-item" data-id="${r.id}">Edit</button>${r.status === "ACTIVE" ? `<button class="btn small danger delete-item" data-id="${r.id}">Delete</button>` : `<button class="btn small primary reactivate-item" data-id="${r.id}">Reactivate</button>`}</div>`,
        },
      ],
      rows: items,
    },
  )}</div></div>`;
  $("#addItem").onclick = () => openItemForm(null, refs);
  $$(".edit-item").forEach(
    (b) =>
      (b.onclick = () =>
        openItemForm(
          items.find((x) => x.id == b.dataset.id),
          refs,
        )),
  );
  $$(".delete-item").forEach(
    (b) =>
      (b.onclick = () =>
        confirmModal(
          "Delete item",
          "This will only deactivate the item. The item will remain in the records.",
          async () => {
            await api(`/api/items/${b.dataset.id}`, { method: "DELETE" });
            toast("Item deactivated");
            await renderPage();
          },
        )),
  );
  $$(".reactivate-item").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          await api(`/api/items/${b.dataset.id}/reactivate`, {
            method: "POST",
          });
          toast("Item reactivated");
          await renderPage();
        } catch (e) {
          toast(e.message, "error");
        }
      }),
  );
}
function openItemForm(item, refs) {
  const isEdit = !!item,
    body = modal(
      isEdit ? "Edit item" : "Add new item",
      `<form id="itemForm">
  <div class="form-grid">
    <div class="field"><label class="req">Item code</label><input id="itemCode" value="${esc(item?.code || "")}" required></div>
    <div class="field span-2"><label class="req">Item name</label><input id="itemName" value="${esc(item?.name || "")}" required></div>
    <div class="field"><label class="req">Category</label><select id="itemCategory">${["MEDICINE", "SUPPLY", "EQUIPMENT"].map((x) => `<option ${x === item?.category ? "selected" : ""}>${x}</option>`).join("")}</select></div>
    <div class="field"><label class="req">Unit of Measure</label><select id="itemUoM">${optionList(refs.uoms, "id", "name", item?.unitOfMeasureId)}<option value="new">+ Add manually…</option></select></div>
        <div class="field span-2" id="newUoMPanel" style="display:none">
      <div class="notice">
        <div style="font-weight:600; margin-bottom:8px;">Add Unit of Measure</div>
        <div class="field"><label class="req">Unit of Measure Name</label><input id="newUoMName" type="text" placeholder="e.g. Tablet, Box, Bottle" autocomplete="off"></div>
        <div class="actions" style="margin-top:10px"><button type="button" class="btn primary small" id="saveNewUoM">Add Unit of Measure</button><button type="button" class="btn small" id="cancelNewUoM">Cancel</button></div>
      </div>
    </div>
    <div class="field"><label class="req">Reorder level</label><input id="itemReorderLevel" type="number" min="0" max="100" value="${item?.reorderLevel ?? 0}" required></div>
    <div class="field"><label class="req">Reorder quantity</label><input id="itemReorderQty" type="number" min="1" max="500" value="${item?.reorderQuantity ?? 1}" required></div>
  </div>
  <div class="modal-actions"><button type="button" class="btn" id="cancelItem">Cancel</button><button class="btn primary">Save item</button></div>
</form>`,
    );
  $("#cancelItem", body).onclick = closeModal;

  const uomSelect = $("#itemUoM", body);
  const newUoMPanel = $("#newUoMPanel", body);
  const newUoMName = $("#newUoMName", body);
  const saveNewUoM = $("#saveNewUoM", body);
  const cancelNewUoM = $("#cancelNewUoM", body);

  let previousUoM = uomSelect.value !== "new" ? uomSelect.value : "";

  uomSelect.onchange = () => {
    if (uomSelect.value === "new") {
      newUoMPanel.style.display = "block";
      newUoMName.value = "";
      setTimeout(() => newUoMName.focus(), 0);
      uomSelect.disabled = true;
    } else {
      previousUoM = uomSelect.value;
      newUoMPanel.style.display = "block";
      newUoMName.value = "";
    }
  };

  cancelNewUoM.onclick = () => {
    newUoMPanel.style.display = "none";
    newUoMName.value = "";
    uomSelect.value = previousUoM || refs.uoms[0]?.id || "";
  };

  saveNewUoM.onclick = async () => {
    const name = newUoMName.value.trim();

    if (!name) {
      return toast("Enter a Unit of Measure", "error");
    }

    try {
      saveNewUoM.disabled = "true";

      const u = await api("/api/settings/units-of-measure", {
        method: "POST",
        body: JSON.stringify({
          name: name,
        }),
      });

      refs.uoms.push(u);

      const option = document.createElement("option");
      option.value = u.id;
      option.textContent = u.name;

      const newOption = uomSelect.querySelector('option[value="new"]');
      uomSelect.insertBefore(option, newOption);

      uomSelect.value = u.id;
      previousUoM = String(u.id);

      newUoMPanel.style.display = "none";
      newUoMName.value = "";

      toast("Unit of Measure added");
    } catch (err) {
      toast(err.message, "error");
    } finally {
      saveNewUoM.disabled = false;
      uomSelect.disabled = false;
    }
  };

  newUoMName.onkeydown = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      saveNewUoM.click();
    }
  };

  // $("#itemUom", body).onchange = async (e) => {
  //   if (e.target.value === "new") {
  //     const name = prompt("Enter a new Unit of Measurement");
  //     if (!name) {
  //       e.target.value = item?.unitOfMeasureId || refs.uoms[0]?.id || "";
  //       return;
  //     }
  //     try {
  //       const u = await api("/api/settings/units-of-measure", {
  //         method: "POST",
  //         body: JSON.stringify({ name }),
  //       });
  //       refs.uoms.push(u);
  //       e.target.insertAdjacentHTML(
  //         "afterbegin",
  //         `<option value="${u.id}">${esc(u.name)}</option>`,
  //       );
  //       e.target.value = u.id;
  //     } catch (err) {
  //       toast(err.message, "error");
  //     }
  //   }
  // };
  $("#itemForm", body).onsubmit = async (e) => {
    e.preventDefault();
    if ($("#itemUoM", body).value === "new") {
      return toast("Add or select a Unit of Measure first", "error");
    }

    const req = {
      code: $("#itemCode").value.trim(),
      name: $("#itemName").value.trim(),
      category: $("#itemCategory").value,
      unitOfMeasureId: Number($("#itemUom").value),
      reorderLevel: Number($("#itemReorderLevel").value),
      reorderQuantity: Number($("#itemReorderQty").value),
    };

    if (
      !Number.isInteger(req.reorderQuantity) ||
      req.reorderQuantity < 1 ||
      req.reorderQuantity > 500
    ) {
      return toast("Reorder quantity must be between 1 and 500", "error");
    }

    try {
      await api(isEdit ? `/api/items/${item.id}` : "/api/items", {
        method: isEdit ? "PUT" : "POST",
        body: JSON.stringify(req),
      });
      closeModal();
      toast(isEdit ? "Item updated" : "Item created");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

function receivingCombo(items, selectedId = "") {
  return `<div class="combo" id="recCombo"><input id="recItemInput" autocomplete="off" placeholder="Search or select item"><input type="hidden" id="recItemId" value="${esc(selectedId)}"><div class="combo-menu" id="recComboMenu"></div></div>`;
}
function bindReceivingCombo(items, onChange) {
  const input = $("#recItemInput"),
    hidden = $("#recItemId"),
    menu = $("#recComboMenu");
  const groups = [
    ["MEDICINE", "MED"],
    ["EQUIPMENT", "EQ"],
    ["SUPPLY", "SUP"],
  ];
  const label = (i) => `${i.code} · ${i.name}`;
  const selected = items.find((i) => String(i.id) === String(hidden.value));
  if (selected) input.value = label(selected);
  const draw = () => {
    const q = input.value.toLowerCase().trim();
    menu.innerHTML =
      groups
        .map(([cat, g]) => {
          const rows = items.filter(
            (i) =>
              i.category === cat &&
              (!q || `${i.code} ${i.name}`.toLowerCase().includes(q)),
          );
          return rows.length
            ? `<div class="combo-group">${g}</div>${rows.map((i) => `<button type="button" class="combo-option" data-id="${i.id}"><b>${esc(i.code)}</b>${esc(i.name)}</button>`).join("")}`
            : "";
        })
        .join("") || '<div class="empty small">No matching items</div>';
    menu.classList.add("open");
    $$(".combo-option", menu).forEach(
      (b) =>
        (b.onclick = () => {
          const i = items.find((x) => x.id == b.dataset.id);
          hidden.value = i.id;
          input.value = label(i);
          menu.classList.remove("open");
          onChange(i);
        }),
    );
  };
  input.onfocus = draw;
  input.oninput = () => {
    hidden.value = "";
    draw();
  };
  document.addEventListener("mousedown", (e) => {
    if (!$("#recCombo")?.contains(e.target)) menu.classList.remove("open");
  });
}

async function pageReceiving(root) {
  const [suppliers, items, refs] = await Promise.all([
    fetchAll("/api/suppliers", { active: true, sort: "name,asc" }),
    fetchAll("/api/items", { status: "ACTIVE", sort: "code,asc" }),
    loadReferences(),
  ]);
  root.innerHTML = `<div class="stack"><div class="card"><div class="card-head"><h2>Encode Receiving Transaction</h2><span>${badge("PENDING")}</span></div><div class="card-body"><form id="receivingForm"><div class="form-grid"><div class="field span-2"><label class="req">Supplier</label><div class="actions"><select id="recSupplier" style="flex:1">${optionList(suppliers)}</select><button type="button" class="btn" id="addSupplierInline">+ Add Supplier</button></div></div><div class="field"><label class="req">Delivery reference</label><input id="recRef" required></div><div class="field"><label class="req">Date received</label><input id="recDate" type="date" value="${today()}" required></div><div class="field span-4"><label>Remarks</label><textarea id="recRemarks" maxlength="150" placeholder="Enter receiving remarks (maximum 150 characters)"></textarea><div class="help">Maximum 150 characters</div></div></div><div class="divider"></div><div class="form-grid"><div class="field span-2"><label class="req">Item</label>${receivingCombo(items)}</div><div class="field"><label class="req">Quantity / Unit</label><div class="inline-pair"><input id="recQty" type="number" min="1" max="500" value="1"><input id="recUom" disabled placeholder="Unit"></div></div><div class="field"><label>Brand</label><input id="recBrand"></div><div class="field"><label>Batch number <span class="muted">(optional)</span></label><input id="recBatch"></div><div class="field"><label>Expiry date</label><input id="recExpiry" type="date"></div><div class="field"><label>Equipment model</label><input id="recModel"></div><div class="field"><label>Serial number</label><input id="recSerial"></div><div class="field"><label>Asset tag</label><input id="recAsset"></div><div class="field"><label class="req">Location</label><select id="recLocation">${optionList(refs.locations)}</select></div><div class="field span-4"><button type="button" class="btn primary" id="addRecLine">Add delivery line</button></div></div><div id="receivingDraft" style="margin-top:14px"></div><div class="actions" style="margin-top:14px"><button class="btn primary">Submit for approval</button><button type="button" class="btn" id="clearRec">Clear draft</button></div></form></div></div></div>`;
  const syncItem = (i) => {
    $("#recUom").value = i?.unitOfMeasure || "";

    const isEquipment = i?.category === "EQUIPMENT";
    const isMedicine = i?.category === "MEDICINE";

    // Quantity
    $("#recQty").value = isEquipment ? 1 : $("#recQty").value;

    $("#recQty").disabled = isEquipment;

    // Batch
    $("#recBatch").disabled = isEquipment;

    // ============================================
    // EXPIRY DATE
    // ============================================

    const expiry = $("#recExpiry");
    const expiryLabel = $("#recExpiryLabel");

    expiry.disabled = isEquipment;

    const expiryDate = $("#recExpiry").value;

    if (items.category === "MEDICINE") {
      if (!expiryDate) {
        return toast("Expiry date is required for medicines", "error");
      }

      if (expiryDate < today()) {
        return toast("Expiry date cannot be in the past", "error");
      }
    }

    // // Required only for medicines
    // expiry.required = isMedicine;

    // Medicines cannot select a date before today
    expiry.min = isMedicine ? today() : "";

    // Show required indicator for medicines
    if (expiryLabel) {
      expiryLabel.classList.toggle("req", isMedicine);
    }

    // ============================================
    // EQUIPMENT FIELDS
    // ============================================

    $("#recModel").disabled = !isEquipment;
    $("#recSerial").disabled = !isEquipment;
    $("#recAsset").disabled = !isEquipment;
  };

  bindReceivingCombo(items, syncItem);
  syncItem(null);
  renderReceivingDraft();
  $("#addSupplierInline").onclick = () =>
    openSupplierForm(null, async (created) => {
      suppliers.push(created);
      $("#recSupplier").insertAdjacentHTML(
        "beforeend",
        `<option value="${created.id}">${esc(created.name)}</option>`,
      );
      $("#recSupplier").value = created.id;
    });
  $("#addRecLine").onclick = () => {
    // --------------------------------------------------
    // 1. Get selected item FIRST
    // --------------------------------------------------

    const itemId = $("#recItemId").value;

    const selectedItem = items.find(
      (item) => String(item.id) === String(itemId),
    );

    if (!selectedItem) {
      return toast("Select an item from the dropdown", "error");
    }

    const expiryDate = $("#recExpiry").value;

    if (selectedItem.category === "MEDICINE") {
      if (!expiryDate) {
        return toast("Expiry date is required for medicines", "error");
      }
    }

    // --------------------------------------------------
    // 2. Get quantity
    // --------------------------------------------------

    const quantity = Number($("#recQty").value);

    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 500) {
      return toast("Enter a valid quantity", "error");
    }

    // --------------------------------------------------
    // 3. Get location
    // --------------------------------------------------

    const locationId = Number($("#recLocation").value);

    if (!locationId) {
      return toast("Select a location", "error");
    }

    const selectedLocation = refs.locations.find(
      (location) => location.id == locationId,
    );

    // --------------------------------------------------
    // 4. COPY all form values into a new object
    // --------------------------------------------------

    const line = {
      itemId: selectedItem.id,

      itemCode: selectedItem.code,

      itemName: selectedItem.name,

      category: selectedItem.category,

      unitOfMeasure: selectedItem.unitOfMeasure,

      quantity: quantity,

      brand: $("#recBrand").value.trim(),

      batchNumber:
        selectedItem.category === "EQUIPMENT"
          ? null
          : $("#recBatch").value.trim() || null,

      expiryDate:
        selectedItem.category === "EQUIPMENT"
          ? null
          : $("#recExpiry").value || null,

      model:
        selectedItem.category === "EQUIPMENT"
          ? $("#recModel").value.trim() || null
          : null,

      serialNumber:
        selectedItem.category === "EQUIPMENT"
          ? $("#recSerial").value.trim() || null
          : null,

      assetTag:
        selectedItem.category === "EQUIPMENT"
          ? $("#recAsset").value.trim() || null
          : null,

      locationId: locationId,

      location: selectedLocation?.name || "",
    };

    // --------------------------------------------------
    // 5. Equipment validation
    // --------------------------------------------------

    if (
      selectedItem.category === "EQUIPMENT" &&
      (!line.serialNumber || !line.assetTag)
    ) {
      return toast(
        "Serial number and asset tag are required for equipment",
        "error",
      );
    }

    // --------------------------------------------------
    // 6. IMPORTANT: ADD THE LINE FIRST
    // --------------------------------------------------

    app.draftReceiving.push(line);

    // --------------------------------------------------
    // 7. IMPORTANT: RENDER IT BEFORE CLEARING
    // --------------------------------------------------

    renderReceivingDraft();

    // --------------------------------------------------
    // 8. NOW clear the item-entry form
    // --------------------------------------------------

    clearReceivingItemForm();

    toast("Delivery line added");
  };
  $("#clearRec").onclick = () => {
    app.draftReceiving = [];
    renderReceivingDraft();
  };
  $("#receivingForm").onsubmit = async (e) => {
    e.preventDefault();
    if (!app.draftReceiving.length)
      return toast("Add at least one delivery line", "error");
    const req = {
      supplierId: Number($("#recSupplier").value),
      referenceNumber: $("#recRef").value.trim(),
      dateReceived: $("#recDate").value,
      remarks: $("#recRemarks").value.trim() || null,
      lines: app.draftReceiving.map(
        ({
          itemId,
          quantity,
          brand,
          batchNumber,
          expiryDate,
          model,
          serialNumber,
          assetTag,
          locationId,
        }) => ({
          itemId,
          quantity,
          brand: brand || null,
          batchNumber,
          expiryDate,
          model,
          serialNumber,
          assetTag,
          locationId,
        }),
      ),
    };
    try {
      await api("/api/receiving", {
        method: "POST",
        body: JSON.stringify(req),
      });
      app.draftReceiving = [];
      toast("Receiving transaction submitted to MySQL");
      await navigate("Receiving Records");
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
function renderReceivingDraft() {
  const host = $("#receivingDraft");
  if (!host) return;
  host.innerHTML = tableMarkup({
    id: "recDraft",
    search: false,
    columns: [
      { label: "Item", key: "itemName", width: 220 },
      { label: "Quantity", key: "quantity", type: "number", width: 90 },
      { label: "Unit", key: "unitOfMeasure", width: 90 },
      {
        label: "Details",
        sortable: false,
        width: 300,
        render: (r) =>
          r.category === "EQUIPMENT"
            ? `${esc(r.brand || "—")} · ${esc(r.model || "—")}<br><span class="small muted">Serial: ${esc(r.serialNumber)} · Asset: ${esc(r.assetTag)} · ${esc(r.location)}</span>`
            : `${esc(r.brand || "—")}<br><span class="small muted">Batch: ${esc(r.batchNumber || "—")} · Expiry: ${fmtDate(r.expiryDate)} · ${esc(r.location)}</span>`,
      },
      {
        label: "",
        sortable: false,
        width: 80,
        render: (r) =>
          `<button class="btn small danger remove-rec" data-index="${app.draftReceiving.indexOf(r)}">Remove</button>`,
      },
    ],
    rows: app.draftReceiving,
  });
  initTables(host);
  $$(".remove-rec", host).forEach(
    (b) =>
      (b.onclick = () => {
        app.draftReceiving.splice(Number(b.dataset.index), 1);
        renderReceivingDraft();
      }),
  );
}

async function pageReceivingRecords(root) {
  const isSupervisor = app.session.role === "Supervisor";

  /*
   * Nurse:
   * Only load receiving transactions submitted by this Nurse.
   *
   * Supervisor:
   * Load ALL receiving transactions.
   */
  const records = isSupervisor
    ? await fetchAll("/api/receiving", {
        sort: "dateReceived,desc",
      })
    : await fetchAll("/api/receiving", {
        receivedBy: app.session.id,
        sort: "dateReceived,desc",
      });

  /*
   * ============================================================
   * SUPERVISOR VIEW
   * ============================================================
   *
   * Supervisor only reviews receiving transaction records here.
   * Approval actions belong to the Approvals page.
   */
  if (isSupervisor) {
    const columns = [
      {
        label: "Reference",
        key: "referenceNumber",
        width: 160,
      },
      {
        label: "Date",
        key: "dateReceived",
        type: "date",
        width: 120,
        render: (r) => fmtDate(r.dateReceived),
      },
      {
        label: "Supplier",
        key: "supplierName",
        width: 200,
      },
      {
        label: "Encoded By",
        key: "receivedBy",
        width: 180,
        render: (r) => esc(r.receivedBy || "—"),
      },
      {
        label: "Approved By",
        key: "approvedBy",
        width: 180,
        render: (r) => esc(r.approvedBy || "—"),
      },
      {
        label: "Status",
        key: "status",
        width: 120,
        render: (r) => badge(r.status),
      },
    ];

    root.innerHTML = `
<div class="card">

<div class="card-head">
<div>
<h2>Receiving Transaction Records</h2>
<div class="small muted">
View all receiving transactions
</div>
</div>
</div>

${tableMarkup({
  id: "supervisorReceivingRecords",
  searchFields: [
    "referenceNumber",
    "supplierName",
    "receivedBy",
    "approvedBy",
    "status",
  ],
  columns: columns,
  rows: records,
})}

</div>
`;

    return;
  }

  /*
   * ============================================================
   * NURSE VIEW
   * ============================================================
   */

  const pending = records.filter((r) => r.status === "PENDING");

  const returned = records.filter((r) => r.status === "RETURNED");

  const recent = records.filter(
    (r) => !["PENDING", "RETURNED"].includes(r.status),
  );

  const cols = [
    {
      label: "Reference",
      key: "referenceNumber",
      width: 160,
    },
    {
      label: "Date",
      key: "dateReceived",
      type: "date",
      width: 120,
      render: (r) => fmtDate(r.dateReceived),
    },
    {
      label: "Supplier",
      key: "supplierName",
      width: 200,
    },
    {
      label: "Lines",
      width: 70,
      key: "lines",
      render: (r) => r.lines.length,
    },
    {
      label: "Status",
      key: "status",
      width: 100,
      render: (r) => badge(r.status),
    },
  ];

  root.innerHTML = `
<div class="stack">

<!-- PENDING -->
<div class="card">
<div class="card-head">
<h2>Pending Requests</h2>
</div>

${tableMarkup({
  id: "pendingRec",
  searchFields: ["referenceNumber", "supplierName"],
  columns: [
    ...cols,
    {
      label: "Actions",
      sortable: false,
      width: 120,
      render: (r) => `
<button
class="btn small danger cancel-rec"
data-id="${r.id}">
Cancel
</button>
`,
    },
  ],
  rows: pending,
})}
</div>


<!-- RETURNED -->
<div class="card">
<div class="card-head">
<h2>Returned Requests</h2>
</div>

${tableMarkup({
  id: "returnedRec",
  searchFields: ["referenceNumber", "supplierName", "returnReason"],
  columns: [
    ...cols,
    {
      label: "Return reason",
      key: "returnReason",
      width: 260,
    },
    {
      label: "Actions",
      sortable: false,
      width: 180,
      render: (r) => `
<div class="actions">

<button
class="btn small edit-returned"
data-id="${r.id}">
Edit items
</button>

<button
class="btn small primary resubmit-rec"
data-id="${r.id}">
Resubmit
</button>

</div>
`,
    },
  ],
  rows: returned,
})}
</div>


<!-- RECENT -->
<div class="card">
<div class="card-head">
<h2>Recent Receiving Records</h2>
</div>

${tableMarkup({
  id: "recentRec",
  searchFields: ["referenceNumber", "supplierName"],
  columns: cols,
  rows: recent,
})}
</div>

</div>
`;

  /*
   * Nurse-only actions
   */

  $$(".cancel-rec").forEach((button) => {
    button.onclick = () => {
      reasonModal(
        "Cancel receiving request",
        "Cancellation reason",
        async (reason) => {
          await api(`/api/receiving/${button.dataset.id}/cancel`, {
            method: "POST",
            body: JSON.stringify({
              reason: reason,
            }),
          });

          toast("Receiving request cancelled");

          await renderPage();
        },
      );
    };
  });

  $$(".edit-returned").forEach((button) => {
    button.onclick = () => {
      const record = records.find((r) => r.id == button.dataset.id);

      openReturnedReceiving(record, async (updated) => {
        const index = records.findIndex((r) => r.id == updated.id);
        if (index >= 0) {
          Object.assign(records[index], updated);
        }
      });
    };
  });

  $$(".resubmit-rec").forEach((button) => {
    button.onclick = async () => {
      try {
        await api(`/api/receiving/${button.dataset.id}/resubmit`, {
          method: "POST",
        });

        toast("Receiving request resubmitted");

        await renderPage();
      } catch (error) {
        toast(error.message, "error");
      }
    };
  });
}

function reasonModal(title, label, onSave) {
  const body = modal(
    title,
    `<form id="reasonForm"><div class="field"><label class="req">${esc(label)}</label><textarea id="reasonText" maxlength="150" required></textarea><div class="help">Maximum 150 characters</div></div><div class="modal-actions"><button type="button" class="btn" id="cancelReason">Cancel</button><button class="btn primary">Submit</button></div></form>`,
  );
  $("#cancelReason", body).onclick = closeModal;
  $("#reasonForm", body).onsubmit = async (e) => {
    e.preventDefault();
    try {
      await onSave($("#reasonText").value.trim());
      closeModal();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
async function openReturnedReceiving(record, onRecordUpdated) {
  const refs = await loadReferences();
  const body = modal(
    `Returned receiving · ${record.referenceNumber}`,
    `<div class="notice warning"><b>Return reason:</b> ${esc(record.returnReason || "—")}</div><div style="margin-top:12px" id="returnedLines"></div><div class="modal-actions"><button class="btn" id="closeReturned">Close</button><button class="btn primary" id="resubmitReturned">Resubmit for approval</button></div>`,
    { wide: true },
  );
  const render = () => {
    $("#returnedLines", body).innerHTML = tableMarkup({
      id: "returnedLinesTable",
      search: false,
      columns: [
        { label: "Item", key: "itemName", width: 220 },
        { label: "Quantity", key: "quantity", type: "number", width: 90 },
        { label: "Unit", key: "unitOfMeasure", width: 90 },
        { label: "Location", key: "location", width: 120 },
        {
          label: "Details",
          sortable: false,
          width: 320,
          render: (r) =>
            r.category === "EQUIPMENT"
              ? `Brand: ${esc(r.brand || "—")} · Model: ${esc(r.model || "—")}<br>Serial: ${esc(r.serialNumber)} · Asset: ${esc(r.assetTag)}`
              : `Brand: ${esc(r.brand || "—")} · Batch: ${esc(r.batchNumber || "—")} · Expiry: ${fmtDate(r.expiryDate)}`,
        },
        {
          label: "",
          sortable: false,
          width: 90,
          render: (r) =>
            `<button class="btn small edit-returned-line" data-id="${r.id}">Edit Item</button>`,
        },
      ],
      rows: record.lines,
    });
    initTables($("#returnedLines", body));
    $$(".edit-returned-line", body).forEach(
      (b) =>
        (b.onclick = () =>
          editReturnedLine(
            record,
            record.lines.find((x) => x.id == b.dataset.id),
            refs,
            async (updated) => {
              Object.assign(record, updated);
              if (updated.lines) record.lines = updated.lines;
              if (typeof onRecordUpdated === "function") {
                await onRecordUpdated(updated);
              }
              render();
            },
          )),
    );
  };
  render();
  $("#closeReturned", body).onclick = closeModal;
  $("#resubmitReturned", body).onclick = async () => {
    try {
      await api(`/api/receiving/${record.id}/resubmit`, { method: "POST" });
      closeModal();
      toast("Receiving request resubmitted");
      await renderPage();
    } catch (e) {
      toast(e.message, "error");
    }
  };
}
function editReturnedLine(record, line, refs, onUpdated) {
  const eq = line.category === "EQUIPMENT",
    body = modal(
      `Edit item · ${line.itemName}`,
      `<form id="returnedLineForm"><div class="form-grid"><div class="field"><label class="req">Quantity / Unit</label><div class="inline-pair"><input id="rlQty" type="number" min="1" ${eq ? 'max="1"' : ""} value="${line.quantity}"><input value="${esc(line.unitOfMeasure)}" disabled></div></div><div class="field"><label>Brand</label><input id="rlBrand" value="${esc(line.brand || "")}"></div>${eq ? `<div class="field"><label>Model</label><input id="rlModel" value="${esc(line.model || "")}"></div><div class="field"><label class="req">Serial number</label><input id="rlSerial" value="${esc(line.serialNumber || "")}"></div><div class="field"><label class="req">Asset tag</label><input id="rlAsset" value="${esc(line.assetTag || "")}"></div>` : `<div class="field"><label>Batch number (optional)</label><input id="rlBatch" value="${esc(line.batchNumber || "")}"></div><div class="field"><label id="recExpiryLabel">Expiry date</label><input id="recExpiry" type="date" value="${esc(line.expiryDate || "")}"></div>`}<div class="field"><label class="req">Location</label><select id="rlLocation">${optionList(refs.locations, "id", "name", line.locationId)}</select></div></div><div class="modal-actions"><button type="button" class="btn" id="rlCancel">Cancel</button><button class="btn primary">Save item</button></div></form>`,
    );
  $("#rlCancel", body).onclick = closeModal;
  $("#returnedLineForm", body).onsubmit = async (e) => {
    e.preventDefault();
    const req = {
      itemId: line.itemId,
      quantity: Number($("#rlQty").value),
      brand: $("#rlBrand").value.trim() || null,
      batchNumber: eq ? null : $("#rlBatch").value.trim() || null,
      expiryDate: eq ? null : $("#recExpiry").value || null,
      model: eq ? $("#rlModel").value.trim() || null : null,
      serialNumber: eq ? $("#rlSerial").value.trim() || null : null,
      assetTag: eq ? $("#rlAsset").value.trim() || null : null,
      locationId: Number($("#rlLocation").value),
    };
    try {
      const updated = await api(
        `/api/receiving/${record.id}/returned/lines/${line.id}`,
        { method: "PUT", body: JSON.stringify(req) },
      );
      closeModal();
      toast("Returned item updated");
      await onUpdated(updated);
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageApprovals(root) {
  const records = await fetchAll("/api/approvals", {
    sort: "dateReceived,desc",
  });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Pending Receiving Approvals</h2></div>${tableMarkup(
    {
      id: "approvals",
      searchFields: ["referenceNumber", "supplierName", "receivedBy"],
      columns: [
        { label: "Reference", key: "referenceNumber", width: 160 },
        {
          label: "Date",
          key: "dateReceived",
          type: "date",
          width: 120,
          render: (r) => fmtDate(r.dateReceived),
        },
        { label: "Supplier", key: "supplierName", width: 200 },
        { label: "Encoded by", key: "receivedBy", width: 160 },
        {
          label: "Lines",
          key: "lines",
          width: 70,
          render: (r) => r.lines.length,
        },
        {
          label: "Actions",
          sortable: false,
          width: 190,
          render: (r) =>
            `<div class="actions"><button class="btn small review-approval" data-id="${r.id}">Review</button><button class="btn small primary approve" data-id="${r.id}">Approve</button><button class="btn small danger return" data-id="${r.id}">Return</button></div>`,
        },
      ],
      rows: records,
    },
  )}</div>`;
  $$(".review-approval").forEach(
    (b) =>
      (b.onclick = () =>
        reviewApproval(records.find((x) => x.id == b.dataset.id))),
  );
  $$(".approve").forEach(
    (b) =>
      (b.onclick = () =>
        confirmModal(
          "Approve receiving",
          "Post this receiving transaction to inventory?",
          async () => {
            await api(`/api/approvals/${b.dataset.id}/approve`, {
              method: "POST",
            });
            toast("Receiving approved and posted to MySQL");
            await renderPage();
          },
        )),
  );
  $$(".return").forEach(
    (b) =>
      (b.onclick = () =>
        reasonModal(
          "Return receiving request",
          "Return reason",
          async (reason) => {
            await api(`/api/approvals/${b.dataset.id}/return`, {
              method: "POST",
              body: JSON.stringify({ reason }),
            });
            toast("Receiving returned to Nurse");
            await renderPage();
          },
        )),
  );
}
function reviewApproval(r) {
  modal(
    `Review receiving · ${r.referenceNumber}`,
    `<div class="form-grid"><div class="field"><label>Reference number</label><div class="notice mono">${esc(r.referenceNumber)}</div></div><div class="field"><label>Supplier</label><div class="notice">${esc(r.supplierName)}</div></div><div class="field"><label>Date received</label><div class="notice">${fmtDate(r.dateReceived)}</div></div><div class="field"><label>Encoded by</label><div class="notice">${esc(r.receivedBy)}</div></div><div class="field span-4"><label>Remarks</label><div class="notice">${esc(r.remarks || "—")}</div></div></div><div class="stack" style="margin-top:14px">${r.lines.map((l) => `<div class="card"><div class="card-body"><div class="grid cols-3"><div><div class="small muted">Item</div><b>${esc(l.itemName)}</b><div class="small mono muted">${esc(l.itemCode)}</div></div><div><div class="small muted">Quantity</div><b class="nowrap">${l.quantity} ${esc(l.unitOfMeasure)}</b></div><div><div class="small muted">Details</div>${l.category === "EQUIPMENT" ? `Brand: ${esc(l.brand || "—")}<br>Model: ${esc(l.model || "—")}<br>Serial number: ${esc(l.serialNumber || "—")}<br>Asset tag: ${esc(l.assetTag || "—")}<br>Location: ${esc(l.location)}` : `Brand: ${esc(l.brand || "—")}<br>Batch number: ${esc(l.batchNumber || "—")}<br>Expiry: ${fmtDate(l.expiryDate)}<br>Location: ${esc(l.location)}`}</div></div></div></div>`).join("")}</div>`,
    { wide: true },
  );
}

async function pageIssuance(root) {
  const [allItems, allBatches] = await Promise.all([
    fetchAll("/api/items", {
      status: "ACTIVE",
      sort: "code,asc",
    }),
    fetchAll("/api/batches", {
      sort: "expiryDate,asc",
    }),
  ]);

  const issuanceDate = today();

  const usableBatches = allBatches.filter(
    (b) =>
      b.status === "ACTIVE" &&
      b.onHand > 0 &&
      (!b.expiryDate || b.expiryDate >= issuanceDate),
  );

  const stockFor = (itemId) =>
    usableBatches
      .filter((b) => b.itemId == itemId)
      .reduce((total, b) => total + Number(b.onHand || 0), 0);

  const items = allItems.filter(
    (i) =>
      i.category !== "EQUIPMENT" &&
      usableBatches.some((b) => b.itemId === i.id),
  );

  // const items = (
  //   await fetchAll("/api/items", { status: "ACTIVE", sort: "code,asc" })
  // ).filter((i) => i.category !== "EQUIPMENT");

  // root.innerHTML = `<div class="card"><div class="card-head"><h2>Record Medicine / Supply Issuance</h2><span class="badge green">FEFO</span></div><div class="card-body"><form id="issuanceForm"><div class="form-grid"><div class="field"><label class="req">Employee number</label><input id="issEmployeeNo" required></div><div class="field"><label class="req">Employee name</label><input id="issEmployeeName" required></div><div class="field"><label>Department</label><input id="issDepartment"></div><div class="field"><label>Supervisor</label><input id="issSupervisor"></div><div class="field span-2"><label class="req">Chief complaint</label><input id="issComplaint" required></div><div class="field"><label class="req">Disposition</label><select id="issDisposition"><option>Returned to work</option><option>Sent home</option><option>Referred to hospital</option></select></div><div class="field"><label class="req">Date</label><input id="issDate" type="date" value="${today()}" required></div><div class="field span-4"><label>Remarks</label><textarea id="issRemarks" maxlength="500"></textarea></div></div><div class="divider"></div><div class="form-grid"><div class="field span-2"><label class="req">Item</label><select id="issItem">${items.map((i) => `<option value="${i.id}">${esc(i.code)} · ${esc(i.name)}</option>`).join("")}</select></div><div class="field"><label class="req">Quantity / Unit</label><div class="inline-pair"><input id="issQty" type="number" min="1" value="1"><input id="issUom" disabled></div></div><div class="field"><button type="button" class="btn primary" id="addIssLine" style="margin-top:20px">Add item</button></div></div><div id="issueDraft" style="margin-top:14px"></div><div class="actions" style="margin-top:14px"><button class="btn primary">Record issuance</button><button type="button" class="btn" id="clearIss">Clear draft</button></div></form></div></div>`;
  root.innerHTML = `<div class="card">
    <div class="card-head">
      <h2>Record Medicine / Supply Issuance</h2><span class="badge green">FEFO</span>
    </div>
    <div class="card-body">
      <form id="issuanceForm">
        <div class="form-grid">
          <div class="field"><label class="req">Employee number</label><input id="issEmployeeNo" required></div>
          <div class="field"><label class="req">Employee name</label><input id="issEmployeeName" required></div>
          <div class="field"><label>Department</label><input id="issDepartment"></div>
          <div class="field"><label>Supervisor</label><input id="issSupervisor"></div>
          <div class="field span-2"><label class="req">Chief complaint</label><input id="issComplaint" required></div>
          <div class="field"><label class="req">Disposition</label><select id="issDisposition">
              <option>Returned to work</option>
              <option>Sent home</option>
              <option>Referred to hospital</option>
            </select></div>
          <div class="field"><label class="req">Date</label><input id="issDate" type="date" value="${today()}" required></div>
          <div class="field span-4"><label>Remarks</label><textarea id="issRemarks" maxlength="500"></textarea></div>
        </div>
        <div class="divider"></div>
        <div class="form-grid">
          <div class="field span-2"><label class="req">Item</label><select id="issItem">${items.map((i) => `<option value="${i.id}">${esc(i.code)} · ${esc(i.name)} · ${stockFor(i.id)} ${esc(i.unitOfMeasure)} available</option>`).join("")}</select></div>
          <div class="field"><label class="req">Quantity / Unit</label>
            <div class="inline-pair"><input id="issQty" type="number" min="1" value="1"><input id="issUom" disabled></div>
          </div>
          <div class="field"><button type="button" class="btn primary" id="addIssLine" style="margin-top:20px">Add item</button></div>
        </div>
        <div id="issueDraft" style="margin-top:14px"></div>
        <div class="actions" style="margin-top:14px"><button class="btn primary">Record issuance</button><button type="button" class="btn" id="clearIss">Clear draft</button></div>
      </form>
    </div>
  </div>`;
  const sync = () => {
    $("#issUom").value =
      items.find((i) => i.id == $("#issItem").value)?.unitOfMeasure || "";
  };
  $("#issItem").onchange = sync;
  sync();
  renderIssueDraft();
  $("#addIssLine").onclick = () => {
    const i = items.find((x) => x.id == $("#issItem").value),
      q = Number($("#issQty").value);
    if (!i || !Number.isInteger(q) || q < 1)
      return toast("Select an item and valid quantity", "error");
    const found = app.draftIssuance.find((x) => x.itemId === i.id);
    if (found) found.quantity += q;
    else
      app.draftIssuance.push({
        itemId: i.id,
        itemName: i.name,
        itemCode: i.code,
        unitOfMeasure: i.unitOfMeasure,
        quantity: q,
      });
    $("#issQty").value = 1;
    renderIssueDraft();
  };
  $("#clearIss").onclick = () => {
    app.draftIssuance = [];
    renderIssueDraft();
  };
  $("#issuanceForm").onsubmit = async (e) => {
    e.preventDefault();
    if (!app.draftIssuance.length)
      return toast("Add at least one issued item", "error");
    const req = {
      dateIssued: $("#issDate").value,
      employeeNumber: $("#issEmployeeNo").value.trim(),
      employeeName: $("#issEmployeeName").value.trim(),
      department: $("#issDepartment").value.trim() || null,
      supervisor: $("#issSupervisor").value.trim() || null,
      chiefComplaint: $("#issComplaint").value.trim(),
      disposition: $("#issDisposition").value,
      remarks: $("#issRemarks").value.trim() || null,
      items: app.draftIssuance.map((x) => ({
        itemId: x.itemId,
        quantity: x.quantity,
      })),
    };
    try {
      await api("/api/issuances", {
        method: "POST",
        body: JSON.stringify(req),
      });
      app.draftIssuance = [];
      toast("Issuance recorded in MySQL");
      await navigate("Issuance Records");
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
function renderIssueDraft() {
  const host = $("#issueDraft");
  if (!host) return;
  host.innerHTML = tableMarkup({
    id: "issDraft",
    search: false,
    columns: [
      { label: "Item", key: "itemName", width: 240 },
      { label: "Quantity", key: "quantity", type: "number", width: 90 },
      { label: "Unit", key: "unitOfMeasure", width: 90 },
      {
        label: "",
        sortable: false,
        width: 80,
        render: (r) =>
          `<button class="btn small danger remove-iss" data-id="${r.itemId}">Remove</button>`,
      },
    ],
    rows: app.draftIssuance,
  });
  initTables(host);
  $$(".remove-iss", host).forEach(
    (b) =>
      (b.onclick = () => {
        app.draftIssuance = app.draftIssuance.filter(
          (x) => x.itemId != b.dataset.id,
        );
        renderIssueDraft();
      }),
  );
}

async function pageIssuanceRecords(root) {
  const rows = await fetchAll("/api/issuances", { sort: "dateIssued,desc" });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Issuance Records</h2></div>${tableMarkup(
    {
      id: "issuanceRecords",
      searchFields: [
        "referenceNumber",
        "employeeNumber",
        "employeeName",
        "department",
        "chiefComplaint",
      ],
      columns: [
        { label: "Reference", key: "referenceNumber", width: 170 },
        {
          label: "Date",
          key: "dateIssued",
          type: "date",
          width: 115,
          render: (r) => fmtDate(r.dateIssued),
        },
        {
          label: "Employee",
          key: "employeeName",
          width: 200,
          render: (r) =>
            `<b>${esc(r.employeeName)}</b><div class="small muted mono">${esc(r.employeeNumber)}</div>`,
        },
        { label: "Department", key: "department", width: 150 },
        {
          label: "Items",
          key: "lines",
          width: 300,
          render: (r) =>
            r.lines
              .map(
                (l) =>
                  `${esc(l.itemName)} × ${l.quantity} ${esc(l.unitOfMeasure)}`,
              )
              .join("<br>"),
        },
        {
          label: "Actions",
          sortable: false,
          width: 90,
          render: (r) =>
            `<button class="btn small edit-issuance" data-id="${r.id}">Edit</button>`,
        },
      ],
      rows,
    },
  )}</div>`;
  $$(".edit-issuance").forEach(
    (b) =>
      (b.onclick = () =>
        openIssuanceEdit(rows.find((r) => r.id == b.dataset.id))),
  );
}
function openIssuanceEdit(r) {
  let lines = r.lines.map((x) => ({ ...x }));
  const body = modal(
    `Edit issuance · ${r.referenceNumber}`,
    `<form id="editIssForm"><div class="form-grid"><div class="field"><label class="req">Date</label><input id="eiDate" type="date" value="${r.dateIssued}" required></div><div class="field"><label class="req">Employee number</label><input id="eiNo" value="${esc(r.employeeNumber)}" required></div><div class="field"><label class="req">Employee name</label><input id="eiName" value="${esc(r.employeeName)}" required></div><div class="field"><label>Department</label><input id="eiDept" value="${esc(r.department || "")}"></div><div class="field"><label>Supervisor</label><input id="eiSup" value="${esc(r.supervisor || "")}"></div><div class="field span-2"><label class="req">Chief complaint</label><input id="eiComplaint" value="${esc(r.chiefComplaint)}" required></div><div class="field"><label class="req">Disposition</label><select id="eiDisp">${["Returned to work", "Sent home", "Referred to hospital"].map((x) => `<option ${x === r.disposition ? "selected" : ""}>${x}</option>`).join("")}</select></div><div class="field span-4"><label>Remarks</label><textarea id="eiRemarks">${esc(r.remarks || "")}</textarea></div></div><div id="editIssLines" style="margin-top:14px"></div><div class="modal-actions"><button type="button" class="btn" id="eiCancel">Cancel</button><button class="btn primary" id="eiSave" disabled>Save changes</button></div></form>`,
    { wide: true },
  );
  const initialLinesState = JSON.stringify(
      lines.map((l) => ({ id: l.id, quantity: Number(l.quantity) }))
    );

  const render = () => {
    $("#editIssLines", body).innerHTML = tableMarkup({
      id: "editIssLinesTable",
      search: false,
      columns: [
        { label: "Item", key: "itemName", width: 240 },
        { label: "Batch", key: "batchNumber", width: 140 },
        {
          label: "Quantity / Unit",
          key: "quantity",
          width: 160,
          render: (l) =>
            `<div class="inline-pair"><input class="lineQty" data-id="${l.id}" type="number" min="1" value="${l.quantity}"><input disabled value="${esc(l.unitOfMeasure)}"></div>`,
        },
        {
          label: "",
          sortable: false,
          width: 80,
          render: (l) =>
            `<button type="button" class="btn small danger remove-line" data-id="${l.id}">Remove</button>`,
        },
      ],
      rows: lines,
    });
    initTables($("#editIssLines", body));
    $$(".remove-line", body).forEach(
      (b) =>
        (b.onclick = () => {
          if (lines.length <= 1)
            return toast("An issuance must contain at least one item", "error");
          lines = lines.filter((x) => x.id != b.dataset.id);
          render();
		  checkChanges();
        }),
    );
  };
  render();
  const formControls = Array.from(
      $("#editIssForm", body).querySelectorAll("input, select, textarea")
    );
    const initialFormValues = new Map(formControls.map((input) => [input, input.value]));
    const checkChanges = () => {
      const formHasChanged = formControls.some(
        (input) => input.value !== initialFormValues.get(input)
      );

      $$(".lineQty", body).forEach((inp) => {
        const l = lines.find((x) => x.id == inp.dataset.id);
        if (l) l.quantity = Number(inp.value);
      });

      const currentLinesState = JSON.stringify(
        lines.map((l) => ({ id: l.id, quantity: Number(l.quantity) }))
      );
      const linesHaveChanged = currentLinesState !== initialLinesState;

      $("#eiSave", body).disabled = !(formHasChanged || linesHaveChanged);
    };

    $("#editIssForm", body).addEventListener("input", checkChanges);
    $("#editIssForm", body).addEventListener("change", checkChanges);
    checkChanges();
  $("#eiCancel", body).onclick = closeModal;
  $("#editIssForm", body).onsubmit = async (e) => {
    e.preventDefault();
    $$(".lineQty", body).forEach((inp) => {
      const l = lines.find((x) => x.id == inp.dataset.id);
      if (l) l.quantity = Number(inp.value);
    });
    const req = {
      dateIssued: $("#eiDate").value,
      employeeNumber: $("#eiNo").value.trim(),
      employeeName: $("#eiName").value.trim(),
      department: $("#eiDept").value.trim() || null,
      supervisor: $("#eiSup").value.trim() || null,
      chiefComplaint: $("#eiComplaint").value.trim(),
      disposition: $("#eiDisp").value,
      remarks: $("#eiRemarks").value.trim() || null,
      lines: lines.map((l) => ({ batchId: l.batchId, quantity: l.quantity })),
    };
    try {
      await api(`/api/issuances/${r.id}`, {
        method: "PUT",
        body: JSON.stringify(req),
      });
      closeModal();
      toast("Issuance updated in MySQL");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageBatches(root) {
  const rows = await fetchAll("/api/batches", { sort: "batchNumber,asc" });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Batch Records</h2></div>${tableMarkup(
    {
      id: "batches",
      searchFields: [
        "itemCode",
        "itemName",
        "batchNumber",
        "brand",
        "location",
        "status",
      ],
      filters: [
        {
          key: "status",
          allLabel: "All statuses",
          options: ["ACTIVE", "DEPLETED", "DISPOSED"].map((x) => ({
            value: x,
            label: labelize(x),
          })),
        },
      ],
      columns: [
        {
          label: "Item",
          key: "itemName",
          width: 220,
          render: (r) =>
            `<b>${esc(r.itemName)}</b><div class="small muted mono">${esc(r.itemCode)}</div>`,
        },
        { label: "Batch", key: "batchNumber", width: 140 },
        { label: "Brand", key: "brand", width: 140 },
        {
          label: "Expiry",
          key: "expiryDate",
          type: "date",
          width: 115,
          render: (r) => fmtDate(r.expiryDate),
        },
        { label: "On Hand", key: "onHand", type: "number", width: 90 },
        { label: "Unit", key: "unitOfMeasure", width: 90 },
        { label: "Location", key: "location", width: 120 },
        {
          label: "Status",
          key: "status",
          width: 100,
          render: (r) => badge(r.status),
        },
        {
          label: "Actions",
          sortable: false,
          width: 100,
          render: (r) =>
            hasPerm("DISPOSAL") && r.status === "ACTIVE"
              ? `<button class="btn small danger dispose-batch" data-id="${r.id}">Dispose</button>`
              : '<span class="small muted">View only</span>',
        },
      ],
      rows,
    },
  )}</div>`;
  $$(".dispose-batch").forEach(
    (b) =>
      (b.onclick = () =>
        openBatchDisposal(rows.find((r) => r.id == b.dataset.id))),
  );
}
function openBatchDisposal(r) {
  const body = modal(
    `Dispose stock · ${r.itemName}`,
    `<form id="disposeBatchForm"><div class="form-grid"><div class="field"><label>Batch</label><input value="${esc(r.batchNumber || "—")}" disabled></div><div class="field"><label>Available</label><input value="${r.onHand} ${esc(r.unitOfMeasure)}" disabled></div><div class="field"><label class="req">Quantity</label><input id="dbQty" type="number" min="1" max="${r.onHand}" value="1"></div><div class="field"><label class="req">Reason</label><input id="dbReason" maxlength="180" required></div><div class="field span-4"><label>Remarks</label><textarea id="dbRemarks" maxlength="500"></textarea></div></div><div class="modal-actions"><button type="button" class="btn" id="dbCancel">Cancel</button><button class="btn danger">Record disposal</button></div></form>`,
  );
  $("#dbCancel", body).onclick = closeModal;
  $("#disposeBatchForm", body).onsubmit = async (e) => {
    e.preventDefault();
    try {
      await api("/api/disposals/batch", {
        method: "POST",
        body: JSON.stringify({
          batchId: r.id,
          quantity: Number($("#dbQty").value),
          reason: $("#dbReason").value.trim(),
          remarks: $("#dbRemarks").value.trim() || null,
        }),
      });
      closeModal();
      toast("Disposal recorded");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageEquipment(root) {
  const rows = await fetchAll("/api/equipment", { sort: "assetTag,asc" });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Equipment Unit Records</h2></div>${tableMarkup(
    {
      id: "equipment",
      searchFields: [
        "itemCode",
        "equipmentName",
        "assetTag",
        "serialNumber",
        "brand",
        "model",
        "location",
        "status",
      ],
      columns: [
        {
          label: "Equipment",
          key: "equipmentName",
          width: 220,
          render: (r) =>
            `<b>${esc(r.equipmentName)}</b><div class="small muted mono">${esc(r.itemCode)}</div>`,
        },
        { label: "Asset tag", key: "assetTag", width: 135 },
        { label: "Serial number", key: "serialNumber", width: 145 },
        {
          label: "Brand / Model",
          key: "brand",
          width: 190,
          render: (r) =>
            `${esc(r.brand || "—")}<div class="small muted">${esc(r.model || "")}</div>`,
        },
        { label: "Location", key: "location", width: 120 },
        {
          label: "Acquired",
          key: "acquiredDate",
          type: "date",
          width: 115,
          render: (r) => fmtDate(r.acquiredDate),
        },
        {
          label: "Status",
          key: "status",
          width: 105,
          render: (r) => badge(r.status),
        },
        {
          label: "Actions",
          sortable: false,
          width: 180,
          render: (r) =>
            `<div class="actions">${app.session.role !== "Supervisor" && r.status !== "DISPOSED" ? `<button class="btn small edit-equipment" data-id="${r.id}">Edit status</button>` : ""}${hasPerm("DISPOSAL") && r.status !== "DISPOSED" ? `<button class="btn small danger dispose-equipment" data-id="${r.id}">Dispose</button>` : ""}</div>`,
        },
      ],
      rows,
    },
  )}</div>`;
  $$(".edit-equipment").forEach(
    (b) =>
      (b.onclick = () =>
        openEquipmentStatus(rows.find((r) => r.id == b.dataset.id))),
  );
  $$(".dispose-equipment").forEach(
    (b) =>
      (b.onclick = () =>
        openEquipmentDisposal(rows.find((r) => r.id == b.dataset.id))),
  );
}
function openEquipmentStatus(r) {
  const body = modal(
    `Edit equipment status · ${r.assetTag}`,
    `<form id="eqStatusForm"><div class="form-grid"><div class="field span-2"><label>Equipment</label><input value="${esc(r.equipmentName)}" disabled></div><div class="field"><label>Asset tag</label><input value="${esc(r.assetTag)}" disabled></div><div class="field"><label>Serial number</label><input value="${esc(r.serialNumber)}" disabled></div><div class="field"><label>Brand</label><input value="${esc(r.brand || "")}" disabled></div><div class="field"><label>Model</label><input value="${esc(r.model || "")}" disabled></div><div class="field"><label>Location</label><input value="${esc(r.location)}" disabled></div><div class="field"><label class="req">Status</label><select id="eqStatus">${["IN_USE", "MAINTENANCE", "RETIRED"].map((x) => `<option ${x === r.status ? "selected" : ""}>${x}</option>`).join("")}</select></div><div class="field span-4"><label class="req">Adjustment reason</label><textarea id="eqReason" required></textarea></div></div><div class="modal-actions"><button type="button" class="btn" id="eqCancel">Cancel</button><button class="btn primary">Save status</button></div></form>`,
  );
  $("#eqCancel", body).onclick = closeModal;
  $("#eqStatusForm", body).onsubmit = async (e) => {
    e.preventDefault();
    try {
      await api(`/api/equipment/${r.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          status: $("#eqStatus").value,
          reason: $("#eqReason").value.trim(),
        }),
      });
      closeModal();
      toast("Equipment status updated");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
function openEquipmentDisposal(r) {
  const body = modal(
    `Dispose equipment · ${r.assetTag}`,
    `<form id="disposeEqForm"><div class="field"><label class="req">Reason</label><input id="deReason" maxlength="180" required></div><div class="field" style="margin-top:10px"><label>Remarks</label><textarea id="deRemarks" maxlength="500"></textarea></div><div class="modal-actions"><button type="button" class="btn" id="deCancel">Cancel</button><button class="btn danger">Record disposal</button></div></form>`,
  );
  $("#deCancel", body).onclick = closeModal;
  $("#disposeEqForm", body).onsubmit = async (e) => {
    e.preventDefault();
    try {
      await api("/api/disposals/equipment", {
        method: "POST",
        body: JSON.stringify({
          equipmentUnitId: r.id,
          reason: $("#deReason").value.trim(),
          remarks: $("#deRemarks").value.trim() || null,
        }),
      });
      closeModal();
      toast("Equipment disposed");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageDisposals(root) {
  const rows = await fetchAll("/api/disposals", { sort: "disposalDate,desc" });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Disposal History</h2></div>${tableMarkup(
    {
      id: "disposals",
      searchFields: ["referenceNumber", "itemName", "reason", "recordedBy"],
      columns: [
        { label: "Reference", key: "referenceNumber", width: 170 },
        {
          label: "Date",
          key: "disposalDate",
          type: "date",
          width: 115,
          render: (r) => fmtDate(r.disposalDate),
        },
        { label: "Item", key: "itemName", width: 220 },
        { label: "Quantity", key: "quantity", type: "number", width: 90 },
        { label: "Reason", key: "reason", width: 240 },
        { label: "Remarks", key: "remarks", width: 260 },
        { label: "Recorded by", key: "recordedBy", width: 160 },
      ],
      rows,
    },
  )}</div>`;
}

async function pageSuppliers(root) {
  const rows = await fetchAll("/api/suppliers", { sort: "name,asc" });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Suppliers</h2><button class="btn primary" id="addSupplier">New Supplier</button></div>${tableMarkup(
    {
      id: "suppliers",
      searchFields: ["name", "contactPerson", "contactNo", "address"],
      columns: [
        { label: "Supplier", key: "name", width: 230 },
        { label: "Contact Person", key: "contactPerson", width: 180 },
        { label: "Contact Number", key: "contactNo", width: 150 },
        { label: "Address", key: "address", width: 270 },
        {
          label: "Actions",
          sortable: false,
          width: 190,
          render: (r) =>
            `<div class="actions"><button class="btn small edit-supplier" data-id="${r.id}">Edit</button>${r.active ? `<button class="btn small danger delete-supplier" data-id="${r.id}">Delete</button>` : `<button class="btn small primary reactivate-supplier" data-id="${r.id}">Reactivate</button>`}</div>`,
        },
      ],
      rows,
    },
  )}</div>`;
  $("#addSupplier").onclick = () => openSupplierForm(null);
  $$(".edit-supplier").forEach(
    (b) =>
      (b.onclick = () =>
        openSupplierForm(rows.find((r) => r.id == b.dataset.id))),
  );
  $$(".delete-supplier").forEach(
    (b) =>
      (b.onclick = () =>
        confirmModal(
          "Delete supplier",
          "Supplier deletion is soft-delete only. The server will block deletion when protected receiving or active-item history exists.",
          async () => {
            await api(`/api/suppliers/${b.dataset.id}`, { method: "DELETE" });
            toast("Supplier deactivated");
            await renderPage();
          },
        )),
  );
  $$(".reactivate-supplier").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          await api(`/api/suppliers/${b.dataset.id}/reactivate`, {
            method: "POST",
          });
          toast("Supplier reactivated");
          await renderPage();
        } catch (e) {
          toast(e.message, "error");
        }
      }),
  );
}
function openSupplierForm(supplier, onCreated = null) {
  const edit = !!supplier,
    body = modal(
      edit ? "Edit supplier" : "New supplier",
      `<form id="supplierForm"><div class="form-grid"><div class="field span-2"><label class="req">Supplier name</label><input id="supName" value="${esc(supplier?.name || "")}" required></div><div class="field"><label>Contact person</label><input id="supPerson" value="${esc(supplier?.contactPerson || "")}"></div><div class="field"><label>Contact number</label><input id="supNo" value="${esc(supplier?.contactNo || "")}"></div><div class="field span-4"><label>Address</label><textarea id="supAddress">${esc(supplier?.address || "")}</textarea></div></div><div class="modal-actions"><button type="button" class="btn" id="supCancel">Cancel</button><button class="btn primary">Save supplier</button></div></form>`,
    );
  $("#supCancel", body).onclick = closeModal;
  $("#supplierForm", body).onsubmit = async (e) => {
    e.preventDefault();
    const req = {
      name: $("#supName").value.trim(),
      contactPerson: $("#supPerson").value.trim() || null,
      contactNo: $("#supNo").value.trim() || null,
      address: $("#supAddress").value.trim() || null,
    };
    try {
      const saved = await api(
        edit ? `/api/suppliers/${supplier.id}` : "/api/suppliers",
        { method: edit ? "PUT" : "POST", body: JSON.stringify(req) },
      );
      closeModal();
      toast(edit ? "Supplier updated" : "Supplier created");
      if (onCreated) await onCreated(saved);
      else await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageUsers(root) {
  const [users, roles] = await Promise.all([
    fetchAll("/api/users", { sort: "email,asc" }),
    fetchAll("/api/roles", { sort: "name,asc" }),
  ]);
  root.innerHTML = `<div class="card"><div class="card-head"><h2>User Accounts</h2><button class="btn primary" id="addUser">Create New User</button></div>${tableMarkup(
    {
      id: "users",
      searchFields: ["email", "fullName", "roleName"],
      columns: [
        { label: "Full Name", key: "fullName", width: 220 },
        { label: "Email", key: "email", width: 240 },
        { label: "Role", key: "roleName", width: 160 },
        {
          label: "Status",
          key: "active",
          width: 100,
          render: (r) => badge(r.active ? "ACTIVE" : "INACTIVE"),
        },
        {
          label: "Actions",
          sortable: false,
          width: 180,
          render: (r) =>
            `<div class="actions"><button class="btn small edit-user" data-id="${r.id}">Edit</button><button class="btn small ${r.active ? "danger" : "primary"} toggle-user" data-id="${r.id}" data-active="${r.active}">${r.active ? "Deactivate" : "Activate"}</button></div>`,
        },
      ],
      rows: users,
    },
  )}</div>`;
  $("#addUser").onclick = () => openUserForm(null, roles);
  $$(".edit-user").forEach(
    (b) =>
      (b.onclick = () =>
        openUserForm(
          users.find((r) => r.id == b.dataset.id),
          roles,
        )),
  );
  $$(".toggle-user").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          await api(
            `/api/users/${b.dataset.id}/active?active=${b.dataset.active !== "true"}`,
            { method: "PATCH" },
          );
          toast("User status updated");
          await renderPage();
        } catch (e) {
          toast(e.message, "error");
        }
      }),
  );
}
function openUserForm(user, roles) {
  const edit = !!user,
    body = modal(
      edit ? "Edit User" : "Create New User",
      `<form id="userForm"><div class="form-grid"><div class="field span-2"><label class="req">Full Name</label><input id="usrName" value="${esc(user?.fullName || "")}" required></div><div class="field span-2"><label class="req">Email</label><input id="usrEmail" type="email" value="${esc(user?.email || "")}" required></div><div class="field"><label class="req">Role</label><select id="usrRole">${optionList(roles, "id", "name", user?.roleId)}</select></div><div class="field span-2"><label class="${edit ? "" : "req"}">${edit ? "New password (optional)" : "Temporary password"}</label><input id="usrPassword" type="password" ${edit ? "" : "required"} minlength="8"></div><div class="field"><label>Status</label><select id="usrActive"><option value="true" ${user?.active !== false ? "selected" : ""}>Active</option><option value="false" ${user?.active === false ? "selected" : ""}>Inactive</option></select></div></div><div class="modal-actions"><button type="button" class="btn" id="usrCancel">Cancel</button><button class="btn primary">Save user</button></div></form>`,
    );
  $("#usrCancel", body).onclick = closeModal;
  $("#userForm", body).onsubmit = async (e) => {
    e.preventDefault();
    const common = {
        email: $("#usrEmail").value.trim(),
        fullName: $("#usrName").value.trim(),
        roleId: Number($("#usrRole").value),
        active: $("#usrActive").value === "true",
      },
      pass = $("#usrPassword").value;
    const req = edit
      ? { ...common, password: pass || null }
      : { ...common, temporaryPassword: pass };
    try {
      await api(edit ? `/api/users/${user.id}` : "/api/users", {
        method: edit ? "PUT" : "POST",
        body: JSON.stringify(req),
      });
      closeModal();
      toast(edit ? "User updated" : "User created");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageRoles(root) {
  const [roles, permissions] = await Promise.all([
    fetchAll("/api/roles", { sort: "name,asc" }),
    api("/api/roles/permissions"),
  ]);
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Roles & Permissions</h2><button class="btn primary" id="addRole">Create New Role</button></div>${tableMarkup(
    {
      id: "roles",
      searchFields: ["name", "description"],
      columns: [
        { label: "Role", key: "name", width: 180 },
        { label: "Description", key: "description", width: 300 },
        {
          label: "Permissions",
          key: "permissionCodes",
          width: 420,
          render: (r) =>
            [...r.permissionCodes]
              .sort()
              .map((p) => badge(p))
              .join(" "),
        },
        {
          label: "Assigned Users",
          key: "activeUserCount",
          type: "number",
          width: 120,
        },
        {
          label: "Status",
          key: "active",
          width: 100,
          render: (r) => badge(r.active ? "ACTIVE" : "INACTIVE"),
        },
        {
          label: "Actions",
          sortable: false,
          width: 160,
          render: (r) =>
            `<div class="actions"><button class="btn small edit-role" data-id="${r.id}">Edit</button><button class="btn small ${r.active ? "danger" : "primary"} toggle-role" data-id="${r.id}" data-active="${r.active}">${r.active ? "Deactivate" : "Activate"}</button></div>`,
        },
      ],
      rows: roles,
    },
  )}</div>`;
  $("#addRole").onclick = () => openRoleForm(null, permissions);
  $$(".edit-role").forEach(
    (b) =>
      (b.onclick = () =>
        openRoleForm(
          roles.find((r) => r.id == b.dataset.id),
          permissions,
        )),
  );
  $$(".toggle-role").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          await api(
            `/api/roles/${b.dataset.id}/active?active=${b.dataset.active !== "true"}`,
            { method: "PATCH" },
          );
          await refreshSession();
          toast("Role status updated");
          await renderPage();
        } catch (e) {
          toast(e.message, "error");
        }
      }),
  );
}
function openRoleForm(role, permissions) {
  const edit = !!role,
    selected = new Set(role?.permissionCodes || []),
    groups = {
      Operations: [
        "RECEIVING",
        "ISSUANCE",
        "BATCHES",
        "EQUIPMENT",
        "DISPOSAL",
        "SUPPLIERS",
      ],
      Oversight: ["APPROVALS", "REPORTS", "TRANSACTION_LOG"],
      Administration: ["ITEMS", "USERS", "ROLES", "SETTINGS", "LOCATIONS"],
    };
  const body = modal(
    edit ? "Edit Role" : "Create New Role",
    `<form id="roleForm"><div class="form-grid"><div class="field span-2"><label class="req">Role Name</label><input id="roleName" value="${esc(role?.name || "")}" required></div><div class="field span-2"><label class="req">Description</label><input id="roleDescription" value="${esc(role?.description || "")}" required></div><div class="field"><label>Status</label><select id="roleActive"><option value="true" ${role?.active !== false ? "selected" : ""}>Active</option><option value="false" ${role?.active === false ? "selected" : ""}>Inactive</option></select></div><div class="field span-4"><div class="actions"><button type="button" class="btn small" id="permAll">Select all</button><button type="button" class="btn small" id="permNone">Clear</button><span class="small muted" id="permCount"></span></div><div class="permission-groups" style="margin-top:8px">${Object.entries(
      groups,
    )
      .map(
        ([g, codes]) =>
          `<div class="permission-group"><h4>${esc(g)}</h4><div class="permission-grid">${codes
            .filter((c) => permissions.includes(c))
            .map((code) => {
              const [name, desc] = PERMISSION_DESCRIPTIONS[code] || [code, ""];
              return `<label class="permission-card"><input type="checkbox" class="perm-check" value="${code}" ${selected.has(code) ? "checked" : ""}><div><b>${esc(name)}</b><span>${esc(desc)}</span></div></label>`;
            })
            .join("")}</div></div>`,
      )
      .join(
        "",
      )}</div></div></div><div class="modal-actions"><button type="button" class="btn" id="roleCancel">Cancel</button><button class="btn primary">Save role</button></div></form>`,
    { wide: true },
  );
  const update = () =>
    ($("#permCount", body).textContent =
      `${$$(".perm-check:checked", body).length} permission(s) selected`);
  update();
  $$(".perm-check", body).forEach((x) => (x.onchange = update));
  $("#permAll", body).onclick = () => {
    $$(".perm-check", body).forEach((x) => (x.checked = true));
    update();
  };
  $("#permNone", body).onclick = () => {
    $$(".perm-check", body).forEach((x) => (x.checked = false));
    update();
  };
  $("#roleCancel", body).onclick = closeModal;
  $("#roleForm", body).onsubmit = async (e) => {
    e.preventDefault();
    const req = {
      name: $("#roleName").value.trim(),
      description: $("#roleDescription").value.trim(),
      active: $("#roleActive").value === "true",
      permissionCodes: $$(".perm-check:checked", body).map((x) => x.value),
    };
    try {
      await api(edit ? `/api/roles/${role.id}` : "/api/roles", {
        method: edit ? "PUT" : "POST",
        body: JSON.stringify(req),
      });
      closeModal();
      await refreshSession();
      toast(edit ? "Role updated; permissions refreshed" : "Role created");
      if (!allowedPages().some((x) => x[0] === app.page))
        app.page = "Dashboard";
      renderNav();
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
function addReference(path, label) {
  const body = modal(
    `Add ${label}`,
    `<form id="refForm"><div class="field"><label class="req">${esc(label)}</label><input id="refName" required></div><div class="modal-actions"><button type="button" class="btn" id="refCancel">Cancel</button><button class="btn primary">Add</button></div></form>`,
  );
  $("#refCancel", body).onclick = closeModal;
  $("#refForm", body).onsubmit = async (e) => {
    e.preventDefault();
    try {
      await api(path, {
        method: "POST",
        body: JSON.stringify({ name: $("#refName").value.trim() }),
      });
      closeModal();
      toast(`${label} added`);
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
function editReference(path, label, item, onSaved) {
  const body = modal(
    `Edit ${label}`,
    `<form id="refEditForm"><div class="field"><label class="req">${esc(label)}</label><input id="refEditName" value="${esc(item.name)}" required></div><div class="modal-actions"><button type="button" class="btn" id="refEditCancel">Cancel</button><button class="btn primary">Save</button></div></form>`,
  );
  $("#refEditCancel", body).onclick = closeModal;
  $("#refEditForm", body).onsubmit = async (e) => {
    e.preventDefault();
    try {
      await api(`${path}/${item.id}`, {
        method: "PUT",
        body: JSON.stringify({ name: $("#refEditName").value.trim() }),
      });
      closeModal();
      toast(`${label} updated`);
      await onSaved();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}
async function pageSettings(root) {
  const refs = await loadReferences(),
    canSettings = hasPerm("SETTINGS"),
    canLocations = hasPerm("LOCATIONS");
  let near = null,
    items = [];
  if (canSettings) near = await api("/api/settings/near-expiry-days");
  if (canSettings && hasPerm("ITEMS"))
    items = await fetchAll("/api/items", { sort: "code,asc" });
  root.innerHTML = `<div class="stack">${canSettings ? `<div class="card"><div class="card-head"><h2>Global Medicine Near-Expiry Setting</h2></div><div class="card-body"><form id="nearForm"><div class="form-grid"><div class="field"><label class="req">Near-expiry days</label><input id="nearDays" type="number" min="0" max="3650" value="${near.days}"></div><div class="field span-3"><div class="notice">This single value applies to every medicine batch.</div></div><div class="field span-4"><button class="btn primary">Save setting</button></div></div></form></div></div>` : ""}<div class="grid ${canSettings ? "cols-2" : ""}">${
    canSettings
      ? `<div class="card"><div class="card-head"><h2>Units of Measurement</h2><button class="btn primary small" id="addUom">Add UoM</button></div>${tableMarkup(
          {
            id: "uoms",
            searchFields: ["name"],
            columns: [
              { label: "Unit of Measurement", key: "name", width: 300 },
              {
                label: "",
                sortable: false,
                width: 90,
                render: (r) =>
                  `<button class="btn small edit-uom" data-id="${r.id}">Edit</button>`,
              },
            ],
            rows: refs.uoms,
          },
        )}</div>`
      : ""
  }${
    canLocations
      ? `<div class="card"><div class="card-head"><h2>Locations</h2><button class="btn primary small" id="addLocation">Add Location</button></div>${tableMarkup(
          {
            id: "locations",
            searchFields: ["name"],
            columns: [
              { label: "Location", key: "name", width: 300 },
              {
                label: "",
                sortable: false,
                width: 90,
                render: (r) =>
                  `<button class="btn small edit-location" data-id="${r.id}">Edit</button>`,
              },
            ],
            rows: refs.locations,
          },
        )}</div>`
      : ""
  }</div>${
    canSettings && hasPerm("ITEMS")
      ? `<div class="card"><div class="card-head"><h2>Item Reorder Settings</h2></div>${tableMarkup(
          {
            id: "reorder",
            searchFields: ["code", "name"],
            columns: [
              { label: "Code", key: "code", width: 130 },
              { label: "Item", key: "name", width: 240 },
              {
                label: "Reorder Level",
                key: "reorderLevel",
                type: "number",
                width: 130,
              },
              {
                label: "Reorder Quantity",
                key: "reorderQuantity",
                type: "number",
                width: 140,
              },
              {
                label: "",
                sortable: false,
                width: 100,
                render: (r) =>
                  `<button class="btn small edit-reorder" data-id="${r.id}">Edit</button>`,
              },
            ],
            rows: items.filter((i) => i.category !== "EQUIPMENT"),
          },
        )}</div>`
      : ""
  }</div>`;
  if (canSettings) {
    $("#nearForm").onsubmit = async (e) => {
      e.preventDefault();
      try {
        await api("/api/settings/near-expiry-days", {
          method: "PUT",
          body: JSON.stringify({ days: Number($("#nearDays").value) }),
        });
        toast("Near-expiry setting saved");
        await renderPage();
      } catch (err) {
        toast(err.message, "error");
      }
    };
    $("#addUom").onclick = () =>
      addReference("/api/settings/units-of-measure", "Unit of Measurement");
  }
  if (canLocations && $("#addLocation"))
    $("#addLocation").onclick = () =>
      addReference("/api/settings/locations", "Location");
  $$(".edit-uom").forEach(
    (b) =>
      (b.onclick = () =>
        editReference(
          "/api/settings/units-of-measure",
          "Unit of Measurement",
          refs.uoms.find((u) => u.id == b.dataset.id),
          renderPage,
        )),
  );
  $$(".edit-location").forEach(
    (b) =>
      (b.onclick = () =>
        editReference(
          "/api/settings/locations",
          "Location",
          refs.locations.find((l) => l.id == b.dataset.id),
          renderPage,
        )),
  );
  $$(".edit-reorder").forEach(
    (b) =>
      (b.onclick = () =>
        openReorder(
          items.find((i) => i.id == b.dataset.id),
          refs,
        )),
  );
}
function openReorder(item, refs) {
  const body = modal(
    `Edit reorder settings · ${item.name}`,
    `<form id="reorderForm"><div class="form-grid"><div class="field"><label class="req">Reorder Level</label><input id="rrLevel" type="number" min="0" max="100" value="${item.reorderLevel}"></div><div class="field"><label class="req">Reorder Quantity</label><input id="rrQty" type="number" min="1" max="500" value="${item.reorderQuantity}"></div></div><div class="modal-actions"><button type="button" class="btn" id="rrCancel">Cancel</button><button class="btn primary">Save</button></div></form>`,
  );
  $("#rrCancel", body).onclick = closeModal;
  $("#reorderForm", body).onsubmit = async (e) => {
    e.preventDefault();

    const reorderQuantity = Number($("#rrQty").value);

    if (
      !Number.isInteger(reorderQuantity) ||
      reorderQuantity < 1 ||
      reorderQuantity > 500
    ) {
      return toast("Reorder quantity must be between 1 and 500", "error");
    }

    try {
      await api(`/api/items/${item.id}`, {
        method: "PUT",
        body: JSON.stringify({
          code: item.code,
          name: item.name,
          category: item.category,
          unitOfMeasureId: item.unitOfMeasureId,
          reorderLevel: Number($("#rrLevel").value),
          reorderQuantity: Number($("#rrQty").value),
        }),
      });
      closeModal();
      toast("Reorder settings updated");
      await renderPage();
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

async function pageLogs(root) {
  const rows = await fetchAll("/api/transaction-logs", {
    sort: "transactionDate,desc",
  });
  root.innerHTML = `<div class="card"><div class="card-head"><h2>Transaction Log</h2></div>${tableMarkup(
    {
      id: "logs",
      searchFields: ["referenceNumber", "user", "itemName", "detail"],
      filters: [
        {
          key: "transactionType",
          allLabel: "All transaction types",
          options: ["ADJUSTMENT", "DISPOSAL", "ISSUANCE", "RECEIVING"].map(
            (x) => ({ value: x, label: labelize(x) }),
          ),
        },
        {
          key: "itemCategory",
          allLabel: "All item types",
          options: ["MEDICINE", "SUPPLY", "EQUIPMENT"].map((x) => ({
            value: x,
            label: labelize(x),
          })),
        },
      ],
      columns: [
        {
          label: "Date",
          key: "transactionDate",
          type: "date",
          width: 160,
          render: (r) => fmtDateTime(r.transactionDate),
        },
        {
          label: "Type",
          key: "transactionType",
          width: 110,
          render: (r) => badge(r.transactionType),
        },
        { label: "Reference", key: "referenceNumber", width: 160 },
        { label: "Item", key: "itemName", width: 190 },
        {
          label: "Before",
          key: "quantityBefore",
          type: "number",
          width: 80,
          render: (r) => r.quantityBefore ?? "—",
        },
        {
          label: "After",
          key: "quantityAfter",
          type: "number",
          width: 80,
          render: (r) => r.quantityAfter ?? "—",
        },
        { label: "User", key: "user", width: 160 },
        { label: "Activity", key: "detail", width: 420 },
      ],
      rows,
    },
  )}</div>`;
}

async function pageReports(root) {
  const [history, refs] = await Promise.all([
    fetchAll("/api/reports/records", { sort: "generatedAt,desc" }),
    loadReferences(),
  ]);
  root.innerHTML = `<div class="stack"><div class="grid cols-3">${[
    [
      "STOCK_BALANCE",
      "Stock Balance",
      "Current medicine and supply stock by week.",
    ],
    [
      "TRANSACTION_HISTORY",
      "Transaction History",
      "Filter by transaction type and medicine/supply/equipment.",
    ],
    [
      "EQUIPMENT_REGISTRY",
      "Equipment Registry / Status",
      "Monthly clinic equipment inventory.",
    ],
  ]
    .map(
      ([type, name, desc]) =>
        `<div class="card shadow"><div class="card-body"><h3 style="margin:0 0 6px;color:var(--navy)">${name}</h3><p class="small muted" style="min-height:34px">${desc}</p><button class="btn primary generate-report" data-type="${type}">Generate report</button></div></div>`,
    )
    .join(
      "",
    )}</div><div class="card"><div class="card-head"><h2>Past Report Records</h2></div>${tableMarkup(
    {
      id: "reportHistory",
      searchFields: ["reportType", "generatedBy", "parametersJson"],
      columns: [
        {
          label: "Report Type",
          key: "reportType",
          width: 180,
          render: (r) => labelize(r.reportType),
        },
        {
          label: "Generated",
          key: "generatedAt",
          type: "date",
          width: 160,
          render: (r) => fmtDateTime(r.generatedAt),
        },
        { label: "Generated By", key: "generatedBy", width: 180 },
        { label: "Parameters", key: "parametersJson", width: 380 },
        {
          label: "Actions",
          sortable: false,
          width: 110,
          render: (r) =>
            `<button class="btn small preview-record" data-id="${r.id}">Preview</button>`,
        },
      ],
      rows: history,
    },
  )}</div></div>`;
  $$(".generate-report").forEach(
    (b) => (b.onclick = () => openGenerateReport(b.dataset.type, refs)),
  );
  $$(".preview-record").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          showReport(await api(`/api/reports/records/${b.dataset.id}/preview`));
        } catch (e) {
          toast(e.message, "error");
        }
      }),
  );
}
function openGenerateReport(type, refs) {
  const isTx = type === "TRANSACTION_HISTORY",
    isEq = type === "EQUIPMENT_REGISTRY",
    body = modal(
      "Generate Report",
      `<form id="reportForm"><div class="form-grid"><div class="field"><label>From</label><input id="repFrom" type="date" value="${today().slice(0, 8)}01"></div><div class="field"><label>To</label><input id="repTo" type="date" value="${today()}"></div>${isTx ? `<div class="field"><label>Transaction Type</label><select id="repTx"><option value="">All transactions</option>${["ADJUSTMENT", "DISPOSAL", "ISSUANCE", "RECEIVING"].map((x) => `<option>${x}</option>`).join("")}</select></div><div class="field"><label>Item Type</label><select id="repCat"><option value="">All item types</option>${["MEDICINE", "SUPPLY", "EQUIPMENT"].map((x) => `<option>${x}</option>`).join("")}</select></div>` : ""}${isEq ? `<div class="field span-2"><label>Clinic Site</label><select id="repLocation"><option value="">All locations</option>${optionList(refs.locations)}</select></div>` : ""}</div><div class="modal-actions"><button type="button" class="btn" id="repCancel">Cancel</button><button class="btn primary">Generate</button></div></form>`,
    );
  $("#repCancel", body).onclick = closeModal;
  $("#reportForm", body).onsubmit = async (e) => {
    e.preventDefault();
    const req = {
      reportType: type,
      from: $("#repFrom").value || null,
      to: $("#repTo").value || null,
      transactionType: isTx ? $("#repTx").value || null : null,
      itemCategory: isTx ? $("#repCat").value || null : null,
      locationId:
        isEq && $("#repLocation").value
          ? Number($("#repLocation").value)
          : null,
    };
    try {
      const report = await api("/api/reports/generate", {
        method: "POST",
        body: JSON.stringify(req),
      });
      closeModal();
      showReport(report);
    } catch (err) {
      toast(err.message, "error");
    }
  };
}

function clearReceivingItemForm() {
  // Clear selected item
  $("#recItemInput").value = "";
  $("#recItemId").value = "";

  // Reset quantity and UOM
  $("#recQty").value = 1;
  $("#recUom").value = "";

  // Clear medicine/supply fields
  $("#recBrand").value = "";
  $("#recBatch").value = "";
  $("#recExpiry").value = "";

  // Clear equipment fields
  $("#recModel").value = "";
  $("#recSerial").value = "";
  $("#recAsset").value = "";

  // Return fields to their default enabled/disabled state
  syncItem(null);
}

function showReport(report) {
  const rows = report.rows || [];
  let content = "";

  const isMedicineIssuance =
    report.reportType === "TRANSACTION_HISTORY" &&
    report.transactionType === "ISSUANCE" &&
    report.itemCategory === "MEDICINE";

  const isSupplyIssuance =
    report.reportType === "TRANSACTION_HISTORY" &&
    report.transactionType === "ISSUANCE" &&
    report.itemCategory === "SUPPLY";

  const isReceiving =
    report.reportType === "TRANSACTION_HISTORY" &&
    report.transactionType === "RECEIVING";

  if (isMedicineIssuance) {
    content = tableMarkup({
      id: "reportMedicineIssuance",

      searchFields: [
        "nurseOnDuty",
        "employeeNumber",
        "employeeName",
        "department",
        "supervisor",
        "chiefComplaint",
        "itemIssued",
        "batchNumber",
        "remarks",
      ],

      columns: [
        {
          label: "Date",
          key: "dateIssued",
          type: "date",
          width: 120,
          render: (r) => fmtDate(r.dateIssued),
        },

        {
          label: "Nurse-on-Duty",
          key: "nurseOnDuty",
          width: 170,
        },

        {
          label: "Employee No.",
          key: "employeeNumber",
          width: 130,
        },

        {
          label: "Employee Name",
          key: "employeeName",
          width: 180,
        },

        {
          label: "Department",
          key: "department",
          width: 140,
        },

        {
          label: "Supervisor",
          key: "supervisor",
          width: 160,
        },

        {
          label: "Chief Complaint",
          key: "chiefComplaint",
          width: 200,
        },

        {
          label: "Disposition",
          key: "disposition",
          width: 160,
        },

        {
          label: "Item Issued",
          key: "itemIssued",
          width: 200,
        },

        {
          label: "Batch No.",
          key: "batchNumber",
          width: 140,
        },

        {
          label: "Quantity",
          key: "quantity",
          type: "number",
          width: 90,
        },

        {
          label: "Unit",
          key: "unitOfMeasure",
          width: 100,
        },

        {
          label: "Remarks",
          key: "remarks",
          width: 250,
        },
      ],

      rows,
    });
  } else if (isSupplyIssuance) {
    content = tableMarkup({
      id: "reportSupplyIssuance",

      searchFields: [
        "nurseOnDuty",
        "employeeNumber",
        "employeeName",
        "department",
        "supervisor",
        "chiefComplaint",
        "itemIssued",
        "batchNumber",
        "remarks",
      ],

      columns: [
        {
          label: "Date",
          key: "dateIssued",
          type: "date",
          width: 120,
          render: (r) => fmtDate(r.dateIssued),
        },
        {
          label: "Nurse-on-Duty",
          key: "nurseOnDuty",
          width: 170,
        },
        {
          label: "Employee No.",
          key: "employeeNumber",
          width: 130,
        },
        {
          label: "Employee Name",
          key: "employeeName",
          width: 180,
        },
        {
          label: "Department",
          key: "department",
          width: 140,
        },
        {
          label: "Supervisor",
          key: "supervisor",
          width: 160,
        },
        {
          label: "Chief Complaint",
          key: "chiefComplaint",
          width: 200,
        },
        {
          label: "Disposition",
          key: "disposition",
          width: 160,
        },
        {
          label: "Item Issued",
          key: "itemIssued",
          width: 200,
        },
        {
          label: "Batch No.",
          key: "batchNumber",
          width: 140,
        },
        {
          label: "Quantity",
          key: "quantity",
          type: "number",
          width: 90,
        },
        {
          label: "Unit",
          key: "unitOfMeasure",
          width: 100,
        },
        {
          label: "Remarks",
          key: "remarks",
          width: 250,
        },
      ],

      rows,
    });
  } else if (isReceiving) {
    content = tableMarkup({
      id: "reportReceiving",

      searchFields: ["dateReceived", "nurseOnDuty", "supplier", "itemReceived"],

      columns: [
        {
          label: "Date Received",
          key: "dateReceived",
          type: "date",
          width: 120,
          render: (r) => fmtDate(r.dateReceived),
        },

        {
          label: "Received By",
          key: "receivedBy",
          width: 170,
        },

        {
          label: "Supplier",
          key: "receivedFrom",
          width: 180,
        },

        {
          label: "Item Received",
          key: "itemReceived",
          width: 200,
        },

        {
          label: "Quantity",
          key: "quantity",
          type: "number",
          width: 90,
        },
      ],

      rows,
    });
  }

  // ============================================
  // ALL OTHER TRANSACTION HISTORY REPORTS
  // ============================================
  else if (report.reportType === "TRANSACTION_HISTORY")
    content = tableMarkup({
      id: "reportTx",
      searchFields: ["referenceNumber", "user", "itemName", "detail"],
      columns: [
        {
          label: "Date",
          key: "date",
          type: "date",
          width: 165,
          render: (r) => fmtDateTime(r.date),
        },
        {
          label: "Transaction Type",
          key: "transactionType",
          width: 120,
          render: (r) => badge(r.transactionType),
        },
        { label: "Reference", key: "referenceNumber", width: 160 },
        { label: "User", key: "user", width: 160 },
        { label: "Item", key: "itemName", width: 200 },
        {
          label: "Category",
          key: "itemCategory",
          width: 110,
          render: (r) => (r.itemCategory ? labelize(r.itemCategory) : "—"),
        },
        { label: "Activity", key: "detail", width: 420 },
      ],
      rows,
    });
  else if (report.reportType === "EQUIPMENT_REGISTRY") {
    content = `<div class="report-table-wrap"><table class="report-table"><thead><tr><th style="width:150px">Asset Tag</th><th style="width:220px">Item</th>${["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"].map((m) => `<th style="width:70px">${m}</th>`).join("")}</tr></thead><tbody>${rows.map((r) => `<tr><td>${esc(r.assetTag)}</td><td>${esc(r.itemName)}</td>${r.monthlyPresence.map((v) => `<td>${v ?? ""}</td>`).join("")}</tr><tr><td><b>Remarks</b></td><td colspan="13">${esc(r.remarks || "")}</td></tr>`).join("")}</tbody></table></div>`;
  } else {
    content = `<div class="report-table-wrap"><table class="report-table"><thead><tr><th>Item</th><th>Running Bal</th><th>Total Monthly Dispensed</th><th>Beginning Inv</th>${Array.from({ length: 5 }, (_, i) => `<th>W${i + 1} DEL</th><th>W${i + 1} Pullout/Returns</th><th>W${i + 1} Dispensed</th><th>W${i + 1} Ending Inv</th><th>W${i + 1} Actual Inv</th><th>W${i + 1} VAR</th>`).join("")}</tr></thead><tbody>${rows.map((r) => `<tr><td>${esc(r.itemName)}</td><td>${r.runningBalance}</td><td>${r.totalMonthlyDispensed}</td><td>${r.beginningInventory}</td>${r.weeks.map((w) => `<td>${w.delivery}</td><td>0</td><td>${w.dispensed}</td><td>${w.endingInventory}</td><td>${w.actualInventory}</td><td>${w.variance}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
  }
  const body = modal(
    report.title,
    `<h2 class="report-title">${esc(report.title)}</h2><div class="report-sub">${fmtDate(report.from)} to ${fmtDate(report.to)}</div>${content}<div class="report-actions"><button class="btn primary exp" data-format="pdf">Generate PDF</button><button class="btn exp" data-format="csv">Export CSV</button><button class="btn exp" data-format="xlsx">Export Excel</button></div>`,
    { wide: true },
  );
  initTables(body);
  $$(".exp", body).forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          const format = b.dataset.format,
            name = `${report.reportType.toLowerCase()}-${report.to}.${format === "xlsx" ? "xlsx" : format}`;
          if (report.reportRecordId)
            await downloadApi(
              `/api/reports/records/${report.reportRecordId}/export/${format}`,
              {},
              name,
            );
          else
            await downloadApi(
              `/api/reports/export/${format}`,
              {
                method: "POST",
                body: JSON.stringify({
                  reportType: report.reportType,
                  from: report.from,
                  to: report.to,

                  transactionType: report.transactionType || null,
                  itemCategory: report.itemCategory || null,
                }),
              },
              name,
            );
        } catch (e) {
          toast(e.message, "error");
        }
      }),
  );
}

async function boot() {
  window.__CIMS_BOOT_STARTED__ = true;
  try {
    if (location.protocol === "file:") {
      app.token = "";
      storageRemove("cims.basic");
      showLogin(
        "The HTML file was opened directly from disk. Start ClinicInventoryApplication in Eclipse, then open http://localhost:8080/ in Chrome, Edge, or Firefox.",
      );
      return;
    }
    if (!app.token) {
      showLogin();
      return;
    }
    $("#app").innerHTML =
      '<div class="boot-screen"><div class="spinner"></div><p>Restoring your signed-in session…</p><p class="small muted">If the server or MySQL is unavailable, this check will stop automatically and show the error.</p></div>';
    app.session = await api("/api/session/me", { timeoutMs: 10000 });
    renderShell();
    await navigate("Dashboard");
  } catch (e) {
    app.token = "";
    storageRemove("cims.basic");
    showLogin(e?.message || "The application could not initialize.");
  } finally {
    window.__CIMS_BOOT_FINISHED__ = true;
  }
}

window.addEventListener("error", (event) => {
  if (!window.__CIMS_BOOT_FINISHED__) {
    const host = document.getElementById("app");
    if (host)
      host.innerHTML = `<div class="login-shell"><div class="login-card"><div class="brand-mark">CIMS</div><h1>Frontend startup error</h1><div class="notice error">${esc(event.message || "The frontend could not start.")}</div><p class="small muted">Open the application through http://localhost:8080/ and check the browser Console if this continues.</p></div></div>`;
  }
});
document.addEventListener("DOMContentLoaded", boot);
