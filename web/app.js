/**
 * SYPOS Mobile Pro — Web PWA Engine v2.3
 * 100% Offline IndexedDB/LocalStorage + Web Bluetooth Thermal ESC/POS Driver +
 * Camera Barcode Scanner + Customer CRM + Google Sheets Sync + WhatsApp PNG Receipt
 */

// ==========================================
// 1. DEFAULT DATA & STATE
// ==========================================
const STATE = {
  products: [],
  categories: [],
  tickets: [],
  customers: [],
  expenses: [],
  promoCodes: [],
  heldSales: [],
  settings: {
    shopName: "SYPOS COMMERCE",
    shopAddress: "Abidjan, Côte d'Ivoire",
    shopPhone: "+225 07 58 24 55 30",
    receiptFooter: "Merci de votre visite et à bientôt !",
    sellerName: "Vendeur 1",
    showPublisherSignature: true,
    publisherSignatureText: "Solution: SYPOS MOBILE 0758245530",
    taxEnabled: false,
    taxRatePercent: 18.0,
    adminPin: "1234",
    allowNegativeStock: false,
    printerWidth: "58",
    isLicensed: false,
    licenseKey: "",
    licenseType: "Non activé",
    licenseExpiryDate: 0,
    googleSheetsUrl: "",
    googleSheetsSync: false
  },
  cart: [],
  appliedPromo: null,
  activeCategory: "",
  selectedCustomerId: "",
  html5QrCode: null,
  pendingPinAction: null,
  bleDevice: null,
  bleCharacteristic: null
};

let currentCustomerFilter = 'all';
let currentHistoryPeriod = 'today';
let pendingQuickAddFromCart = false;

// ==========================================
// 2. GOOGLE APPS SCRIPT TEMPLATE CODE
// ==========================================
const APPS_SCRIPT_TEMPLATE = `/**
 * ==========================================================================
 * GOOGLE APPS SCRIPT POUR SYPOS MOBILE
 * Suivi des Ventes & Dépenses en Temps Réel
 * ==========================================================================
 * 
 * INSTRUCTIONS DE DÉPLOIEMENT :
 * 1. Ouvrez un nouveau classeur Google Sheets.
 * 2. Allez dans "Extensions" > "Apps Script".
 * 3. Effacez le code par défaut et collez l'intégralité de ce script.
 * 4. Cliquez sur "Déployer" (bouton bleu en haut à droite) > "Nouveau déploiement".
 * 5. Choisissez le type : "Application Web".
 * 6. Définissez "Qui a accès" (Who has access) : "Tout le monde" (Anyone).
 * 7. Cliquez sur "Déployer", autorisez l'accès et copiez l'URL de l'application Web.
 * 8. Collez l'URL dans les Paramètres SYPOS Mobile > "URL Webhook Apps Script".
 */

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    
    // 1. Test de Connexion Ping
    if (data.type === "TEST") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "SUCCESS",
        message: "Connexion réussie avec SYPOS Mobile !"
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // 2. Enregistrement d'une Vente / Ticket
    if (data.type === "SALE" || data.type === "TICKET") {
      var sheet = getOrCreateSheet(ss, "Ventes", [
        "Date", "Heure", "N° Ticket", "Vendeur", "Client", "Articles", 
        "Sous-Total (CFA)", "Remise (CFA)", "TVA (CFA)", "Total Payé (CFA)", "Mode de Règlement"
      ]);
      
      var now = new Date(data.date || Date.now());
      var dateStr = Utilities.formatDate(now, Session.getScriptTimeZone(), "dd/MM/yyyy");
      var timeStr = Utilities.formatDate(now, Session.getScriptTimeZone(), "HH:mm:ss");
      var itemsStr = (data.items || []).map(function(i) {
        return i.productName + " (x" + i.qty + ")";
      }).join(", ");
      
      sheet.appendRow([
        dateStr,
        timeStr,
        data.ticketNumber || data.number,
        data.sellerName || "",
        data.customerName || "Client Comptoir",
        itemsStr,
        data.subTotal || data.totalAmount,
        data.discount || 0,
        data.taxAmount || 0,
        data.totalAmount,
        (data.paymentMethod || "ESPECES").toUpperCase()
      ]);

      // Feuille détaillée Articles Vendus
      var itemSheet = getOrCreateSheet(ss, "Articles_Vendus", [
        "Date", "N° Ticket", "Article", "Prix Unitaire", "Quantité", "Total Ligne (CFA)"
      ]);
      (data.items || []).forEach(function(i) {
        itemSheet.appendRow([
          dateStr,
          data.ticketNumber || data.number,
          i.productName,
          i.price,
          i.qty,
          (i.price * i.qty)
        ]);
      });
    }

    // 3. Enregistrement d'une Dépense / Sortie de Caisse
    if (data.type === "EXPENSE") {
      var expSheet = getOrCreateSheet(ss, "Dépenses", [
        "Date", "Heure", "Motif de la Dépense", "Montant (CFA)"
      ]);
      var nowExp = new Date(data.date || Date.now());
      expSheet.appendRow([
        Utilities.formatDate(nowExp, Session.getScriptTimeZone(), "dd/MM/yyyy"),
        Utilities.formatDate(nowExp, Session.getScriptTimeZone(), "HH:mm:ss"),
        data.reason,
        data.amount
      ]);
    }

    return ContentService.createTextOutput(JSON.stringify({ status: "SUCCESS" }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "ERROR", error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function getOrCreateSheet(ss, name, headers) {
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length)
      .setFontWeight("bold")
      .setBackground("#0D9488")
      .setFontColor("#FFFFFF");
    sheet.setFrozenRows(1);
  }
  return sheet;
}`;

// ==========================================
// 3. LOCAL PERSISTENCE (LocalStorage)
// ==========================================
function loadFromStorage() {
  const s = localStorage.getItem('sypos_data_v2');
  if (s) {
    try {
      const parsed = JSON.parse(s);
      Object.assign(STATE.settings, parsed.settings || {});
      STATE.products = parsed.products || [];
      STATE.categories = (parsed.categories && parsed.categories.length > 0) ? parsed.categories : [
        { id: "1", name: "Alimentation", color: "#10B981" },
        { id: "2", name: "Boissons", color: "#3B82F6" },
        { id: "3", name: "Hygiène", color: "#EC4899" },
        { id: "4", name: "Divers", color: "#8B5CF6" }
      ];
      STATE.tickets = parsed.tickets || [];
      STATE.customers = (parsed.customers || []).map(c => ({
        id: c.id || ("c_" + Math.random()),
        name: c.name || "Client",
        phone: c.phone || "",
        email: c.email || "",
        notes: c.notes || "",
        totalSpent: typeof c.totalSpent === 'number' ? c.totalSpent : (c.debt ? 0 : 0),
        visitCount: typeof c.visitCount === 'number' ? c.visitCount : (c.totalSpent ? 1 : 0),
        lastPurchaseDate: c.lastPurchaseDate || null,
        createdAt: c.createdAt || Date.now()
      }));
      STATE.expenses = parsed.expenses || [];
      STATE.promoCodes = parsed.promoCodes || [];
      STATE.heldSales = parsed.heldSales || [];
    } catch (e) {
      console.error(e);
    }
  } else {
    STATE.categories = [
      { id: "1", name: "Alimentation", color: "#10B981" },
      { id: "2", name: "Boissons", color: "#3B82F6" },
      { id: "3", name: "Hygiène", color: "#EC4899" },
      { id: "4", name: "Divers", color: "#8B5CF6" }
    ];
    STATE.products = [];
    STATE.customers = [];
    STATE.tickets = [];
    STATE.expenses = [];
    STATE.heldSales = [];
    STATE.promoCodes = [];
    saveToStorage();
  }
}

function saveToStorage() {
  localStorage.setItem('sypos_data_v2', JSON.stringify({
    settings: STATE.settings,
    products: STATE.products,
    categories: STATE.categories,
    tickets: STATE.tickets,
    customers: STATE.customers,
    expenses: STATE.expenses,
    promoCodes: STATE.promoCodes,
    heldSales: STATE.heldSales
  }));
}

// ==========================================
// 4. TAB NAVIGATION
// ==========================================
function initTabs() {
  document.querySelectorAll('.app-tabbar .tab-item').forEach(tab => {
    tab.addEventListener('click', (e) => {
      e.preventDefault();
      const tabName = tab.dataset.tab;
      document.querySelectorAll('.app-tabbar .tab-item').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));
      const activeSec = document.getElementById(`view-${tabName}`);
      if (activeSec) activeSec.classList.add('active');

      // Refresh view contents
      if (tabName === 'pos') renderPos();
      else if (tabName === 'products') renderProducts();
      else if (tabName === 'history') renderHistory();
      else if (tabName === 'reports') renderReports();
      else if (tabName === 'customers') renderCustomers();
      else if (tabName === 'tools') renderTools();
      else if (tabName === 'settings') renderSettings();
    });
  });
}

// ==========================================
// 5. POS / CAISSE & CART MANAGEMENT
// ==========================================
function renderPos() {
  const container = document.getElementById('posProductGrid');
  const catScroll = document.getElementById('posCategoryScroll');
  if (!container || !catScroll) return;

  // Render Categories pills
  catScroll.innerHTML = `<button class="cat-pill ${!STATE.activeCategory ? 'active' : ''}" data-cat="">Tous</button>`;
  (STATE.categories || []).forEach(cat => {
    const pill = document.createElement('button');
    pill.className = `cat-pill ${STATE.activeCategory === cat.id ? 'active' : ''}`;
    pill.dataset.cat = cat.id;
    pill.textContent = cat.name;
    pill.addEventListener('click', () => {
      STATE.activeCategory = cat.id;
      renderPos();
    });
    catScroll.appendChild(pill);
  });

  const firstPill = catScroll.querySelector('button[data-cat=""]');
  if (firstPill) {
    firstPill.onclick = () => {
      STATE.activeCategory = "";
      renderPos();
    };
  }

  // Filter Products
  const searchVal = (document.getElementById('posSearchInput') ? document.getElementById('posSearchInput').value : '').toLowerCase().trim();
  const filtered = (STATE.products || []).filter(p => {
    const matchCat = !STATE.activeCategory || p.catId === STATE.activeCategory;
    const matchSearch = !searchVal || p.name.toLowerCase().includes(searchVal) || (p.barcode && p.barcode.includes(searchVal));
    return matchCat && matchSearch;
  });

  container.innerHTML = '';
  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1/-1; text-align:center; padding: 40px 10px; color: var(--text-muted);">
        <div style="font-size: 36px; margin-bottom: 8px;">🛍️</div>
        <div>Aucun article trouvé</div>
      </div>
    `;
    return;
  }

  filtered.forEach(p => {
    const card = document.createElement('div');
    card.className = 'product-card';
    const isOutOfStock = p.stock <= 0 && !STATE.settings.allowNegativeStock;
    if (isOutOfStock) card.style.opacity = '0.5';

    card.innerHTML = `
      <div class="prod-badge" style="display:${p.stock <= 5 ? 'block' : 'none'}; background:${p.stock <= 0 ? 'var(--danger)' : 'var(--warning)'};">
        ${p.stock <= 0 ? 'Épuisé' : 'Stock bas: ' + p.stock}
      </div>
      <div class="prod-name">${p.name}</div>
      <div class="prod-price">${p.salePrice.toLocaleString('fr-FR')} CFA</div>
    `;
    card.addEventListener('click', () => addToCart(p));
    container.appendChild(card);
  });

  updateFloatingCart();
}

function addToCart(product) {
  if (product.stock <= 0 && !STATE.settings.allowNegativeStock) {
    alert("⚠️ Article épuisé en stock !");
    return;
  }
  const existing = STATE.cart.find(i => i.product.id === product.id);
  if (existing) {
    if (existing.qty + 1 > product.stock && !STATE.settings.allowNegativeStock) {
      alert("⚠️ Stock insuffisant pour ajouter un exemplaire supplémentaire.");
      return;
    }
    existing.qty += 1;
  } else {
    STATE.cart.push({ product, qty: 1 });
  }
  if (navigator.vibrate) navigator.vibrate(40);
  updateFloatingCart();
}

function updateFloatingCart() {
  const bar = document.getElementById('floatingCartBar');
  const countEl = document.getElementById('floatCartCount');
  const totalEl = document.getElementById('floatCartTotal');
  const heldBadge = document.getElementById('heldBadge');

  if (heldBadge) {
    if (STATE.heldSales && STATE.heldSales.length > 0) {
      heldBadge.textContent = STATE.heldSales.length;
      heldBadge.style.display = 'inline-block';
    } else {
      heldBadge.style.display = 'none';
    }
  }

  if (!bar || !countEl || !totalEl) return;
  const count = STATE.cart.reduce((sum, i) => sum + i.qty, 0);
  const total = getCartTotal();

  if (count > 0) {
    bar.style.display = 'flex';
    countEl.textContent = count;
    totalEl.textContent = total.toLocaleString('fr-FR');
  } else {
    bar.style.display = 'none';
  }
}

function getCartSubtotal() {
  return STATE.cart.reduce((sum, i) => sum + (i.product.salePrice * i.qty), 0);
}

function getCartTotal() {
  let subtotal = getCartSubtotal();
  if (STATE.appliedPromo) {
    if (STATE.appliedPromo.discountPercent) {
      subtotal -= (subtotal * (STATE.appliedPromo.discountPercent / 100));
    } else if (STATE.appliedPromo.discountAmount) {
      subtotal = Math.max(0, subtotal - STATE.appliedPromo.discountAmount);
    }
  }
  if (STATE.settings.taxEnabled) {
    subtotal += (subtotal * ((STATE.settings.taxRatePercent || 18) / 100));
  }
  return Math.round(subtotal);
}

function populateCartCustomerSelect() {
  const sel = document.getElementById('cartCustomerSelect');
  if (!sel) return;
  const currentVal = STATE.selectedCustomerId;
  sel.innerHTML = '<option value="">-- Client Comptoir (Anonyme) --</option>';
  (STATE.customers || []).forEach(c => {
    const opt = document.createElement('option');
    opt.value = c.id;
    opt.textContent = `${c.name} ${c.phone ? '(' + c.phone + ')' : ''}`;
    if (c.id === currentVal) opt.selected = true;
    sel.appendChild(opt);
  });
  updateCartCustomerBadge();
}

function updateCartCustomerBadge() {
  const badge = document.getElementById('cartCustomerDetailsBadge');
  if (!badge) return;
  const cust = STATE.customers.find(c => c.id === STATE.selectedCustomerId);
  if (cust) {
    badge.style.display = 'block';
    badge.textContent = `⭐ Total achats : ${(cust.totalSpent || 0).toLocaleString('fr-FR')} CFA (${cust.visitCount || 0} commande(s))`;
  } else {
    badge.style.display = 'none';
  }
}

function renderCartModal() {
  const list = document.getElementById('cartItemsList');
  if (!list) return;
  list.innerHTML = '';

  populateCartCustomerSelect();

  if (STATE.cart.length === 0) {
    list.innerHTML = '<div style="text-align:center; padding: 20px; color: var(--text-muted);">Le panier est vide.</div>';
  } else {
    STATE.cart.forEach((item, index) => {
      const row = document.createElement('div');
      row.className = 'cart-item-row';
      row.innerHTML = `
        <div class="cart-item-info">
          <div class="cart-item-title">${item.product.name}</div>
          <div class="cart-item-price">${item.product.salePrice.toLocaleString('fr-FR')} CFA / unité</div>
        </div>
        <div class="cart-item-qty-controls">
          <button class="icon-btn btn-qty-minus" style="border: 1px solid var(--border); width: 32px; height: 32px;">-</button>
          <span style="font-weight: 700; width: 24px; text-align: center;">${item.qty}</span>
          <button class="icon-btn btn-qty-plus" style="border: 1px solid var(--border); width: 32px; height: 32px;">+</button>
          <button class="icon-btn btn-qty-del" style="color: var(--danger); width: 32px; height: 32px; margin-left: 4px;">🗑️</button>
        </div>
      `;

      row.querySelector('.btn-qty-minus').addEventListener('click', () => {
        if (item.qty > 1) {
          item.qty -= 1;
        } else {
          STATE.cart.splice(index, 1);
        }
        renderCartModal();
        updateFloatingCart();
      });

      row.querySelector('.btn-qty-plus').addEventListener('click', () => {
        if (item.qty + 1 > item.product.stock && !STATE.settings.allowNegativeStock) {
          alert("⚠️ Stock insuffisant.");
          return;
        }
        item.qty += 1;
        renderCartModal();
        updateFloatingCart();
      });

      row.querySelector('.btn-qty-del').addEventListener('click', () => {
        STATE.cart.splice(index, 1);
        renderCartModal();
        updateFloatingCart();
      });

      list.appendChild(row);
    });
  }

  const sub = getCartSubtotal();
  const tot = getCartTotal();
  document.getElementById('cartSubtotalText').textContent = sub.toLocaleString('fr-FR') + ' CFA';
  document.getElementById('cartTotalText').textContent = tot.toLocaleString('fr-FR') + ' CFA';

  const taxRow = document.getElementById('cartTaxRow');
  if (taxRow) {
    if (STATE.settings.taxEnabled) {
      taxRow.style.display = 'flex';
      const taxAmt = Math.round(sub * ((STATE.settings.taxRatePercent || 18) / 100));
      document.getElementById('cartTaxText').textContent = taxAmt.toLocaleString('fr-FR') + ' CFA';
    } else {
      taxRow.style.display = 'none';
    }
  }
}

// ==========================================
// 6. BLUETOOTH THERMAL PRINTER ESC/POS ENGINE
// ==========================================
async function connectBluetoothPrinter() {
  if (!navigator.bluetooth) {
    alert("⚠️ Web Bluetooth n'est pas supporté par ce navigateur. Utilisez Chrome sur Android ou Bluefy sur iOS.");
    return false;
  }
  try {
    const device = await navigator.bluetooth.requestDevice({
      filters: [
        { services: ['000018f0-0000-1000-8000-00805f9b34fb'] },
        { services: ['e7810a71-73ae-499d-8c15-faa9aef0c3f2'] },
        { services: ['49535343-fe7d-4ae5-8fa9-9fafd205e455'] }
      ],
      optionalServices: [
        '000018f0-0000-1000-8000-00805f9b34fb',
        'e7810a71-73ae-499d-8c15-faa9aef0c3f2',
        '49535343-fe7d-4ae5-8fa9-9fafd205e455',
        '0000ffe0-0000-1000-8000-00805f9b34fb'
      ]
    });

    device.addEventListener('gattserverdisconnected', () => {
      console.log('Printer disconnected');
      STATE.bleDevice = null;
      STATE.bleCharacteristic = null;
    });

    const server = await device.gatt.connect();
    const services = await server.getPrimaryServices();
    for (let s of services) {
      const chars = await s.getCharacteristics();
      for (let c of chars) {
        if (c.properties.write || c.properties.writeWithoutResponse) {
          STATE.bleDevice = device;
          STATE.bleCharacteristic = c;
          localStorage.setItem('last_ble_device_id', device.id);
          alert("✅ Imprimante Bluetooth connectée : " + (device.name || "Imprimante Thermique"));
          return true;
        }
      }
    }
  } catch (err) {
    if (err.name !== 'NotFoundError') {
      console.error("BLE Error:", err);
      alert("Erreur Bluetooth : " + err.message);
    }
    return false;
  }
  return false;
}

async function sendToPrinter(rawBytes) {
  if (!STATE.bleCharacteristic) {
    const ok = await connectBluetoothPrinter();
    if (!ok) return false;
  }
  try {
    const chunkSize = 100;
    for (let i = 0; i < rawBytes.length; i += chunkSize) {
      const chunk = rawBytes.slice(i, i + chunkSize);
      await STATE.bleCharacteristic.writeValue(chunk);
    }
    return true;
  } catch (err) {
    console.error("Print send error:", err);
    alert("⚠️ Erreur lors de l'envoi à l'imprimante : " + err.message);
    return false;
  }
}

function removeAccents(str) {
  return (str || '').normalize("NFD").replace(/[\u0300-\u036f]/g, "");
}

function centerText(text, width = 32) {
  const clean = text.trim();
  if (clean.length >= width) return clean.substring(0, width);
  const left = Math.floor((width - clean.length) / 2);
  return ' '.repeat(left) + clean;
}

function padLine(left, right, width = 32) {
  const l = left.trim();
  const r = right.trim();
  const spaces = width - l.length - r.length;
  if (spaces <= 0) return l.substring(0, width - r.length - 1) + ' ' + r;
  return l + ' '.repeat(spaces) + r;
}

function generateReceiptPlainText(ticket) {
  const width = (STATE.settings.printerWidth === "80") ? 42 : 32;
  const sep = '='.repeat(width);
  const dash = '-'.repeat(width);

  let out = [];
  out.push(sep);
  out.push(centerText(removeAccents(STATE.settings.shopName || "SYPOS").toUpperCase(), width));
  if (STATE.settings.shopAddress) {
    out.push(centerText(removeAccents(STATE.settings.shopAddress), width));
  }
  if (STATE.settings.shopPhone) {
    out.push(centerText("Tel: " + removeAccents(STATE.settings.shopPhone), width));
  }
  out.push(sep);
  out.push(padLine("Ticket N°: " + ticket.number, new Date(ticket.date).toLocaleDateString('fr-FR'), width));
  if (STATE.settings.sellerName) {
    out.push(padLine("Vendeur:", removeAccents(STATE.settings.sellerName), width));
  }
  if (ticket.customerName) {
    out.push(padLine("Client:", removeAccents(ticket.customerName), width));
  }
  out.push(dash);

  ticket.items.forEach(it => {
    const lineTotal = (it.price * it.qty).toLocaleString('fr-FR') + ' F';
    const nameStr = removeAccents(it.productName);
    const qtyPriceStr = `${it.qty}x ${it.price.toLocaleString('fr-FR')}`;
    out.push(nameStr);
    out.push(padLine("  " + qtyPriceStr, lineTotal, width));
  });

  out.push(dash);
  out.push(padLine("TOTAL NET :", ticket.totalAmount.toLocaleString('fr-FR') + ' CFA', width));
  out.push(padLine("Reglement :", removeAccents((ticket.paymentMethod || 'ESPECES').toUpperCase()), width));

  if (ticket.cashReceived) {
    out.push(padLine("Recu :", ticket.cashReceived.toLocaleString('fr-FR') + ' CFA', width));
  }
  if (ticket.changeGiven) {
    out.push(padLine("Rendu :", ticket.changeGiven.toLocaleString('fr-FR') + ' CFA', width));
  }
  out.push(sep);

  if (STATE.settings.receiptFooter) {
    out.push(centerText(removeAccents(STATE.settings.receiptFooter), width));
  }
  if (STATE.settings.showPublisherSignature) {
    out.push(centerText(removeAccents(STATE.settings.publisherSignatureText || "SYPOS MOBILE 0758245530"), width));
  }
  
  out.push('\n\n');
  return out.join('\n');
}

function generateReceiptHtml(ticket) {
  const dateStr = new Date(ticket.date).toLocaleString('fr-FR');
  let itemsHtml = '';
  ticket.items.forEach(it => {
    itemsHtml += `
      <div class="receipt-row">
        <span>${it.productName} ×${it.qty}</span>
        <span>${(it.price * it.qty).toLocaleString('fr-FR')} CFA</span>
      </div>
    `;
  });

  return `
    <div class="receipt-center receipt-bold" style="font-size: 14px;">${STATE.settings.shopName.toUpperCase()}</div>
    <div class="receipt-center">${STATE.settings.shopAddress}</div>
    <div class="receipt-center">Tél: ${STATE.settings.shopPhone}</div>
    <div class="receipt-divider"></div>
    <div class="receipt-row"><span>Ticket N°:</span><span>${ticket.number}</span></div>
    <div class="receipt-row"><span>Date:</span><span>${dateStr}</span></div>
    <div class="receipt-row"><span>Vendeur:</span><span>${STATE.settings.sellerName}</span></div>
    ${ticket.customerName ? `<div class="receipt-row"><span>Client:</span><span style="font-weight:bold;">${ticket.customerName}</span></div>` : ''}
    <div class="receipt-divider"></div>
    ${itemsHtml}
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 14px;">
      <span>TOTAL NET :</span>
      <span>${ticket.totalAmount.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-row"><span>Règlement :</span><span>${(ticket.paymentMethod || 'ESPECES').toUpperCase()}</span></div>
    ${ticket.cashReceived ? `<div class="receipt-row"><span>Reçu :</span><span>${ticket.cashReceived.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${ticket.changeGiven ? `<div class="receipt-row"><span>Rendu :</span><span>${ticket.changeGiven.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div class="receipt-center" style="font-size: 11px;">${STATE.settings.receiptFooter}</div>
    ${STATE.settings.showPublisherSignature ? `<div class="receipt-center" style="font-size: 9px; color:#666; margin-top:6px;">${STATE.settings.publisherSignatureText}</div>` : ''}
  `;
}

// ==========================================
// 7. Z-REPORT & CLÔTURE DE CAISSE
// ==========================================
function getZReportData() {
  const now = new Date();
  const dateText = now.toLocaleDateString('fr-FR') + ' ' + now.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  
  let totalSales = 0;
  let ticketsCount = STATE.tickets.length;
  let cashSales = 0;
  let waveSales = 0;
  let omSales = 0;
  let momoSales = 0;
  let cardSales = 0;

  STATE.tickets.forEach(t => {
    totalSales += t.totalAmount;
    const m = (t.paymentMethod || 'cash').toLowerCase();
    if (m === 'cash' || m === 'especes') cashSales += t.totalAmount;
    else if (m === 'wave') waveSales += t.totalAmount;
    else if (m === 'om' || m === 'orange') omSales += t.totalAmount;
    else if (m === 'momo' || m === 'mtn') momoSales += t.totalAmount;
    else if (m === 'card' || m === 'carte') cardSales += t.totalAmount;
  });

  const totalExpenses = (STATE.expenses || []).reduce((sum, e) => sum + (e.amount || 0), 0);
  const netCashInDrawer = Math.max(0, cashSales - totalExpenses);

  return {
    dateText,
    totalSales,
    ticketsCount,
    totalExpenses,
    cashSales,
    waveSales,
    omSales,
    momoSales,
    cardSales,
    netCashInDrawer
  };
}

function generateZReportPlainText(zData) {
  const width = (STATE.settings.printerWidth === "80") ? 42 : 32;
  const sep = '='.repeat(width);
  const dash = '-'.repeat(width);

  let out = [];
  out.push(sep);
  out.push(centerText("RAPPORT Z DE CAISSE", width));
  out.push(centerText(removeAccents(STATE.settings.shopName || "SYPOS COMMERCE").toUpperCase(), width));
  out.push(centerText("Cloture: " + zData.dateText, width));
  if (STATE.settings.sellerName) {
    out.push(centerText("Responsable: " + removeAccents(STATE.settings.sellerName), width));
  }
  out.push(sep);
  out.push(padLine("TOTAL VENTES :", zData.totalSales.toLocaleString('fr-FR') + ' CFA', width));
  out.push(padLine("Nombre Tickets :", String(zData.ticketsCount), width));
  if (zData.totalExpenses > 0) {
    out.push(padLine("Total Depenses :", '-' + zData.totalExpenses.toLocaleString('fr-FR') + ' CFA', width));
  }
  out.push(dash);
  out.push("Ventilation Encaissements:");
  if (zData.cashSales > 0) out.push(padLine("  - Especes (Cash):", zData.cashSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.waveSales > 0) out.push(padLine("  - Wave:", zData.waveSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.omSales > 0) out.push(padLine("  - Orange Money:", zData.omSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.momoSales > 0) out.push(padLine("  - MTN MoMo:", zData.momoSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.cardSales > 0) out.push(padLine("  - Carte Bancaire:", zData.cardSales.toLocaleString('fr-FR') + ' F', width));
  out.push(sep);
  out.push(padLine("CASH NET EN CAISSE :", zData.netCashInDrawer.toLocaleString('fr-FR') + ' CFA', width));
  out.push(sep);
  out.push(centerText("Signature Responsable:", width));
  out.push('\n\n');
  out.push(centerText("................................", width));
  out.push('\n\n\n');
  return out.join('\n');
}

function generateZReportHtml(zData) {
  return `
    <div class="receipt-center receipt-bold" style="font-size: 15px;">RAPPORT Z DE CAISSE</div>
    <div class="receipt-center receipt-bold">${STATE.settings.shopName.toUpperCase()}</div>
    <div class="receipt-center" style="font-size: 11px; color: #666;">Date de clôture : ${zData.dateText}</div>
    <div class="receipt-center" style="font-size: 11px;">Responsable : ${STATE.settings.sellerName}</div>
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 14px;">
      <span>TOTAL VENTES :</span>
      <span style="color: var(--primary);">${zData.totalSales.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-row"><span>Nombre de Tickets :</span><span>${zData.ticketsCount}</span></div>
    ${zData.totalExpenses > 0 ? `<div class="receipt-row" style="color: var(--danger);"><span>Dépenses / Sorties :</span><span>-${zData.totalExpenses.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div style="font-size: 12px; font-weight: 700; margin-bottom: 4px;">Ventilation des Règlements :</div>
    ${zData.cashSales > 0 ? `<div class="receipt-row"><span>💵 Espèces (Cash) :</span><span>${zData.cashSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.waveSales > 0 ? `<div class="receipt-row"><span>🌊 Wave :</span><span>${zData.waveSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.omSales > 0 ? `<div class="receipt-row"><span>🟠 Orange Money :</span><span>${zData.omSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.momoSales > 0 ? `<div class="receipt-row"><span>🟡 MTN MoMo :</span><span>${zData.momoSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.cardSales > 0 ? `<div class="receipt-row"><span>💳 Carte Bancaire :</span><span>${zData.cardSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 15px; background: #e8f5e9; padding: 6px; border-radius: 8px;">
      <span>CASH NET EN CAISSE :</span>
      <span style="color: #2e7d32;">${zData.netCashInDrawer.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-divider"></div>
    <div class="receipt-center" style="font-size: 10px; color: #888;">Signature Responsable :</div>
    <div style="height: 35px;"></div>
    <div class="receipt-center" style="color: #aaa;">................................</div>
  `;
}

function exportToCsv(filename, headers, rows) {
  let csvContent = "\uFEFF";
  csvContent += headers.map(h => `"${h}"`).join(";") + "\r\n";
  rows.forEach(r => {
    csvContent += r.map(val => `"${String(val || '').replace(/"/g, '""')}"`).join(";") + "\r\n";
  });
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", filename);
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

function createEscPosBarcodeLabelBytes(shopName, prodName, price, barcode) {
  const init = [0x1B, 0x40, 0x1C, 0x2E, 0x1B, 0x74, 0x00];
  const center = [0x1B, 0x61, 0x01];
  const boldOn = [0x1B, 0x45, 0x01];
  const boldOff = [0x1B, 0x45, 0x00];
  const dHeightOn = [0x1B, 0x21, 0x10];
  const dHeightOff = [0x1B, 0x21, 0x00];

  const textLines = [
    ...center, ...boldOn, ...Array.from(removeAccents(shopName || "SYPOS")).map(c => c.charCodeAt(0)), 0x0A,
    ...boldOff, ...Array.from(removeAccents(prodName || "Article")).map(c => c.charCodeAt(0)), 0x0A,
    ...boldOn, ...dHeightOn, ...Array.from(price + " CFA").map(c => c.charCodeAt(0)), 0x0A,
    ...boldOff, ...dHeightOff
  ];

  let barcodeBytes = [];
  const cleanCode = (barcode || '').replace(/[^0-9A-Za-z]/g, '');
  if (cleanCode.length > 0) {
    const codeAscii = Array.from(cleanCode).map(c => c.charCodeAt(0));
    barcodeBytes = [
      0x1D, 0x68, 50, // GS h 50 (Height)
      0x1D, 0x77, 2,  // GS w 2 (Module width 2)
      0x1D, 0x48, 2,  // GS H 2 (HRI below barcode)
      0x1D, 0x6B, 73, codeAscii.length, ...codeAscii,
      0x0A
    ];
  }

  const footer = [0x0A, 0x0A, 0x1B, 0x64, 0x03];
  return new Uint8Array([...init, ...textLines, ...barcodeBytes, ...footer]);
}

// ==========================================
// 8. CRYPTOGRAPHIC LICENSE MANAGEMENT (RSA-2048)
// ==========================================
const RSA_PUBLIC_KEY_B64 = 
  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy4fzpl4lsQkScqSwHrAx" +
  "CiUEndXd1Kop9u/kKdGtxmvvJzxD15KN71TJ8ZVw/ds749Pz3yCdzlU7io5fDshu" +
  "VLYNVCXIpOJUtjGdtxVNCv9l0+bpAUKLZpPEHGgyXlDGbVE8G6bOQXXt57CoFdTQ" +
  "iGm7iBuQ2jcI6tW2Y0BZztforF57YX133ls9Eex5aM7pjzQcY31hrlLDKN3+1CD+" +
  "/XFSfPw5WfcLlOYm0x6FzFkFhG6s5qSNB1cfQ5yjScXvHGXoRa5Eo/MH4BXkU2vT" +
  "IZS5Y4+gqdc4T6drGyzQ2uUUtsII8zNSh0gRZAja452p4EuwpyrcgcopZG497gHp" +
  "5QIDAQAB";

function getDeviceId() {
  let devId = localStorage.getItem('sypos_device_id');
  if (!devId) {
    const rawFp = (navigator.userAgent || '') + '|' + screen.width + 'x' + screen.height + '|' + (navigator.hardwareConcurrency || 4) + '|' + Date.now();
    let hash = 0;
    for (let i = 0; i < rawFp.length; i++) {
      hash = ((hash << 5) - hash) + rawFp.charCodeAt(i);
      hash |= 0;
    }
    const hex1 = Math.abs(hash).toString(16).toUpperCase().padStart(4, '0').substring(0, 4);
    const hex2 = Math.floor(1000 + Math.random() * 9000).toString(16).toUpperCase().padStart(4, '0').substring(0, 4);
    devId = `SYPOS-DEV-${hex1}-${hex2}`;
    localStorage.setItem('sypos_device_id', devId);
  }
  return devId;
}

function base64UrlDecode(str) {
  let b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  while (b64.length % 4) b64 += '=';
  return (typeof forge !== 'undefined') ? forge.util.decode64(b64) : atob(b64);
}

function validateLicenseToken(rawKey) {
  const token = (rawKey || '').trim().replace(/[`\s\r\n]/g, '');
  if (!token) return { isValid: false, message: "Veuillez saisir votre clé de licence." };
  if (!token.startsWith('SYP1.')) return { isValid: false, message: "Clé invalide (doit commencer par 'SYP1.')." };

  const parts = token.split('.');
  if (parts.length !== 3) return { isValid: false, message: "Structure de clé corrompue." };

  try {
    const payloadBytes = base64UrlDecode(parts[1]);
    const sigBytes = base64UrlDecode(parts[2]);

    if (typeof forge === 'undefined') {
      return { isValid: false, message: "Module cryptographique non chargé. Vérifiez votre connexion." };
    }

    const der = forge.util.decode64(RSA_PUBLIC_KEY_B64);
    const asn1 = forge.asn1.fromDer(der);
    const pubKey = forge.pki.publicKeyFromAsn1(asn1);

    const md = forge.md.sha256.create();
    md.update(payloadBytes, 'raw');
    const isSignatureValid = pubKey.verify(md.digest().bytes(), sigBytes);

    if (!isSignatureValid) {
      return { isValid: false, message: "Signature invalide : cette clé est falsifiée ou corrompue." };
    }

    const payload = JSON.parse(payloadBytes);
    const targetDevId = (payload.devId || '').trim().toUpperCase();
    const currentDevId = getDeviceId().trim().toUpperCase();

    if (targetDevId !== currentDevId) {
      return { isValid: false, message: `Clé prévue pour l'appareil (${targetDevId}), incompatible avec (${currentDevId}).` };
    }

    const now = Date.now();
    if (payload.exp && payload.exp > 0 && now > payload.exp) {
      const expDate = new Date(payload.exp).toLocaleDateString('fr-FR');
      return { isValid: false, message: `Cette licence a expiré le ${expDate}.` };
    }

    const planLabel = payload.plan === 'LIFETIME' ? 'Définitive (À Vie)' : (payload.plan === 'ANNUAL' ? 'Annuelle Pro' : 'Commerciale');

    return {
      isValid: true,
      licenseType: planLabel,
      expiryDate: payload.exp || 0,
      shopName: payload.shop || STATE.settings.shopName,
      message: `✅ Licence ${planLabel} activée avec succès !`
    };
  } catch (err) {
    return { isValid: false, message: "Erreur de validation : " + err.message };
  }
}

function isLicenseActive() {
  if (!STATE.settings.isLicensed) return false;
  if (!STATE.settings.licenseKey) return false;
  const res = validateLicenseToken(STATE.settings.licenseKey);
  return res.isValid;
}

function showLicenseModal(closable = false) {
  const modal = document.getElementById('modalLicense');
  const btnClose = document.getElementById('btnCloseLicenseModal');
  const devIdEl = document.getElementById('licenseDialogDevId');
  if (devIdEl) devIdEl.textContent = getDeviceId();
  if (btnClose) btnClose.style.display = closable ? 'block' : 'none';
  modal.classList.add('active');
}

// ==========================================
// 9. GOOGLE SHEETS ASYNC REMOTE SYNC
// ==========================================
async function syncTicketToGoogleSheets(ticket) {
  if (!STATE.settings.googleSheetsSync || !STATE.settings.googleSheetsUrl) return;
  try {
    const payload = {
      type: "SALE",
      ticketNumber: ticket.number,
      date: ticket.date,
      sellerName: STATE.settings.sellerName || "",
      customerName: ticket.customerName || "Client Comptoir",
      items: ticket.items,
      subTotal: ticket.subTotal || ticket.totalAmount,
      discount: ticket.discount || 0,
      taxAmount: ticket.taxAmount || 0,
      totalAmount: ticket.totalAmount,
      paymentMethod: ticket.paymentMethod
    };
    fetch(STATE.settings.googleSheetsUrl, {
      method: "POST",
      mode: "no-cors",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }).catch(err => console.warn("Google Sheets sync warn:", err));
  } catch (e) {
    console.warn("Google Sheets sync error:", e);
  }
}

async function syncExpenseToGoogleSheets(expense) {
  if (!STATE.settings.googleSheetsSync || !STATE.settings.googleSheetsUrl) return;
  try {
    fetch(STATE.settings.googleSheetsUrl, {
      method: "POST",
      mode: "no-cors",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        type: "EXPENSE",
        reason: expense.reason,
        amount: expense.amount,
        date: expense.date
      })
    }).catch(err => console.warn("Google Sheets expense sync warn:", err));
  } catch (e) {
    console.warn("Google Sheets expense sync error:", e);
  }
}

// ==========================================
// 10. BACKUP & RESTORE UTILITIES
// ==========================================
function exportBackupJson() {
  const backup = {
    app: "SYPOS Mobile",
    version: "2.3",
    timestamp: Date.now(),
    date: new Date().toISOString(),
    settings: STATE.settings,
    products: STATE.products,
    categories: STATE.categories,
    tickets: STATE.tickets,
    customers: STATE.customers,
    expenses: STATE.expenses,
    promoCodes: STATE.promoCodes,
    heldSales: STATE.heldSales
  };

  const str = JSON.stringify(backup, null, 2);
  const blob = new Blob([str], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  const dStr = new Date().toISOString().split('T')[0];
  a.href = url;
  a.download = `SYPOS_Backup_${dStr}_${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  alert("✅ Sauvegarde exportée avec succès !");
}

function restoreBackupJson(file) {
  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const parsed = JSON.parse(e.target.result);
      if (!parsed.products || !parsed.settings) {
        alert("❌ Fichier de sauvegarde invalide ou corrompu !");
        return;
      }
      if (!confirm("⚠️ Attention : Restaurer cette sauvegarde va remplacer les données actuelles de la caisse. Continuer ?")) {
        return;
      }
      Object.assign(STATE.settings, parsed.settings || {});
      STATE.products = parsed.products || [];
      STATE.categories = parsed.categories || [];
      STATE.tickets = parsed.tickets || [];
      STATE.customers = (parsed.customers || []).map(c => ({
        id: c.id || ("c_" + Math.random()),
        name: c.name || "Client",
        phone: c.phone || "",
        email: c.email || "",
        notes: c.notes || "",
        totalSpent: c.totalSpent || 0,
        visitCount: c.visitCount || 0,
        lastPurchaseDate: c.lastPurchaseDate || null,
        createdAt: c.createdAt || Date.now()
      }));
      STATE.expenses = parsed.expenses || [];
      STATE.promoCodes = parsed.promoCodes || [];
      STATE.heldSales = parsed.heldSales || [];
      saveToStorage();
      renderPos();
      renderProducts();
      renderHistory();
      renderReports();
      renderCustomers();
      renderSettings();
      alert("🎉 Restauration terminée avec succès ! Vos données ont été réinjectées.");
    } catch (err) {
      alert("❌ Erreur lors de la lecture du fichier : " + err);
    }
  };
  reader.readAsText(file);
}

// ==========================================
// 11. WHATSAPP PNG & TEXT RECEIPT SHARING
// ==========================================
async function shareReceiptAsPng(ticket) {
  const el = document.getElementById('receiptPaperContent');
  if (!el) return;
  
  try {
    if (typeof html2canvas === 'undefined') {
      alert("⚠️ Conversion d'image indisponible. Utilisation du format texte.");
      shareReceiptAsText(ticket);
      return;
    }

    const canvas = await html2canvas(el, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false
    });

    canvas.toBlob(async (blob) => {
      if (!blob) {
        shareReceiptAsText(ticket);
        return;
      }
      
      const fileName = `Ticket_${ticket.number || 'SYPOS'}.png`;
      const file = new File([blob], fileName, { type: 'image/png' });

      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        try {
          await navigator.share({
            files: [file],
            title: `Ticket ${ticket.number} — ${STATE.settings.shopName}`,
            text: `Bonjour, voici votre ticket de caisse ${ticket.number} chez ${STATE.settings.shopName}. Merci pour votre confiance !`
          });
          return;
        } catch (shareErr) {
          if (shareErr.name === 'AbortError') return;
        }
      }

      // Download file fallback
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      const msg = `🧾 *Ticket N°: ${ticket.number} — ${STATE.settings.shopName}*\n💰 Total : ${ticket.totalAmount.toLocaleString('fr-FR')} CFA\n\n*(L'image PNG du ticket a été téléchargée sur votre téléphone pour l'envoyer directement en pièce jointe WhatsApp)*`;
      window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(msg)}`, '_blank');
    }, 'image/png');
  } catch (err) {
    console.error("Error generating receipt PNG:", err);
    shareReceiptAsText(ticket);
  }
}

function shareReceiptAsText(ticket) {
  let text = `🧾 *${STATE.settings.shopName.toUpperCase()}*\n`;
  if (STATE.settings.shopAddress) text += `📍 ${STATE.settings.shopAddress}\n`;
  if (STATE.settings.shopPhone) text += `📞 ${STATE.settings.shopPhone}\n`;
  text += `--------------------------------\n`;
  text += `Ticket N°: ${ticket.number}\n`;
  text += `Date: ${new Date(ticket.date).toLocaleString('fr-FR')}\n`;
  if (ticket.customerName) text += `Client: ${ticket.customerName}\n`;
  text += `--------------------------------\n`;
  ticket.items.forEach(i => {
    text += `${i.qty}x ${i.productName} = ${(i.price * i.qty).toLocaleString('fr-FR')} CFA\n`;
  });
  text += `--------------------------------\n`;
  text += `*TOTAL NET : ${ticket.totalAmount.toLocaleString('fr-FR')} CFA*\n`;
  text += `Règlement : ${(ticket.paymentMethod || 'ESPECES').toUpperCase()}\n`;
  text += `\n${STATE.settings.receiptFooter}`;

  window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`, '_blank');
}

function formatTimeAgo(timestamp) {
  if (!timestamp) return 'Jamais';
  const diff = Date.now() - timestamp;
  const days = Math.floor(diff / (24 * 60 * 60 * 1000));
  if (days === 0) return "Aujourd'hui";
  if (days === 1) return "Hier";
  if (days < 7) return `Il y a ${days} jours`;
  if (days < 30) return `Il y a ${Math.floor(days / 7)} sem.`;
  return `Il y a ${Math.floor(days / 30)} mois`;
}

// ==========================================
// 12. APP STARTUP & EVENT LISTENERS
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
  loadFromStorage();
  initTabs();

  // Populate initial views
  renderPos();
  renderProducts();
  renderHistory();
  renderReports();
  renderCustomers();
  renderTools();
  renderSettings();

  // Device ID in License Modal
  const devIdEl = document.getElementById('licenseDialogDevId');
  if (devIdEl) devIdEl.textContent = getDeviceId();

  // Copy Device ID button
  document.getElementById('btnCopyDeviceId').addEventListener('click', () => {
    navigator.clipboard.writeText(getDeviceId()).then(() => {
      alert("✅ Identifiant Appareil copié dans le presse-papier !");
    }).catch(() => {
      prompt("Copiez votre ID Appareil :", getDeviceId());
    });
  });

  // WhatsApp Order License button
  document.getElementById('btnWhatsAppLicense').addEventListener('click', () => {
    const id = getDeviceId();
    const phone = "2250758245530";
    const msg = `Bonjour SYPOS Mobile, je souhaite commander / activer une licence pour mon commerce.\n\nMon ID Appareil : *${id}*\nNom Boutique : ${STATE.settings.shopName}`;
    window.open(`https://api.whatsapp.com/send?phone=${phone}&text=${encodeURIComponent(msg)}`, '_blank');
  });

  // Validate License Key button
  document.getElementById('btnValidateLicense').addEventListener('click', () => {
    const key = document.getElementById('inputLicenseKey').value.trim();
    const feedback = document.getElementById('licenseFeedbackMsg');
    const res = validateLicenseToken(key);

    if (res.isValid) {
      STATE.settings.isLicensed = true;
      STATE.settings.licenseKey = key;
      STATE.settings.licenseType = res.licenseType;
      STATE.settings.licenseExpiryDate = res.expiryDate;
      if (res.shopName) STATE.settings.shopName = res.shopName;
      saveToStorage();
      renderSettings();

      feedback.style.display = 'block';
      feedback.style.background = '#e8f5e9';
      feedback.style.color = '#2e7d32';
      feedback.textContent = res.message;

      setTimeout(() => {
        document.getElementById('modalLicense').classList.remove('active');
        feedback.style.display = 'none';
      }, 1500);
    } else {
      feedback.style.display = 'block';
      feedback.style.background = '#ffebee';
      feedback.style.color = '#c62828';
      feedback.textContent = res.message;
    }
  });

  // Scan License QR
  document.getElementById('btnScanLicenseQr').addEventListener('click', () => {
    document.getElementById('modalScanner').classList.add('active');
    const qr = new Html5Qrcode("qr-reader");
    STATE.html5QrCode = qr;
    qr.start({ facingMode: "environment" }, { fps: 10, qrbox: { width: 250, height: 250 } }, (decodedText) => {
      document.getElementById('inputLicenseKey').value = decodedText;
      qr.stop().then(() => {
        document.getElementById('modalScanner').classList.remove('active');
        document.getElementById('btnValidateLicense').click();
      });
    }).catch(err => {
      alert("Erreur caméra : " + err);
      document.getElementById('modalScanner').classList.remove('active');
    });
  });

  // Demo mode
  const btnDemo = document.getElementById('btnDemoMode');
  if (btnDemo) {
    btnDemo.addEventListener('click', () => {
      document.getElementById('modalLicense').classList.remove('active');
    });
  }

  // Settings: Open License Modal
  const btnOpenLicense = document.getElementById('btnOpenLicenseModal');
  if (btnOpenLicense) {
    btnOpenLicense.addEventListener('click', () => showLicenseModal(false));
  }

  // Live POS search
  document.getElementById('posSearchInput').addEventListener('input', renderPos);

  // Floating Cart Click
  document.getElementById('floatingCartBar').addEventListener('click', () => {
    renderCartModal();
    document.getElementById('modalCart').classList.add('active');
  });

  // Modal Close buttons
  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', () => {
      const modal = btn.closest('.modal-overlay');
      if (modal) modal.classList.remove('active');
    });
  });

  // Clear Cart
  document.getElementById('btnClearCartBtn').addEventListener('click', () => {
    requestAdminPin("Vider complètement le panier", () => {
      STATE.cart = [];
      STATE.appliedPromo = null;
      STATE.selectedCustomerId = "";
      saveToStorage();
      renderCartModal();
      updateFloatingCart();
      document.getElementById('modalCart').classList.remove('active');
    });
  });

  // Quick Customer Selection in Cart
  const cartCustSelect = document.getElementById('cartCustomerSelect');
  if (cartCustSelect) {
    cartCustSelect.addEventListener('change', (e) => {
      STATE.selectedCustomerId = e.target.value;
      updateCartCustomerBadge();
    });
  }

  // Quick Add Customer from Cart button
  const btnQuickAddFromCart = document.getElementById('btnQuickAddCustFromCart');
  if (btnQuickAddFromCart) {
    btnQuickAddFromCart.addEventListener('click', () => {
      pendingQuickAddFromCart = true;
      openCustomerModal(null);
    });
  }

  // Checkout button
  document.getElementById('btnGoToCheckout').addEventListener('click', () => {
    document.getElementById('modalCart').classList.remove('active');
    const total = getCartTotal();
    document.getElementById('payAmountDue').textContent = total.toLocaleString('fr-FR') + ' CFA';
    document.getElementById('payCashReceivedInput').value = '';
    document.getElementById('payChangeDueBox').style.display = 'none';

    // Show assigned customer if any
    const assignedBadge = document.getElementById('payAssignedCustomerBadge');
    if (assignedBadge) {
      const cust = STATE.customers.find(c => c.id === STATE.selectedCustomerId);
      if (cust) {
        assignedBadge.style.display = 'block';
        assignedBadge.textContent = `👤 Client associé : ${cust.name}`;
      } else {
        assignedBadge.style.display = 'none';
      }
    }

    document.getElementById('modalPayment').classList.add('active');
  });

  // Payment Method change
  document.getElementById('payPaymentMethod').addEventListener('change', (e) => {
    const isCash = e.target.value === 'cash';
    document.getElementById('payCashReceivedGroup').style.display = isCash ? 'block' : 'none';
  });

  // Cash Received live calculation
  document.getElementById('payCashReceivedInput').addEventListener('input', (e) => {
    const rec = parseFloat(e.target.value) || 0;
    const total = getCartTotal();
    const box = document.getElementById('payChangeDueBox');
    if (rec >= total && total > 0) {
      box.style.display = 'block';
      document.getElementById('payChangeDueAmount').textContent = (rec - total).toLocaleString('fr-FR') + ' CFA';
    } else {
      box.style.display = 'none';
    }
  });

  // Confirm Payment & Record Sale
  document.getElementById('btnConfirmPayment').addEventListener('click', () => {
    const method = document.getElementById('payPaymentMethod').value;
    const total = getCartTotal();
    const rec = parseFloat(document.getElementById('payCashReceivedInput').value) || total;
    const change = Math.max(0, rec - total);

    const ticketNumber = "TK-" + String(STATE.tickets.length + 1).padStart(5, '0');
    const assignedCustomer = STATE.customers.find(c => c.id === STATE.selectedCustomerId);

    const ticket = {
      id: "t_" + Date.now(),
      number: ticketNumber,
      date: new Date().toISOString(),
      items: STATE.cart.map(i => ({
        productId: i.product.id,
        productName: i.product.name,
        qty: i.qty,
        price: i.product.salePrice,
        costPrice: i.product.costPrice || 0
      })),
      totalAmount: total,
      paymentMethod: method,
      cashReceived: method === 'cash' ? rec : null,
      changeGiven: method === 'cash' ? change : null,
      customerId: assignedCustomer ? assignedCustomer.id : null,
      customerName: assignedCustomer ? assignedCustomer.name : null,
      status: 'completed'
    };

    // Deduct stocks
    STATE.cart.forEach(i => {
      const prod = STATE.products.find(p => p.id === i.product.id);
      if (prod) prod.stock -= i.qty;
    });

    // Update Customer CRM statistics if attached
    if (assignedCustomer) {
      assignedCustomer.totalSpent = (assignedCustomer.totalSpent || 0) + total;
      assignedCustomer.visitCount = (assignedCustomer.visitCount || 0) + 1;
      assignedCustomer.lastPurchaseDate = Date.now();
    }

    STATE.tickets.unshift(ticket);
    STATE.cart = [];
    STATE.appliedPromo = null;
    STATE.selectedCustomerId = "";
    saveToStorage();

    // Async Google Sheets Sync
    syncTicketToGoogleSheets(ticket);

    document.getElementById('modalPayment').classList.remove('active');
    updateFloatingCart();
    renderPos();
    renderCustomers();

    // Show Receipt Modal
    document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(ticket);
    document.getElementById('modalReceipt').classList.add('active');
  });

  // Direct Bluetooth ESC/POS Print button
  const btnBleDirect = document.getElementById('btnPrintBleDirectBtn');
  if (btnBleDirect) {
    btnBleDirect.addEventListener('click', async () => {
      if (STATE.tickets.length === 0) return;
      const t = STATE.tickets[0];
      const plainText = generateReceiptPlainText(t);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) {
        alert("✅ Ticket imprimé avec succès !");
      }
    });
  }

  // Print Receipt Button (Standard AirPrint/PDF)
  document.getElementById('btnPrintReceiptBtn').addEventListener('click', () => {
    window.print();
  });

  // WhatsApp Share Receipt as PNG Image
  const btnShareWhatsAppPng = document.getElementById('btnShareWhatsAppPngBtn');
  if (btnShareWhatsAppPng) {
    btnShareWhatsAppPng.addEventListener('click', () => {
      if (STATE.tickets.length === 0) return;
      shareReceiptAsPng(STATE.tickets[0]);
    });
  }

  // WhatsApp Share Receipt as Text
  const btnShareWhatsAppText = document.getElementById('btnShareWhatsAppTextBtn');
  if (btnShareWhatsAppText) {
    btnShareWhatsAppText.addEventListener('click', () => {
      if (STATE.tickets.length === 0) return;
      shareReceiptAsText(STATE.tickets[0]);
    });
  }

  // Admin PIN submission
  document.getElementById('btnSubmitPin').addEventListener('click', () => {
    const entered = document.getElementById('pinInputCode').value;
    if (entered === STATE.settings.adminPin) {
      document.getElementById('modalPin').classList.remove('active');
      if (STATE.pendingPinAction) {
        STATE.pendingPinAction();
        STATE.pendingPinAction = null;
      }
    } else {
      alert("❌ Code PIN incorrect !");
    }
  });

  // Barcode Camera Scanner in POS tab
  const btnOpenScan = document.getElementById('btnOpenScanner');
  if (btnOpenScan) {
    btnOpenScan.addEventListener('click', () => {
      document.getElementById('modalScanner').classList.add('active');
      if (STATE.html5QrCode) {
        try { STATE.html5QrCode.stop(); } catch(e) {}
      }
      const qr = new Html5Qrcode("qr-reader");
      STATE.html5QrCode = qr;
      
      const config = {
        fps: 15,
        qrbox: { width: 260, height: 160 },
        aspectRatio: 1.777778
      };

      qr.start({ facingMode: "environment" }, config, (decodedText) => {
        if (navigator.vibrate) navigator.vibrate(80);
        
        const matched = STATE.products.find(p => p.barcode && (p.barcode.trim() === decodedText.trim() || decodedText.includes(p.barcode.trim())));
        if (matched) {
          addToCart(matched);
          alert(`✅ Article scanné : ${matched.name} (+1 au panier)`);
        } else {
          document.getElementById('posSearchInput').value = decodedText;
          renderPos();
        }

        qr.stop().then(() => {
          document.getElementById('modalScanner').classList.remove('active');
        }).catch(() => {
          document.getElementById('modalScanner').classList.remove('active');
        });
      }).catch(err => {
        console.error("Camera start error:", err);
        alert("⚠️ Impossible d'ouvrir la caméra : vérifiez les autorisations de votre navigateur.");
        document.getElementById('modalScanner').classList.remove('active');
      });
    });
  }

  // Close scanner button
  const btnCloseScan = document.getElementById('btnCloseScanner');
  if (btnCloseScan) {
    btnCloseScan.addEventListener('click', () => {
      if (STATE.html5QrCode) {
        try {
          STATE.html5QrCode.stop().then(() => {
            document.getElementById('modalScanner').classList.remove('active');
          }).catch(() => {
            document.getElementById('modalScanner').classList.remove('active');
          });
        } catch(e) {
          document.getElementById('modalScanner').classList.remove('active');
        }
      } else {
        document.getElementById('modalScanner').classList.remove('active');
      }
    });
  }

  // Bluetooth persistent auto-reconnect on startup & on focus
  async function autoReconnectBluetooth() {
    if (!navigator.bluetooth) return;
    if (navigator.bluetooth.getDevices) {
      try {
        const devices = await navigator.bluetooth.getDevices();
        if (devices && devices.length > 0) {
          const lastId = localStorage.getItem('last_ble_device_id');
          const target = devices.find(d => d.id === lastId) || devices[0];
          if (target && target.gatt && !target.gatt.connected) {
            console.log("Auto-reconnecting to thermal printer:", target.name);
            const server = await target.gatt.connect();
            const services = await server.getPrimaryServices();
            for (let s of services) {
              const chars = await s.getCharacteristics();
              for (let c of chars) {
                if (c.properties.write || c.properties.writeWithoutResponse) {
                  STATE.bleDevice = target;
                  STATE.bleCharacteristic = c;
                  console.log("Bluetooth thermal printer reconnected silently!");
                  break;
                }
              }
            }
          }
        }
      } catch (err) {
        console.warn("Silent BLE reconnect error:", err);
      }
    }
  }

  autoReconnectBluetooth();
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') autoReconnectBluetooth();
  });
  window.addEventListener('focus', autoReconnectBluetooth);

  document.getElementById('btnHeaderBle').addEventListener('click', connectBluetoothPrinter);

  // Settings Save button
  document.getElementById('btnSaveSettings').addEventListener('click', () => {
    STATE.settings.shopName = document.getElementById('setShopName').value.trim() || "SYPOS COMMERCE";
    STATE.settings.shopAddress = document.getElementById('setShopAddress').value.trim();
    STATE.settings.shopPhone = document.getElementById('setShopPhone').value.trim();
    STATE.settings.sellerName = document.getElementById('setSellerName').value.trim() || "Vendeur";
    STATE.settings.receiptFooter = document.getElementById('setReceiptFooter').value.trim() || "Merci de votre visite !";
    STATE.settings.showPublisherSignature = document.getElementById('setShowSignature').checked;
    STATE.settings.taxEnabled = document.getElementById('setTaxEnabled').checked;
    STATE.settings.adminPin = document.getElementById('setAdminPin').value.trim() || "1234";
    STATE.settings.printerWidth = document.getElementById('setPrinterWidth').value || "58";
    
    // Google Sheets Settings
    STATE.settings.googleSheetsUrl = (document.getElementById('setGoogleSheetsUrl') ? document.getElementById('setGoogleSheetsUrl').value : '').trim();
    STATE.settings.googleSheetsSync = document.getElementById('setGoogleSheetsSync') ? document.getElementById('setGoogleSheetsSync').checked : false;

    saveToStorage();
    alert("✅ Paramètres enregistrés avec succès !");
  });

  // Settings: Reset Database
  const btnReset = document.getElementById('btnResetDatabase');
  if (btnReset) {
    btnReset.addEventListener('click', () => {
      requestAdminPin("Réinitialiser complètement la base de données", () => {
        if (confirm("⚠️ Êtes-vous ABSOLUMENT certain de vouloir effacer toutes les données ?")) {
          localStorage.removeItem('sypos_data_v2');
          location.reload();
        }
      });
    });
  }

  // Settings: Backup & Restore buttons
  const btnExportBackup = document.getElementById('btnExportBackupJson');
  if (btnExportBackup) {
    btnExportBackup.addEventListener('click', exportBackupJson);
  }

  const btnImportBackup = document.getElementById('btnImportBackupJson');
  const inputRestoreFile = document.getElementById('inputRestoreBackupFile');
  if (btnImportBackup && inputRestoreFile) {
    btnImportBackup.addEventListener('click', () => {
      inputRestoreFile.click();
    });
    inputRestoreFile.addEventListener('change', (e) => {
      if (e.target.files && e.target.files[0]) {
        restoreBackupJson(e.target.files[0]);
        e.target.value = '';
      }
    });
  }

  const btnShareBackupWA = document.getElementById('btnShareBackupWhatsApp');
  if (btnShareBackupWA) {
    btnShareBackupWA.addEventListener('click', () => {
      exportBackupJson();
      const phone = STATE.settings.shopPhone ? STATE.settings.shopPhone.replace(/[^0-9]/g, '') : '';
      const text = `💾 *SAUVEGARDE SYPOS MOBILE*\nBoutique : ${STATE.settings.shopName}\nDate : ${new Date().toLocaleString('fr-FR')}\nLe fichier de sauvegarde a été téléchargé pour être archivé.`;
      window.open(`https://api.whatsapp.com/send?${phone ? 'phone=' + phone + '&' : ''}text=${encodeURIComponent(text)}`, '_blank');
    });
  }

  // Google Sheets Test Connection button
  const btnTestSheets = document.getElementById('btnTestGoogleSheets');
  if (btnTestSheets) {
    btnTestSheets.addEventListener('click', async () => {
      const url = (document.getElementById('setGoogleSheetsUrl') ? document.getElementById('setGoogleSheetsUrl').value : '').trim();
      if (!url) {
        alert("⚠️ Veuillez d'abord renseigner l'URL Webhook Apps Script !");
        return;
      }
      try {
        btnTestSheets.textContent = "⏳ Test en cours...";
        await fetch(url, {
          method: "POST",
          mode: "no-cors",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ type: "TEST" })
        });
        btnTestSheets.textContent = "🧪 Tester Connexion";
        alert("✅ Signal envoyé à Google Apps Script ! Vérifiez votre classeur Google Sheets.");
      } catch (err) {
        btnTestSheets.textContent = "🧪 Tester Connexion";
        alert("❌ Erreur de connexion : " + err.message);
      }
    });
  }

  // Google Sheets Apps Script Modal & Copy button
  const btnOpenAppsScriptModal = document.getElementById('btnOpenAppsScriptModal');
  const modalAppsScript = document.getElementById('modalAppsScript');
  const txtAppsScriptCode = document.getElementById('txtAppsScriptCode');
  const btnCopyAppsScript = document.getElementById('btnCopyAppsScriptCodeSubmit');

  if (btnOpenAppsScriptModal && modalAppsScript) {
    btnOpenAppsScriptModal.addEventListener('click', () => {
      if (txtAppsScriptCode) txtAppsScriptCode.value = APPS_SCRIPT_TEMPLATE;
      modalAppsScript.classList.add('active');
    });
  }

  if (btnCopyAppsScript && txtAppsScriptCode) {
    btnCopyAppsScript.addEventListener('click', () => {
      navigator.clipboard.writeText(APPS_SCRIPT_TEMPLATE).then(() => {
        alert("📋 Code Google Apps Script copié dans le presse-papier !");
      }).catch(() => {
        txtAppsScriptCode.select();
        document.execCommand('copy');
        alert("📋 Code copié !");
      });
    });
  }

  // Apply promo code
  const btnPromo = document.getElementById('btnApplyPromo');
  if (btnPromo) {
    btnPromo.addEventListener('click', () => {
      const code = document.getElementById('cartPromoInput').value.trim().toUpperCase();
      if (!code) return;
      if (code === "SOLDES10" || code === "PROMO10") {
        STATE.appliedPromo = { code, discountPercent: 10 };
        document.getElementById('cartPromoBadge').style.display = 'block';
        document.getElementById('cartPromoBadge').textContent = `✅ Remise ${code} appliquée (-10%)`;
      } else if (code === "REMISE500") {
        STATE.appliedPromo = { code, discountAmount: 500 };
        document.getElementById('cartPromoBadge').style.display = 'block';
        document.getElementById('cartPromoBadge').textContent = `✅ Remise ${code} appliquée (-500 CFA)`;
      } else {
        alert("❌ Code promo invalide ou expiré !");
      }
      renderCartModal();
    });
  }

  // Held Sales functionality
  function renderHeldSales() {
    const container = document.getElementById('heldSalesListContainer');
    container.innerHTML = '';
    if (!STATE.heldSales || STATE.heldSales.length === 0) {
      container.innerHTML = '<div style="text-align:center; padding: 20px; color: var(--text-muted);">Aucune vente en attente.</div>';
      return;
    }
    STATE.heldSales.forEach((h, index) => {
      const card = document.createElement('div');
      card.className = 'card-group';
      card.style.marginBottom = '10px';
      const itemsSummary = h.cart.map(i => `${i.product.name} (x${i.qty})`).join(', ');
      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
          <div style="font-weight:700; font-size:14px;">Panier N°${index + 1} (${h.cart.length} articles)</div>
          <div style="font-size:12px; color:var(--text-muted);">${new Date(h.date).toLocaleTimeString('fr-FR')}</div>
        </div>
        <div style="font-size:12px; color:var(--text-muted); margin-bottom:10px;">${itemsSummary}</div>
        <div style="display:flex; gap:8px;">
          <button class="btn-main resume-held-btn" style="margin:0; flex:1; font-size:13px; height:38px;">▶️ Reprendre</button>
          <button class="btn-main btn-danger del-held-btn" style="margin:0; width:auto; font-size:13px; height:38px; padding:0 12px;">🗑️</button>
        </div>
      `;

      card.querySelector('.resume-held-btn').addEventListener('click', () => {
        if (STATE.cart.length > 0) {
          if (!confirm("⚠️ Votre panier actuel contient déjà des articles. Les remplacer par la vente en attente ?")) return;
        }
        STATE.cart = h.cart;
        STATE.appliedPromo = h.appliedPromo;
        STATE.selectedCustomerId = h.selectedCustomerId || "";
        STATE.heldSales.splice(index, 1);
        saveToStorage();
        updateFloatingCart();
        renderPos();
        document.getElementById('modalHeldSales').classList.remove('active');
        renderCartModal();
        document.getElementById('modalCart').classList.add('active');
      });

      card.querySelector('.del-held-btn').addEventListener('click', () => {
        if (confirm("Supprimer cette vente en attente ?")) {
          STATE.heldSales.splice(index, 1);
          saveToStorage();
          renderHeldSales();
          updateFloatingCart();
        }
      });

      container.appendChild(card);
    });
  }

  const btnHeldHeader = document.getElementById('btnHeldSales');
  if (btnHeldHeader) {
    btnHeldHeader.addEventListener('click', () => {
      renderHeldSales();
      document.getElementById('modalHeldSales').classList.add('active');
    });
  }

  const btnHoldCart = document.getElementById('btnHoldCurrentSale');
  const btnHoldCartModal = document.getElementById('btnHoldCurrentCartBtn');
  function handleHoldCartAction() {
    if (STATE.cart.length === 0) {
      alert("⚠️ Le panier est vide.");
      return;
    }
    if (!STATE.heldSales) STATE.heldSales = [];
    STATE.heldSales.unshift({
      id: "held_" + Date.now(),
      date: new Date().toISOString(),
      cart: [...STATE.cart],
      appliedPromo: STATE.appliedPromo,
      selectedCustomerId: STATE.selectedCustomerId
    });
    STATE.cart = [];
    STATE.appliedPromo = null;
    STATE.selectedCustomerId = "";
    saveToStorage();
    updateFloatingCart();
    renderPos();
    document.getElementById('modalCart').classList.remove('active');
    document.getElementById('modalHeldSales').classList.remove('active');
    alert("⏸️ Panier mis en attente ! Vous pouvez le reprendre à tout moment via le bouton ⏸️ en haut.");
  }
  if (btnHoldCart) btnHoldCart.addEventListener('click', handleHoldCartAction);
  if (btnHoldCartModal) btnHoldCartModal.addEventListener('click', handleHoldCartAction);

  // Trigger Z-Report Modal
  const btnTriggerZ = document.getElementById('btnTriggerZReport');
  if (btnTriggerZ) {
    btnTriggerZ.addEventListener('click', () => {
      requestAdminPin("Clôturer la caisse (Rapport Z)", () => {
        const z = getZReportData();
        document.getElementById('zReportPaperContent').innerHTML = generateZReportHtml(z);
        document.getElementById('modalZReport').classList.add('active');
      });
    });
  }

  // Print Z-Report BLE
  const btnPrintZBle = document.getElementById('btnPrintZReportBleBtn');
  if (btnPrintZBle) {
    btnPrintZBle.addEventListener('click', async () => {
      const z = getZReportData();
      const plainText = generateZReportPlainText(z);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Rapport Z imprimé avec succès !");
    });
  }

  // Export Z-Report PDF
  const btnExportZPdf = document.getElementById('btnExportZReportPdf');
  if (btnExportZPdf) {
    btnExportZPdf.addEventListener('click', () => {
      const z = getZReportData();
      const win = window.open('', '_blank');
      win.document.write(`
        <html><head><title>Rapport Z de Caisse</title>
        <style>body { font-family: sans-serif; padding: 20px; max-width: 400px; margin: auto; }</style>
        </head><body>
        ${generateZReportHtml(z)}
        <script>window.print();</script></body></html>
      `);
      win.document.close();
    });
  }

  // Export Z-Report CSV
  const btnExportZCsv = document.getElementById('btnExportZReportCsv');
  if (btnExportZCsv) {
    btnExportZCsv.addEventListener('click', () => {
      const z = getZReportData();
      const headers = ["Poste", "Montant"];
      const rows = [
        ["Date Cloture", z.dateText],
        ["Total Ventes", z.totalSales + " CFA"],
        ["Nombre de Tickets", z.ticketsCount],
        ["Total Depenses", z.totalExpenses + " CFA"],
        ["Especes (Cash)", z.cashSales + " CFA"],
        ["Wave", z.waveSales + " CFA"],
        ["Orange Money", z.omSales + " CFA"],
        ["MTN MoMo", z.momoSales + " CFA"],
        ["Carte Bancaire", z.cardSales + " CFA"],
        ["Net Cash en Caisse", z.netCashInDrawer + " CFA"]
      ];
      exportToCsv(`Rapport_Z_${Date.now()}.csv`, headers, rows);
    });
  }

  // ==========================================
  // PRODUCT CRUD (CATALOGUE)
  // ==========================================
  function openProductModal(prod = null) {
    const isEdit = !!prod;
    document.getElementById('modalProductTitle').textContent = isEdit ? "📦 Modifier l'Article" : "📦 Ajouter un Article";
    document.getElementById('prodEditId').value = isEdit ? prod.id : "";
    document.getElementById('prodInputName').value = isEdit ? prod.name : "";
    document.getElementById('prodInputCostPrice').value = isEdit ? (prod.costPrice || "") : "";
    document.getElementById('prodInputSalePrice').value = isEdit ? prod.salePrice : "";
    document.getElementById('prodInputStock').value = isEdit ? prod.stock : "";
    document.getElementById('prodInputBarcode').value = isEdit ? (prod.barcode || "") : "";

    // Update categories select
    const catSelect = document.getElementById('prodInputCategory');
    catSelect.innerHTML = '';
    const cats = STATE.categories && STATE.categories.length > 0 ? STATE.categories : [
      { id: "1", name: "Alimentation" },
      { id: "2", name: "Boissons" },
      { id: "3", name: "Hygiène" },
      { id: "4", name: "Divers" }
    ];
    cats.forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.id;
      opt.textContent = c.name;
      if (isEdit && prod.catId === c.id) opt.selected = true;
      catSelect.appendChild(opt);
    });

    const delBtn = document.getElementById('btnDeleteProductModal');
    if (delBtn) delBtn.style.display = isEdit ? 'block' : 'none';

    document.getElementById('modalProduct').classList.add('active');
  }

  const btnAddProd = document.getElementById('btnAddNewProduct');
  if (btnAddProd) {
    btnAddProd.addEventListener('click', () => openProductModal(null));
  }

  const btnSaveProd = document.getElementById('btnSaveProductSubmit');
  if (btnSaveProd) {
    btnSaveProd.addEventListener('click', () => {
      const id = document.getElementById('prodEditId').value;
      const name = document.getElementById('prodInputName').value.trim();
      const catId = document.getElementById('prodInputCategory').value;
      const costPrice = parseFloat(document.getElementById('prodInputCostPrice').value) || 0;
      const salePrice = parseFloat(document.getElementById('prodInputSalePrice').value) || 0;
      const stock = parseInt(document.getElementById('prodInputStock').value, 10) || 0;
      const barcode = document.getElementById('prodInputBarcode').value.trim();

      if (!name) {
        alert("⚠️ Veuillez saisir la désignation de l'article !");
        return;
      }
      if (salePrice <= 0) {
        alert("⚠️ Veuillez saisir un prix de vente valide !");
        return;
      }

      if (id) {
        const p = STATE.products.find(x => x.id === id);
        if (p) {
          p.name = name;
          p.catId = catId;
          p.costPrice = costPrice;
          p.salePrice = salePrice;
          p.stock = stock;
          p.barcode = barcode;
        }
      } else {
        const newP = {
          id: "p_" + Date.now(),
          name,
          catId,
          costPrice,
          salePrice,
          stock,
          barcode
        };
        STATE.products.unshift(newP);
      }

      saveToStorage();
      renderProducts();
      renderPos();
      renderTools();
      document.getElementById('modalProduct').classList.remove('active');
      alert("✅ Article enregistré avec succès !");
    });
  }

  const btnDelProdModal = document.getElementById('btnDeleteProductModal');
  if (btnDelProdModal) {
    btnDelProdModal.addEventListener('click', () => {
      const id = document.getElementById('prodEditId').value;
      if (id && confirm("Êtes-vous sûr de vouloir supprimer cet article du catalogue ?")) {
        STATE.products = STATE.products.filter(p => p.id !== id);
        saveToStorage();
        renderProducts();
        renderPos();
        renderTools();
        document.getElementById('modalProduct').classList.remove('active');
      }
    });
  }

  const btnAddCat = document.getElementById('btnAddCategoryPrompt');
  if (btnAddCat) {
    btnAddCat.addEventListener('click', () => {
      const catName = prompt("Nom de la nouvelle catégorie :");
      if (catName && catName.trim()) {
        const newCat = {
          id: "cat_" + Date.now(),
          name: catName.trim(),
          color: "#0D9488"
        };
        if (!STATE.categories) STATE.categories = [];
        STATE.categories.push(newCat);
        saveToStorage();
        openProductModal(STATE.products.find(x => x.id === document.getElementById('prodEditId').value));
      }
    });
  }

  // Barcode Scanner inside Product Modal (Preserves all fields without wiping)
  const btnScanProdBarcode = document.getElementById('btnScanBarcodeForProduct');
  if (btnScanProdBarcode) {
    btnScanProdBarcode.addEventListener('click', () => {
      document.getElementById('modalScanner').classList.add('active');
      const qr = new Html5Qrcode("qr-reader");
      STATE.html5QrCode = qr;
      qr.start({ facingMode: "environment" }, { fps: 10, qrbox: { width: 250, height: 250 } }, (decodedText) => {
        document.getElementById('prodInputBarcode').value = decodedText;
        qr.stop().then(() => {
          document.getElementById('modalScanner').classList.remove('active');
        }).catch(() => {
          document.getElementById('modalScanner').classList.remove('active');
        });
      }).catch(err => {
        alert("Impossible d'ouvrir la caméra : " + err);
        document.getElementById('modalScanner').classList.remove('active');
      });
    });
  }

  // ==========================================
  // EXPENSES (SORTIES DE CAISSE)
  // ==========================================
  const btnAddExp = document.getElementById('btnAddExpense');
  if (btnAddExp) {
    btnAddExp.addEventListener('click', () => {
      document.getElementById('expenseInputReason').value = '';
      document.getElementById('expenseInputAmount').value = '';
      document.getElementById('modalExpense').classList.add('active');
    });
  }

  const btnSaveExp = document.getElementById('btnSaveExpenseSubmit');
  if (btnSaveExp) {
    btnSaveExp.addEventListener('click', () => {
      const reason = document.getElementById('expenseInputReason').value.trim();
      const amount = parseFloat(document.getElementById('expenseInputAmount').value) || 0;
      if (!reason || amount <= 0) {
        alert("⚠️ Veuillez renseigner le motif et un montant valide !");
        return;
      }
      if (!STATE.expenses) STATE.expenses = [];
      const exp = {
        id: "e_" + Date.now(),
        reason,
        amount,
        date: new Date().toISOString()
      };
      STATE.expenses.unshift(exp);
      saveToStorage();
      
      // Async Google Sheets Sync
      syncExpenseToGoogleSheets(exp);

      renderReports();
      document.getElementById('modalExpense').classList.remove('active');
      alert("💸 Dépense enregistrée avec succès !");
    });
  }

  // ==========================================
  // CUSTOMER CRM CRUD & RELANCES
  // ==========================================
  function openCustomerModal(cust = null) {
    const isEdit = !!cust;
    document.getElementById('modalCustomerTitle').textContent = isEdit ? "👤 Modifier la Fiche Client" : "👤 Nouveau Client";
    document.getElementById('custEditId').value = isEdit ? cust.id : "";
    document.getElementById('custInputName').value = isEdit ? cust.name : "";
    document.getElementById('custInputPhone').value = isEdit ? cust.phone : "";
    document.getElementById('custInputEmail').value = isEdit ? (cust.email || "") : "";
    document.getElementById('custInputNotes').value = isEdit ? (cust.notes || "") : "";

    const btnDel = document.getElementById('btnDeleteCustomerModal');
    if (btnDel) btnDel.style.display = isEdit ? 'block' : 'none';

    document.getElementById('modalCustomer').classList.add('active');
  }

  const btnAddCust = document.getElementById('btnAddCustomer');
  if (btnAddCust) {
    btnAddCust.addEventListener('click', () => {
      pendingQuickAddFromCart = false;
      openCustomerModal(null);
    });
  }

  const btnSaveCust = document.getElementById('btnSaveCustomerSubmit');
  if (btnSaveCust) {
    btnSaveCust.addEventListener('click', () => {
      const id = document.getElementById('custEditId').value;
      const name = document.getElementById('custInputName').value.trim();
      const phone = document.getElementById('custInputPhone').value.trim();
      const email = document.getElementById('custInputEmail').value.trim();
      const notes = document.getElementById('custInputNotes').value.trim();

      if (!name) {
        alert("⚠️ Veuillez saisir le nom du client !");
        return;
      }
      if (!phone) {
        alert("⚠️ Veuillez saisir le numéro de téléphone / WhatsApp !");
        return;
      }

      if (!STATE.customers) STATE.customers = [];

      let savedCustomerId = id;

      if (id) {
        const c = STATE.customers.find(x => x.id === id);
        if (c) {
          c.name = name;
          c.phone = phone;
          c.email = email;
          c.notes = notes;
        }
      } else {
        const newCust = {
          id: "c_" + Date.now(),
          name,
          phone,
          email,
          notes,
          totalSpent: 0,
          visitCount: 0,
          lastPurchaseDate: null,
          createdAt: Date.now()
        };
        STATE.customers.unshift(newCust);
        savedCustomerId = newCust.id;
      }

      saveToStorage();
      renderCustomers();
      populateCartCustomerSelect();
      document.getElementById('modalCustomer').classList.remove('active');

      if (pendingQuickAddFromCart) {
        STATE.selectedCustomerId = savedCustomerId;
        populateCartCustomerSelect();
        updateCartCustomerBadge();
        pendingQuickAddFromCart = false;
      }

      alert("👤 Client enregistré avec succès !");
    });
  }

  const btnDelCust = document.getElementById('btnDeleteCustomerModal');
  if (btnDelCust) {
    btnDelCust.addEventListener('click', () => {
      const id = document.getElementById('custEditId').value;
      if (id && confirm("Êtes-vous certain de vouloir supprimer ce client de la base ?")) {
        STATE.customers = STATE.customers.filter(c => c.id !== id);
        if (STATE.selectedCustomerId === id) STATE.selectedCustomerId = "";
        saveToStorage();
        renderCustomers();
        populateCartCustomerSelect();
        document.getElementById('modalCustomer').classList.remove('active');
      }
    });
  }

  // CRM Filter Pills
  document.querySelectorAll('.cust-filter-pill').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.cust-filter-pill').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentCustomerFilter = btn.dataset.filter || 'all';
      renderCustomers();
    });
  });

  const custSearchInput = document.getElementById('custSearchInput');
  if (custSearchInput) {
    custSearchInput.addEventListener('input', renderCustomers);
  }

  // Customer Purchase History Modal
  let activeHistoryCustomer = null;
  function openCustomerHistoryModal(cust) {
    activeHistoryCustomer = cust;
    const modal = document.getElementById('modalCustomerHistory');
    const profileEl = document.getElementById('custHistProfileCard');
    const listEl = document.getElementById('custHistTicketsList');
    if (!modal || !profileEl || !listEl) return;

    const tickets = STATE.tickets.filter(t => t.customerId === cust.id || (t.customerName && t.customerName.toLowerCase() === cust.name.toLowerCase()));
    
    profileEl.innerHTML = `
      <div style="font-size: 16px; font-weight: 800; color: var(--primary);">${cust.name}</div>
      <div style="font-size: 12px; color: var(--text-muted); margin-top: 2px;">📞 ${cust.phone || 'Sans numéro'} ${cust.email ? ' | ✉️ ' + cust.email : ''}</div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 10px;">
        <div style="background: var(--bg-card); padding: 8px; border-radius: 8px;">
          <div style="font-size: 10px; color: var(--text-muted);">Total Dépensé</div>
          <div style="font-size: 15px; font-weight: 800; color: var(--success);">${(cust.totalSpent || 0).toLocaleString('fr-FR')} CFA</div>
        </div>
        <div style="background: var(--bg-card); padding: 8px; border-radius: 8px;">
          <div style="font-size: 10px; color: var(--text-muted);">Commandes</div>
          <div style="font-size: 15px; font-weight: 800; color: var(--primary);">${cust.visitCount || tickets.length}</div>
        </div>
      </div>
    `;

    listEl.innerHTML = '';
    if (tickets.length === 0) {
      listEl.innerHTML = '<div style="font-size: 12px; color: var(--text-muted); text-align: center; padding: 20px;">Aucun ticket associé à ce client pour le moment.</div>';
    } else {
      tickets.forEach(t => {
        const row = document.createElement('div');
        row.className = 'card-group';
        row.style.marginBottom = '8px';
        row.style.cursor = 'pointer';
        row.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-weight: 700; font-size: 13px;">${t.number}</div>
              <div style="font-size: 11px; color: var(--text-muted);">${new Date(t.date).toLocaleString('fr-FR')}</div>
            </div>
            <div style="font-weight: 800; color: var(--primary);">${t.totalAmount.toLocaleString('fr-FR')} CFA</div>
          </div>
        `;
        row.addEventListener('click', () => {
          document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(t);
          document.getElementById('modalReceipt').classList.add('active');
        });
        listEl.appendChild(row);
      });
    }

    modal.classList.add('active');
  }

  // Customer Relance WhatsApp Modal
  let activeRelanceCustomer = null;
  function openCustomerRelanceModal(cust) {
    activeRelanceCustomer = cust;
    const modal = document.getElementById('modalCustomerRelance');
    const label = document.getElementById('relanceClientNameText');
    if (label) label.textContent = `Envoyer une relance WhatsApp à ${cust.name} (${cust.phone || 'Sans numéro'}) :`;
    if (modal) modal.classList.add('active');
  }

  document.getElementById('btnRelanceFidelite').addEventListener('click', () => {
    if (!activeRelanceCustomer || !activeRelanceCustomer.phone) {
      alert("⚠️ Ce client n'a pas de numéro de téléphone renseigné !");
      return;
    }
    const cleanPhone = activeRelanceCustomer.phone.replace(/[^0-9]/g, '');
    const msg = `Bonjour ${activeRelanceCustomer.name} ! 😊\nToute l'équipe de *${STATE.settings.shopName}* vous remercie pour votre fidélité (${activeRelanceCustomer.visitCount || 1} commandes chez nous). C'est toujours un plaisir de vous servir ! À très bientôt en boutique. 🛍️`;
    window.open(`https://api.whatsapp.com/send?phone=${cleanPhone}&text=${encodeURIComponent(msg)}`, '_blank');
  });

  document.getElementById('btnRelancePromo').addEventListener('click', () => {
    if (!activeRelanceCustomer || !activeRelanceCustomer.phone) {
      alert("⚠️ Ce client n'a pas de numéro de téléphone renseigné !");
      return;
    }
    const cleanPhone = activeRelanceCustomer.phone.replace(/[^0-9]/g, '');
    const msg = `Bonjour ${activeRelanceCustomer.name} ! 📢\nDe nouveaux arrivages et promotions exclusives sont disponibles cette semaine chez *${STATE.settings.shopName}*.\nPassez nous voir ou contactez-nous pour réserver vos articles préférés ! 🌟`;
    window.open(`https://api.whatsapp.com/send?phone=${cleanPhone}&text=${encodeURIComponent(msg)}`, '_blank');
  });

  document.getElementById('btnRelanceInactif').addEventListener('click', () => {
    if (!activeRelanceCustomer || !activeRelanceCustomer.phone) {
      alert("⚠️ Ce client n'a pas de numéro de téléphone renseigné !");
      return;
    }
    const cleanPhone = activeRelanceCustomer.phone.replace(/[^0-9]/g, '');
    const msg = `Bonjour ${activeRelanceCustomer.name} ! 💖\nVous nous manquez chez *${STATE.settings.shopName}* ! Nous avons rentré de nouveaux produits qui pourraient vous intéresser. Au plaisir de vous revoir très bientôt ! ✨`;
    window.open(`https://api.whatsapp.com/send?phone=${cleanPhone}&text=${encodeURIComponent(msg)}`, '_blank');
  });

  // ==========================================
  // EXPORTS & REPORTS
  // ==========================================
  const btnExpHistCsv = document.getElementById('btnExportHistoryCsv');
  if (btnExpHistCsv) {
    btnExpHistCsv.addEventListener('click', () => {
      const headers = ["Ticket", "Date", "Montant Total", "Mode Reglement", "Client", "Articles"];
      const rows = STATE.tickets.map(t => [
        t.number,
        new Date(t.date).toLocaleString('fr-FR'),
        t.totalAmount + " CFA",
        (t.paymentMethod || 'ESPECES').toUpperCase(),
        t.customerName || "Comptoir",
        t.items.map(i => `${i.productName} (x${i.qty})`).join(', ')
      ]);
      exportToCsv(`Historique_Ventes_${Date.now()}.csv`, headers, rows);
    });
  }

  const btnExpHistPdf = document.getElementById('btnExportHistoryPdf');
  if (btnExpHistPdf) {
    btnExpHistPdf.addEventListener('click', () => {
      const win = window.open('', '_blank');
      let rowsHtml = '';
      STATE.tickets.forEach(t => {
        rowsHtml += `<tr><td>${t.number}</td><td>${new Date(t.date).toLocaleString('fr-FR')}</td><td>${t.customerName || 'Comptoir'}</td><td>${t.items.length}</td><td>${t.totalAmount} CFA</td><td>${t.paymentMethod}</td></tr>`;
      });
      win.document.write(`
        <html><head><title>Historique des Ventes</title>
        <style>body { font-family: sans-serif; padding: 20px; } table { width: 100%; border-collapse: collapse; } th, td { border: 1px solid #ccc; padding: 8px; text-align: left; } th { background: #f0f0f0; }</style>
        </head><body>
        <h2>Historique des Ventes — ${STATE.settings.shopName}</h2>
        <table><thead><tr><th>Ticket</th><th>Date</th><th>Client</th><th>Articles</th><th>Montant</th><th>Règlement</th></tr></thead><tbody>${rowsHtml}</tbody></table>
        <script>window.print();</script></body></html>
      `);
      win.document.close();
    });
  }

  const btnExpBilanCsv = document.getElementById('btnExportBilanCsv');
  if (btnExpBilanCsv) {
    btnExpBilanCsv.addEventListener('click', () => {
      const z = getZReportData();
      const stockVal = STATE.products.reduce((sum, p) => sum + (p.stock * p.salePrice), 0);
      const headers = ["Poste", "Montant"];
      const rows = [
        ["Chiffre d'Affaires", z.totalSales + " CFA"],
        ["Nombre de Ventes", z.ticketsCount],
        ["Total Depenses", z.totalExpenses + " CFA"],
        ["Valeur du Stock", stockVal + " CFA"],
        ["Benefice Net Estime", (z.totalSales - z.totalExpenses) + " CFA"]
      ];
      exportToCsv(`Bilan_Financier_${Date.now()}.csv`, headers, rows);
    });
  }

  const btnExpBilanPdf = document.getElementById('btnExportBilanPdf');
  if (btnExpBilanPdf) {
    btnExpBilanPdf.addEventListener('click', () => {
      const z = getZReportData();
      const win = window.open('', '_blank');
      win.document.write(`
        <html><head><title>Bilan Financier</title>
        <style>body { font-family: sans-serif; padding: 20px; max-width: 500px; margin: auto; }</style>
        </head><body>
        ${generateZReportHtml(z)}
        <script>window.print();</script></body></html>
      `);
      win.document.close();
    });
  }

  // ==========================================
  // DELIVERY NOTE (BORDEREAU DE LIVRAISON)
  // ==========================================
  function generateDeliveryNotePlainText(deliv) {
    const width = (STATE.settings.printerWidth === "80") ? 42 : 32;
    const sep = '='.repeat(width);
    const dash = '-'.repeat(width);

    let out = [];
    out.push(sep);
    out.push(centerText("BORDEREAU DE LIVRAISON", width));
    out.push(centerText(removeAccents(STATE.settings.shopName || "SYPOS").toUpperCase(), width));
    if (STATE.settings.shopPhone) {
      out.push(centerText("Tel: " + removeAccents(STATE.settings.shopPhone), width));
    }
    out.push(sep);
    out.push("DESTINATAIRE :");
    out.push("Nom : " + removeAccents(deliv.recipientName));
    out.push("Tel : " + removeAccents(deliv.recipientPhone));
    out.push("Lieu: " + removeAccents(deliv.deliveryAddress));
    if (deliv.note) {
      out.push("Note: " + removeAccents(deliv.note));
    }
    out.push(dash);
    out.push("COLIS / CONTENU :");
    out.push(removeAccents(deliv.itemsSummary));
    out.push(dash);
    out.push(padLine("Prix Articles :", deliv.amountArticles.toLocaleString('fr-FR') + ' F', width));
    if (deliv.fee > 0) {
      out.push(padLine("Frais Livraison :", deliv.fee.toLocaleString('fr-FR') + ' F', width));
    }
    out.push(sep);
    const totalCollect = deliv.amountArticles + deliv.fee;
    out.push(padLine("TOTAL A ENCAISSER :", totalCollect.toLocaleString('fr-FR') + ' CFA', width));
    out.push(sep);
    out.push(centerText("Signature Client a la reception :", width));
    out.push('\n');
    out.push(centerText("................................", width));
    out.push('\n');
    return out.join('\n');
  }

  const btnOpenDeliv = document.getElementById('btnOpenDeliveryModal');
  if (btnOpenDeliv) {
    btnOpenDeliv.addEventListener('click', () => {
      document.getElementById('delivRecipientName').value = '';
      document.getElementById('delivRecipientPhone').value = '';
      document.getElementById('delivAddress').value = '';
      document.getElementById('delivItemsSummary').value = '';
      document.getElementById('delivAmountArticles').value = '';
      document.getElementById('delivFee').value = '';
      document.getElementById('delivNote').value = '';
      document.getElementById('modalDelivery').classList.add('active');
    });
  }

  const btnPrintDelivBle = document.getElementById('btnPrintDeliveryBleBtn');
  if (btnPrintDelivBle) {
    btnPrintDelivBle.addEventListener('click', async () => {
      const recipientName = document.getElementById('delivRecipientName').value.trim();
      const recipientPhone = document.getElementById('delivRecipientPhone').value.trim();
      const deliveryAddress = document.getElementById('delivAddress').value.trim();
      const itemsSummary = document.getElementById('delivItemsSummary').value.trim();
      const amountArticles = parseFloat(document.getElementById('delivAmountArticles').value) || 0;
      const fee = parseFloat(document.getElementById('delivFee').value) || 0;
      const note = document.getElementById('delivNote').value.trim();

      if (!recipientName || !recipientPhone || !deliveryAddress || !itemsSummary) {
        alert("⚠️ Veuillez remplir le nom, téléphone, adresse et contenu du colis !");
        return;
      }

      const deliv = { recipientName, recipientPhone, deliveryAddress, itemsSummary, amountArticles, fee, note };
      const plainText = generateDeliveryNotePlainText(deliv);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Bordereau de livraison imprimé avec succès !");
    });
  }

  const btnShareDelivWA = document.getElementById('btnShareDeliveryWhatsAppBtn');
  if (btnShareDelivWA) {
    btnShareDelivWA.addEventListener('click', () => {
      const recipientName = document.getElementById('delivRecipientName').value.trim();
      const recipientPhone = document.getElementById('delivRecipientPhone').value.trim();
      const deliveryAddress = document.getElementById('delivAddress').value.trim();
      const itemsSummary = document.getElementById('delivItemsSummary').value.trim();
      const amountArticles = parseFloat(document.getElementById('delivAmountArticles').value) || 0;
      const fee = parseFloat(document.getElementById('delivFee').value) || 0;
      const note = document.getElementById('delivNote').value.trim();

      const total = amountArticles + fee;
      let text = `📦 *BORDEREAU DE LIVRAISON — ${STATE.settings.shopName}*\n\n` +
        `👤 *Client :* ${recipientName}\n` +
        `📞 *Tél :* ${recipientPhone}\n` +
        `📍 *Lieu :* ${deliveryAddress}\n` +
        `📝 *Colis :* ${itemsSummary}\n` +
        `💰 *Total à encaisser :* ${total.toLocaleString('fr-FR')} CFA\n` +
        (note ? `ℹ️ *Note :* ${note}\n` : '') +
        `\nMerci de confirmer la livraison !`;

      window.location.href = `https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`;
    });
  }

  // ==========================================
  // STOCK ADJUSTMENT MODAL CONTROLS
  // ==========================================
  const btnMinus = document.getElementById('btnStockMinus');
  if (btnMinus) {
    btnMinus.addEventListener('click', () => {
      const inp = document.getElementById('prodInputStock');
      inp.value = Math.max(0, (parseInt(inp.value, 10) || 0) - 1);
    });
  }

  const btnPlus = document.getElementById('btnStockPlus');
  if (btnPlus) {
    btnPlus.addEventListener('click', () => {
      const inp = document.getElementById('prodInputStock');
      inp.value = (parseInt(inp.value, 10) || 0) + 1;
    });
  }

  document.querySelectorAll('.btn-quick-stock').forEach(btn => {
    btn.addEventListener('click', () => {
      const val = parseInt(btn.dataset.val, 10) || 0;
      const inp = document.getElementById('prodInputStock');
      inp.value = (parseInt(inp.value, 10) || 0) + val;
    });
  });

  // History period filter buttons
  document.querySelectorAll('.history-filter').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.history-filter').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentHistoryPeriod = btn.dataset.period || 'today';
      renderHistory();
    });
  });

  // Search input in Catalogue
  const prodSearch = document.getElementById('prodSearchInput');
  if (prodSearch) {
    prodSearch.addEventListener('input', () => renderProducts());
  }

  // Print Thermal Barcode Label
  const btnPrintLabel = document.getElementById('btnPrintLabelNow');
  if (btnPrintLabel) {
    btnPrintLabel.addEventListener('click', async () => {
      const name = document.getElementById('toolNameInput').value || 'Article';
      const price = document.getElementById('toolPriceInput').value || '0';
      const barcode = document.getElementById('toolBarcodeInput').value || '';
      
      const rawBytes = createEscPosBarcodeLabelBytes(STATE.settings.shopName, name, price, barcode);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Étiquette imprimée avec code-barres thermique !");
    });
  }

  // Test Bluetooth Print Button in Settings
  const btnTestBle = document.getElementById('btnTestBlePrint');
  if (btnTestBle) {
    btnTestBle.addEventListener('click', async () => {
      const dummyTicket = {
        number: 'TEST-' + Math.floor(1000 + Math.random() * 9000),
        date: new Date().toISOString(),
        items: [
          { productName: 'Article Test 1', qty: 1, price: 1500 },
          { productName: 'Article Test 2', qty: 2, price: 2500 }
        ],
        totalAmount: 6500,
        paymentMethod: 'cash'
      };
      const plainText = generateReceiptPlainText(dummyTicket);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Ticket de test imprimé avec succès !");
    });
  }

  // Helper PIN Request
  function requestAdminPin(actionName, callback) {
    if (!STATE.settings.adminPin || STATE.settings.adminPin === "0000") {
      callback();
      return;
    }
    STATE.pendingPinAction = callback;
    document.getElementById('pinActionDescription').textContent = `Action protégée : ${actionName}`;
    document.getElementById('pinInputCode').value = '';
    document.getElementById('modalPin').classList.add('active');
  }

  // Service Worker Registration for PWA Offline
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch(console.error);
  }
});

// ==========================================
// 13. VIEW RENDERING FUNCTIONS
// ==========================================
function renderProducts() {
  const container = document.getElementById('productsListContainer');
  if (!container) return;
  container.innerHTML = '';
  const search = (document.getElementById('prodSearchInput') ? document.getElementById('prodSearchInput').value : '').toLowerCase();
  
  const filtered = (STATE.products || []).filter(p => p.name.toLowerCase().includes(search) || (p.barcode && p.barcode.includes(search)));

  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="text-align:center; padding:30px 10px; color:var(--text-muted);">
        <div style="font-size:32px; margin-bottom:8px;">📦</div>
        <div>Aucun article trouvé</div>
        <button class="btn-main" onclick="document.getElementById('btnAddNewProduct').click()" style="margin-top:12px; width:auto; padding:0 16px;">
          ➕ Ajouter un article
        </button>
      </div>
    `;
    return;
  }

  filtered.forEach(p => {
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.display = 'flex';
    card.style.justifyContent = 'space-between';
    card.style.alignItems = 'center';
    card.style.marginBottom = '8px';
    card.innerHTML = `
      <div style="flex:1; cursor:pointer;" class="prod-info-click">
        <div style="font-weight: 700; font-size: 15px;">${p.name}</div>
        <div style="font-size: 12px; color: var(--text-muted); margin-top:2px;">
          Stock : <strong style="color:${p.stock <= 5 ? 'var(--danger)' : 'var(--primary)'}; font-size:13px;">${p.stock}</strong> 
          ${p.barcode ? `| Code: ${p.barcode}` : ''}
        </div>
        <div style="font-size: 14px; font-weight: 700; color: var(--primary); margin-top: 2px;">${p.salePrice.toLocaleString('fr-FR')} CFA</div>
      </div>
      <div style="display:flex; gap:6px; align-items:center;">
        <button class="icon-btn stock-minus-btn" style="border:1px solid var(--border); width:36px; height:36px; font-size:16px; font-weight:bold;">-</button>
        <button class="icon-btn stock-plus-btn" style="border:1px solid var(--border); width:36px; height:36px; font-size:16px; font-weight:bold; color:var(--primary);">+</button>
        <button class="icon-btn edit-prod-btn" style="border:1px solid var(--border); width:36px; height:36px; color: var(--primary);">✏️</button>
      </div>
    `;

    card.querySelector('.prod-info-click').addEventListener('click', () => openProductModal(p));
    card.querySelector('.edit-prod-btn').addEventListener('click', () => openProductModal(p));
    
    card.querySelector('.stock-minus-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      if (p.stock > 0) {
        p.stock -= 1;
        saveToStorage();
        renderProducts();
        renderPos();
      }
    });

    card.querySelector('.stock-plus-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      p.stock += 1;
      saveToStorage();
      renderProducts();
      renderPos();
    });

    container.appendChild(card);
  });
}

function renderHistory() {
  const container = document.getElementById('historyListContainer');
  if (!container) return;
  container.innerHTML = '';
  let totalPeriod = 0;

  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfYesterday = startOfToday - 86400000;
  const startOfWeek = now.getTime() - (7 * 86400000);
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

  const filteredTickets = (STATE.tickets || []).filter(t => {
    const tTime = new Date(t.date).getTime();
    if (currentHistoryPeriod === 'today') return tTime >= startOfToday;
    if (currentHistoryPeriod === 'yesterday') return tTime >= startOfYesterday && tTime < startOfToday;
    if (currentHistoryPeriod === 'week') return tTime >= startOfWeek;
    if (currentHistoryPeriod === 'month') return tTime >= startOfMonth;
    return true;
  });

  if (filteredTickets.length === 0) {
    container.innerHTML = '<div style="text-align:center; color:var(--text-muted); padding:20px; font-size:13px;">Aucune vente sur cette période</div>';
  } else {
    filteredTickets.forEach(t => {
      totalPeriod += t.totalAmount;
      const card = document.createElement('div');
      card.className = 'card-group';
      card.style.marginBottom = '10px';
      card.style.cursor = 'pointer';
      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <div style="font-weight: 700; font-size: 14px;">${t.number} ${t.customerName ? '<span style="font-size:12px; color:var(--primary);">• ' + t.customerName + '</span>' : ''}</div>
            <div style="font-size: 11px; color: var(--text-muted);">${new Date(t.date).toLocaleString('fr-FR')} • ${(t.paymentMethod || 'ESPECES').toUpperCase()}</div>
          </div>
          <div style="font-size: 16px; font-weight: 700; color: var(--primary);">${t.totalAmount.toLocaleString('fr-FR')} CFA</div>
        </div>
      `;
      card.addEventListener('click', () => {
        document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(t);
        document.getElementById('modalReceipt').classList.add('active');
      });
      container.appendChild(card);
    });
  }

  const totalEl = document.getElementById('historyTotalSales');
  if (totalEl) totalEl.textContent = totalPeriod.toLocaleString('fr-FR') + ' CFA';
}

function renderReports() {
  const z = getZReportData();
  const stockVal = STATE.products.reduce((sum, p) => sum + (p.stock * p.salePrice), 0);
  
  const revEl = document.getElementById('reportRevenue');
  if (revEl) revEl.textContent = z.totalSales.toLocaleString('fr-FR') + ' CFA';
  const profEl = document.getElementById('reportProfit');
  if (profEl) profEl.textContent = (z.totalSales - z.totalExpenses).toLocaleString('fr-FR') + ' CFA';
  const stockEl = document.getElementById('reportStockValue');
  if (stockEl) stockEl.textContent = stockVal.toLocaleString('fr-FR') + ' CFA';

  const expContainer = document.getElementById('expensesListContainer');
  if (expContainer) {
    expContainer.innerHTML = '';
    if (!STATE.expenses || STATE.expenses.length === 0) {
      expContainer.innerHTML = '<div style="font-size:12px; color:var(--text-muted); padding:8px 0;">Aucune dépense enregistrée</div>';
    } else {
      STATE.expenses.forEach(e => {
        const row = document.createElement('div');
        row.style.display = 'flex';
        row.style.justifyContent = 'space-between';
        row.style.padding = '6px 0';
        row.style.borderBottom = '1px solid var(--border)';
        row.style.fontSize = '13px';
        row.innerHTML = `
          <div><span>${e.reason}</span> <span style="font-size:10px; color:var(--text-muted);">${new Date(e.date).toLocaleTimeString('fr-FR')}</span></div>
          <div style="font-weight:700; color:var(--danger);">-${e.amount.toLocaleString('fr-FR')} CFA</div>
        `;
        expContainer.appendChild(row);
      });
    }
  }
}

function renderCustomers() {
  const container = document.getElementById('customersListContainer');
  if (!container) return;
  container.innerHTML = '';
  
  if (!STATE.customers) STATE.customers = [];

  const totalClients = STATE.customers.length;
  const totalSalesFromClients = STATE.customers.reduce((sum, c) => sum + (c.totalSpent || 0), 0);

  const cntEl = document.getElementById('customerCountTotal');
  if (cntEl) cntEl.textContent = totalClients;
  const salesEl = document.getElementById('customerTotalSales');
  if (salesEl) salesEl.textContent = totalSalesFromClients.toLocaleString('fr-FR') + ' CFA';

  const search = (document.getElementById('custSearchInput') ? document.getElementById('custSearchInput').value : '').toLowerCase().trim();
  const now = Date.now();
  const thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000);
  const sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000);

  let filtered = STATE.customers.filter(c => {
    const matchSearch = !search || 
      (c.name && c.name.toLowerCase().includes(search)) || 
      (c.phone && c.phone.includes(search));
    if (!matchSearch) return false;

    if (currentCustomerFilter === 'vip') {
      return (c.totalSpent >= 50000) || (c.visitCount >= 5);
    }
    if (currentCustomerFilter === 'recent') {
      return c.lastPurchaseDate && c.lastPurchaseDate >= sevenDaysAgo;
    }
    if (currentCustomerFilter === 'inactive') {
      return !c.lastPurchaseDate || c.lastPurchaseDate < thirtyDaysAgo;
    }
    return true;
  });

  filtered.sort((a, b) => (b.totalSpent || 0) - (a.totalSpent || 0));

  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="text-align:center; padding:30px 10px; color:var(--text-muted);">
        <div style="font-size:32px; margin-bottom:8px;">👥</div>
        <div>Aucun client trouvé dans cette vue</div>
        <button class="btn-main" onclick="document.getElementById('btnAddCustomer').click()" style="margin-top:12px; width:auto; padding:0 16px;">
          ➕ Ajouter un client
        </button>
      </div>
    `;
    return;
  }

  filtered.forEach(c => {
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.marginBottom = '10px';

    const isVip = (c.totalSpent >= 50000) || (c.visitCount >= 5);
    const lastVisitStr = c.lastPurchaseDate ? formatTimeAgo(c.lastPurchaseDate) : 'Aucun achat enregistré';

    card.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: 8px;">
        <div>
          <div style="font-weight:700; font-size:16px; display:flex; align-items:center; gap:6px;">
            <span>${c.name}</span>
            ${isVip ? '<span style="font-size:10px; background:#FEF3C7; color:#92400E; padding:2px 6px; border-radius:10px; font-weight:bold;">⭐ VIP</span>' : ''}
          </div>
          <div style="font-size:12px; color:var(--text-muted); margin-top:2px; display:flex; align-items:center; gap:4px;">
            <span>📞</span> <strong>${c.phone || 'Sans numéro'}</strong>
            ${c.email ? ` | ✉️ ${c.email}` : ''}
          </div>
        </div>
        <div style="text-align:right;">
          <div style="font-size:15px; font-weight:800; color:var(--success);">
            ${(c.totalSpent || 0).toLocaleString('fr-FR')} CFA
          </div>
          <div style="font-size:11px; color:var(--text-muted);">
            ${c.visitCount || 0} commande(s)
          </div>
        </div>
      </div>

      <div style="font-size:11px; color:var(--text-muted); margin-bottom:10px; display:flex; justify-content:space-between;">
        <span>🕒 Dernier passage : <strong>${lastVisitStr}</strong></span>
        ${c.notes ? `<span style="font-style:italic;">📝 ${c.notes}</span>` : ''}
      </div>

      <div style="display:flex; gap:6px; flex-wrap:wrap;">
        <button class="btn-main btn-secondary btn-cust-relance" style="margin:0; flex:1; height:36px; font-size:12px; background:#e8f5e9; color:#2e7d32;">
          💬 Relance WhatsApp
        </button>
        <button class="btn-main btn-secondary btn-cust-hist" style="margin:0; width:auto; height:36px; font-size:12px; padding:0 10px;">
          📋 Historique
        </button>
        <button class="icon-btn btn-cust-edit" style="border:1px solid var(--border); width:36px; height:36px; color:var(--primary);">
          ✏️
        </button>
        <button class="icon-btn btn-cust-del" style="border:1px solid var(--border); width:36px; height:36px; color:var(--danger);">
          🗑️
        </button>
      </div>
    `;

    card.querySelector('.btn-cust-relance').addEventListener('click', () => openCustomerRelanceModal(c));
    card.querySelector('.btn-cust-hist').addEventListener('click', () => openCustomerHistoryModal(c));
    card.querySelector('.btn-cust-edit').addEventListener('click', () => openCustomerModal(c));
    card.querySelector('.btn-cust-del').addEventListener('click', () => {
      if (confirm(`Voulez-vous supprimer la fiche client de "${c.name}" ?`)) {
        STATE.customers = STATE.customers.filter(x => x.id !== c.id);
        if (STATE.selectedCustomerId === c.id) STATE.selectedCustomerId = "";
        saveToStorage();
        renderCustomers();
        populateCartCustomerSelect();
      }
    });

    container.appendChild(card);
  });
}

function renderTools() {
  const select = document.getElementById('toolProductSelect');
  if (!select) return;
  select.innerHTML = '<option value="">-- Personnalisé --</option>';
  (STATE.products || []).forEach(p => {
    select.innerHTML += `<option value="${p.id}">${p.name} (${p.salePrice} CFA)</option>`;
  });
  select.onchange = (e) => {
    const prod = STATE.products.find(p => p.id === e.target.value);
    if (prod) {
      document.getElementById('toolNameInput').value = prod.name;
      document.getElementById('toolPriceInput').value = prod.salePrice;
      document.getElementById('toolBarcodeInput').value = prod.barcode || '';
      document.getElementById('lblName').textContent = prod.name;
      document.getElementById('lblPrice').textContent = prod.salePrice + ' CFA';
      document.getElementById('lblSerial').textContent = prod.barcode || '';
    }
  };
}

function renderSettings() {
  const setShopName = document.getElementById('setShopName');
  if (setShopName) setShopName.value = STATE.settings.shopName;
  const setShopAddress = document.getElementById('setShopAddress');
  if (setShopAddress) setShopAddress.value = STATE.settings.shopAddress;
  const setShopPhone = document.getElementById('setShopPhone');
  if (setShopPhone) setShopPhone.value = STATE.settings.shopPhone;
  const setSellerName = document.getElementById('setSellerName');
  if (setSellerName) setSellerName.value = STATE.settings.sellerName;
  const setReceiptFooter = document.getElementById('setReceiptFooter');
  if (setReceiptFooter) setReceiptFooter.value = STATE.settings.receiptFooter;
  const setShowSignature = document.getElementById('setShowSignature');
  if (setShowSignature) setShowSignature.checked = STATE.settings.showPublisherSignature;
  const setTaxEnabled = document.getElementById('setTaxEnabled');
  if (setTaxEnabled) setTaxEnabled.checked = STATE.settings.taxEnabled;
  const setAdminPin = document.getElementById('setAdminPin');
  if (setAdminPin) setAdminPin.value = STATE.settings.adminPin;
  const setPrinterWidth = document.getElementById('setPrinterWidth');
  if (setPrinterWidth) setPrinterWidth.value = STATE.settings.printerWidth || "58";

  // Google Sheets Settings
  const setSheetsUrl = document.getElementById('setGoogleSheetsUrl');
  if (setSheetsUrl) setSheetsUrl.value = STATE.settings.googleSheetsUrl || "";
  const setSheetsSync = document.getElementById('setGoogleSheetsSync');
  if (setSheetsSync) setSheetsSync.checked = !!STATE.settings.googleSheetsSync;

  // Update License Status Card
  const devIdEl = document.getElementById('settingsDevId');
  if (devIdEl) devIdEl.textContent = getDeviceId();

  const badge = document.getElementById('settingsLicenseStatusBadge');
  if (badge) {
    if (isLicenseActive()) {
      const exp = STATE.settings.licenseExpiryDate ? ` (${new Date(STATE.settings.licenseExpiryDate).toLocaleDateString('fr-FR')})` : ' (À vie)';
      badge.textContent = `✅ Active - ${STATE.settings.licenseType || 'Commerciale'}${exp}`;
      badge.style.background = '#e8f5e9';
      badge.style.color = '#2e7d32';
    } else {
      badge.textContent = '❌ Non Activée / Expirée';
      badge.style.background = '#ffebee';
      badge.style.color = '#c62828';
    }
  }
}
